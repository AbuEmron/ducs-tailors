#!/usr/bin/env python3
"""
A static sanity check over the Android sources.

This exists because of an environment constraint rather than a design choice: the Compose
UI in `androidApp/` was authored where Google's Maven repository is unreachable, so it
could not be compiled. This script catches the errors a compiler would have caught first —
a component called with the wrong argument name, an import of something that does not
exist, an unbalanced brace — so that the first real build starts from a shorter list.

It is not a substitute for `./gradlew assembleDebug`. Run that as soon as you have an
environment with an Android SDK; see the status section of the README.

Usage:  python3 tools/check-android-sources.py
Exit:   0 when nothing suspicious was found, 1 otherwise.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "androidApp" / "app" / "src" / "main" / "java" / "org" / "fisabilillah" / "app"
CORE = ROOT / "core"

FAILURES: list[str] = []
WARNINGS: list[str] = []


def fail(path: Path, message: str) -> None:
    FAILURES.append(f"{path.relative_to(ROOT)}: {message}")


def warn(path: Path, message: str) -> None:
    WARNINGS.append(f"{path.relative_to(ROOT)}: {message}")


def kotlin_files(root: Path) -> list[Path]:
    return sorted(p for p in root.rglob("*.kt"))


def declared_symbols(files: list[Path]) -> set[str]:
    """Top-level declarations: classes, objects, interfaces, enums, functions, properties."""
    found: set[str] = set()
    pattern = re.compile(
        r"^\s*(?:public |internal |private |protected )?"
        r"(?:data |value |sealed |abstract |open |enum |annotation )*"
        r"(?:class|object|interface|fun|val|var|typealias)\s+"
        r"(?:<[^>]*>\s+)?"
        r"([A-Za-z_][A-Za-z0-9_]*)",
        re.MULTILINE,
    )
    for path in files:
        text = path.read_text(encoding="utf-8")
        for match in pattern.finditer(text):
            found.add(match.group(1))
    return found


def check_balanced(path: Path, text: str) -> None:
    """Braces and parentheses, ignoring those inside strings and comments."""
    depth_brace = depth_paren = 0
    i = 0
    in_line_comment = in_block_comment = False
    in_string = in_raw_string = False
    while i < len(text):
        two = text[i : i + 2]
        three = text[i : i + 3]

        if in_line_comment:
            if text[i] == "\n":
                in_line_comment = False
            i += 1
            continue
        if in_block_comment:
            if two == "*/":
                in_block_comment = False
                i += 2
                continue
            i += 1
            continue
        if in_raw_string:
            if three == '"""':
                in_raw_string = False
                i += 3
                continue
            i += 1
            continue
        if in_string:
            if text[i] == "\\":
                i += 2
                continue
            if text[i] == '"':
                in_string = False
            i += 1
            continue

        if two == "//":
            in_line_comment = True
            i += 2
            continue
        if two == "/*":
            in_block_comment = True
            i += 2
            continue
        if three == '"""':
            in_raw_string = True
            i += 3
            continue
        if text[i] == '"':
            in_string = True
            i += 1
            continue

        if text[i] == "{":
            depth_brace += 1
        elif text[i] == "}":
            depth_brace -= 1
            if depth_brace < 0:
                fail(path, "a closing brace appears before its opening brace")
                return
        elif text[i] == "(":
            depth_paren += 1
        elif text[i] == ")":
            depth_paren -= 1
            if depth_paren < 0:
                fail(path, "a closing parenthesis appears before its opening one")
                return
        i += 1

    if depth_brace != 0:
        fail(path, f"unbalanced braces (off by {depth_brace})")
    if depth_paren != 0:
        fail(path, f"unbalanced parentheses (off by {depth_paren})")


def members_of(files: list[Path], type_name: str) -> set[str]:
    """
    The member names a class exposes: constructor properties, and the `val`/`var`/`fun`
    declarations in its body.

    Crude — it stops at the first line that starts a new top-level declaration rather than
    tracking braces — but it is enough for the small data classes this check cares about.
    """
    members: set[str] = set()
    header = re.compile(
        r"^(?:public |internal )?(?:data |value |sealed )*class "
        + re.escape(type_name)
        + r"\b"
    )
    member = re.compile(
        r"^\s+(?:public |internal |private |override |const )*"
        r"(?:val|var|fun)\s+([A-Za-z_][A-Za-z0-9_]*)"
    )
    ctor_param = re.compile(r"^\s+(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)")
    for path in files:
        lines = path.read_text(encoding="utf-8").splitlines()
        for index, line in enumerate(lines):
            if not header.match(line):
                continue
            for following in lines[index + 1 :]:
                # The class ends at a closing brace in the first column. A `)` there is
                # the end of the constructor parameter list, not the end of the class.
                if following.startswith("}"):
                    break
                found = member.match(following) or ctor_param.match(following)
                if found:
                    members.add(found.group(1))
    return members


