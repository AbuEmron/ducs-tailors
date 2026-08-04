/**
 * Fi Sabilillah — the responsive brand mark.
 *
 * NOTE ON STATUS. There is no web client in this repository; the product is a native
 * Kotlin/Compose Android application. This component is real, valid React and is written
 * against the same geometry every other asset is generated from, but nothing currently
 * imports it. It exists so that a future web or PWA client starts from the identity
 * rather than re-tracing it, and so the brief's API is honoured exactly.
 *
 * The geometry below is copied from `tools/brand/geometry.py`. If the emblem changes,
 * re-run `python3 tools/brand/build.py` and update the four constants here; a test in
 * that script's output (`brand/tokens/brand-tokens.json`) carries the same numbers.
 */
import * as React from "react";

export type LogoVariant =
  | "icon"
  | "horizontal"
  | "stacked"
  | "wordmark"
  | "compact"
  | "monochrome";

export type LogoTone = "full" | "dark" | "light" | "gold" | "monochrome";

export type LogoSize = "xs" | "sm" | "md" | "lg" | "xl" | "hero";

const EMBLEM_HEIGHT: Record<LogoSize, number> = {
  xs: 16,
  sm: 24,
  md: 40,
  lg: 64,
  xl: 96,
  hero: 160,
};

/** Below this the middle arch closes up and the mark has to simplify. */
const SIMPLIFY_BELOW = 32;

const ARCH_OUTER = "M16 48 L16 26 C16 18 23 11 32 6.5 C41 11 48 18 48 26 L48 48";
const ARCH_MIDDLE =
  "M23.5 48 L23.5 31 C23.5 26 27 21.5 32 19 C37 21.5 40.5 26 40.5 31 L40.5 48";
const ARCH_INNER =
  "M28.7 48 L28.7 34.2 C28.7 31.2 30.1 28.7 32 27.4 C33.9 28.7 35.3 31.2 35.3 34.2 L35.3 48 Z";
const ARCH_INNER_SMALL =
  "M27.9 48 L27.9 33.4 C27.9 30 29.6 27.1 32 25.6 C34.4 27.1 36.1 30 36.1 33.4 L36.1 48 Z";

const TONES: Record<LogoTone, { plate: string | null; arch: string; inner: string }> = {
  full: { plate: "#0B3A30", arch: "#F4F1E8", inner: "#C6A664" },
  dark: { plate: "#0C1013", arch: "#F4F1E8", inner: "#C6A664" },
  light: { plate: "#F4F1E8", arch: "#0B3A30", inner: "#836427" },
  gold: { plate: "#0B3A30", arch: "#C6A664", inner: "#F4F1E8" },
  monochrome: { plate: null, arch: "currentColor", inner: "currentColor" },
};

export interface FiSabilillahLogoProps {
  variant?: LogoVariant;
  tone?: LogoTone;
  size?: LogoSize;
  showTagline?: boolean;
  /**
   * Omit when something adjacent already names the application. A logo repeated to a
   * screen reader on every page is noise, not access.
   */
  title?: string | null;
  className?: string;
}

function Emblem({
  arch,
  inner,
  simplified,
}: {
  arch: string;
  inner: string;
  simplified: boolean;
}) {
  return (
    <>
      <g
        fill="none"
        stroke={arch}
        strokeWidth={simplified ? 6 : 5}
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <path d={ARCH_OUTER} />
        {!simplified && <path d={ARCH_MIDDLE} />}
      </g>
      <path d={simplified ? ARCH_INNER_SMALL : ARCH_INNER} fill={inner} />
    </>
  );
}

export function FiSabilillahLogo({
  variant = "horizontal",
  tone = "full",
  size = "md",
  showTagline = false,
  title = "Fi Sabilillah",
  className,
}: FiSabilillahLogoProps) {
  const px = EMBLEM_HEIGHT[size];
  const t = TONES[variant === "monochrome" ? "monochrome" : tone];

  // The variant asked for is not always the variant that should be drawn. An
  // "horizontal" logo at 16px is a wordmark nobody can read next to a mark nobody can
  // see, so small sizes collapse to the icon regardless of what the caller wanted.
  const effective: LogoVariant =
    px < SIMPLIFY_BELOW && variant !== "wordmark" ? "icon" : variant;
  const simplified = px < SIMPLIFY_BELOW;

  const a11y = title
    ? { role: "img" as const, "aria-label": title }
    : { "aria-hidden": true as const, focusable: false as const };

  if (effective === "icon" || effective === "compact") {
    const pad = effective === "compact" ? 0 : 0;
    return (
      <svg
        viewBox="0 0 64 64"
        width={px}
        height={px}
        className={className}
        {...a11y}
      >
        {title && <title>{title}</title>}
        {t.plate && <rect width="64" height="64" rx="14" fill={t.plate} />}
        <g transform={`translate(${pad},${pad})`}>
          <Emblem arch={t.arch} inner={t.inner} simplified={simplified} />
        </g>
      </svg>
    );
  }

  // Wordmark and lockups need the drawn letterforms, which live in the generated
  // SVGs rather than inline here: 13 glyphs of path data would triple this file and
  // would be a second copy free to drift from `brand/logo/*.svg`.
  const href = {
    horizontal: "/brand/logo/fi-sabilillah-logo-horizontal.svg",
    stacked: "/brand/logo/fi-sabilillah-logo-stacked.svg",
    wordmark: "/brand/logo/fi-sabilillah-wordmark.svg",
    monochrome: "/brand/logo/fi-sabilillah-logo-horizontal-mono.svg",
    icon: "/brand/logo/fi-sabilillah-icon-primary.svg",
    compact: "/brand/logo/fi-sabilillah-icon-primary.svg",
  }[effective];

  const heightFor: Partial<Record<LogoVariant, number>> = {
    horizontal: px * 1.1,
    stacked: px * 2.2,
    wordmark: px * 0.62,
  };

  return (
    <img
      src={tone === "light" ? href.replace(".svg", "-light.svg") : href}
      alt={title ?? ""}
      height={heightFor[effective] ?? px}
      className={className}
      {...(title ? {} : { "aria-hidden": true })}
    />
  );
}

export default FiSabilillahLogo;