def main() -> int:
    if not APP.exists():
        print(f"No Android sources found at {APP}")
        return 1

    app_files = kotlin_files(APP)
    core_files = kotlin_files(CORE)
    known = declared_symbols(app_files) | declared_symbols(core_files)

    # Generated at build time by the Android Gradle plugin, so they exist for the
    # compiler but not on disk. BuildConfig carries the Supabase project URL and
    # publishable key; R is the resource table, which is where every generated brand
    # drawable arrives from.
    known |= {"BuildConfig", "R"}

    # Symbols that come from AndroidX, Compose, Kotlin and the JDK. We cannot resolve those
    # here, so imports rooted at these packages are trusted.
    external_roots = (
        "androidx.",
        "android.",
        "kotlin.",
        "kotlinx.",
        "java.",
        "javax.",
        "org.jetbrains.",
    )

    for path in app_files:
        text = path.read_text(encoding="utf-8")

        if not text.startswith("package "):
            fail(path, "does not start with a package declaration")

        # The package must match the directory, or the compiler will not find the file.
        package_line = text.split("\n", 1)[0]
        declared_package = package_line.removeprefix("package ").strip()
        expected = str(path.parent.relative_to(APP.parent.parent.parent)).replace("/", ".")
        if declared_package != expected:
            fail(path, f"package is '{declared_package}' but the directory implies '{expected}'")

        check_balanced(path, text)

        # A duplicated import is not a style problem. Kotlin rejects the file outright
        # with "Conflicting import: imported name 'X' is ambiguous" -- which is what
        # happens when a block of imports is appended to a file that already had them.
        imported_leaves: dict[str, str] = {}
        for line in text.splitlines():
            if not line.startswith("import "):
                continue
            full = line.removeprefix("import ").split(" as ")[0].strip()
            leaf = full.rsplit(".", 1)[-1]
            previous = imported_leaves.get(leaf)
            if previous == full:
                fail(path, f"imports '{full}' twice")
            elif previous is not None:
                fail(path, f"imports '{leaf}' from both '{previous}' and '{full}'")
            else:
                imported_leaves[leaf] = full

        for line in text.splitlines():
            stripped = line.strip()

            if stripped.startswith("import ") and stripped.endswith(".*"):
                fail(path, f"wildcard import: {stripped}")

            if stripped.startswith("import "):
                imported = stripped.removeprefix("import ").split(" as ")[0].strip()
                if imported.startswith(external_roots):
                    continue
                symbol = imported.rsplit(".", 1)[-1]
                # Lower-case leaf names are extension functions or properties, which our
                # crude declaration scan does not always catch.
                if symbol[:1].isupper() and symbol not in known:
                    fail(path, f"imports '{imported}' but no such declaration was found")

            if "TODO(" in stripped and "//" not in stripped.split("TODO(")[0]:
                warn(path, "contains a TODO() call, which throws at runtime")

            if len(line) > 110:
                warn(path, f"line longer than 110 characters: {stripped[:60]}...")

    # Members called on a `Principal`. This is the one receiver worth checking by name:
    # it decides whether a screen shows a moderation control, so a typo here is a
    # permission bug rather than a rendering one, and the type is small enough to
    # enumerate exactly. Add a receiver to this map when it has cost you a build.
    principal_members = members_of(core_files, "Principal") | {
        "copy", "equals", "hashCode", "toString",
    }
    # Extensions the app declares on Principal are members as far as a caller is
    # concerned -- provided the calling file imports them, which the import check above
    # already verifies.
    for path in app_files:
        for match in re.finditer(
            r"^(?:internal |public |private )?fun Principal\??\.([A-Za-z_][A-Za-z0-9_]*)",
            path.read_text(encoding="utf-8"),
            re.MULTILINE,
        ):
            principal_members.add(match.group(1))
    if principal_members:
        for path in app_files:
            text = path.read_text(encoding="utf-8")
            for match in re.finditer(r"\bprincipal(?:\?)?\.((?:is|can|has)[A-Z][A-Za-z0-9_]*)", text):
                # Only capability-shaped names. `principal` is often a nullable behind a
                # `State`, so `.value`, `.let` and friends are legitimate and unresolvable
                # here; a missing `isX`/`canX` is the case worth catching, because it
                # decides whether a permission-bearing control is drawn.
                name = match.group(1)
                if name not in principal_members:
                    fail(path, f"calls 'principal.{name}', which Principal does not declare")

    # Every composable a navigation graph references must actually exist.
    nav = APP / "ui" / "navigation" / "FiSabilillahNavHost.kt"
    if nav.exists():
        nav_text = nav.read_text(encoding="utf-8")
        for match in re.finditer(r"^import (org\.fisabilillah\.app\.ui\.screens\.[\w.]+)$", nav_text, re.MULTILINE):
            symbol = match.group(1).rsplit(".", 1)[-1]
            if symbol[:1].isupper() and symbol not in known:
                fail(nav, f"navigation references '{symbol}', which does not exist")

    print(f"Checked {len(app_files)} Android source files.\n")

    if WARNINGS:
        print(f"{len(WARNINGS)} warning(s):")
        for warning in WARNINGS[:40]:
            print(f"  - {warning}")
        if len(WARNINGS) > 40:
            print(f"  ... and {len(WARNINGS) - 40} more")
        print()

    if FAILURES:
        print(f"{len(FAILURES)} problem(s) a compiler would reject:")
        for failure in FAILURES:
            print(f"  - {failure}")
        return 1

    print("No structural problems found.")
    print("This is not a compile. Run ./gradlew assembleDebug in androidApp/ to be sure.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
