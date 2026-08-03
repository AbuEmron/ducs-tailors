package org.fisabilillah.app.ui.screens.legal

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.DisclaimerCard
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.model.DonationFeatureFlags
import org.fisabilillah.core.policy.ContentSignals

/**
 * The four documents every member is entitled to read before agreeing to anything.
 *
 * These are drafts written by engineers, and each one says so in the first card rather than
 * in a footnote. Shipping four screens of lorem ipsum would have been faster; it would also
 * have meant nobody could review the substance of what the product intends to promise until
 * a lawyer had been paid, which is exactly the wrong order.
 */

// ── Terms of use ─────────────────────────────────────────────────────────────

@Composable
internal fun TermsScreen(onBack: () -> Unit) {
    LegalScaffold(title = "Terms of use", onBack = onBack) {
        PlaceholderNotice(
            "This is a working draft written by the product team. It has not been reviewed " +
                "by a qualified lawyer, it is not enforceable as it stands, and it must be " +
                "replaced before the platform accepts its first member. It is published in " +
                "this state so that the substance can be argued with early."
        )

        Heading("1. Who may use this platform")
        Body(
            "Accounts are for people aged eighteen or over. You confirm your age when you " +
                "create an account, and declaring an age you are not is a breach of these " +
                "terms and grounds for closing the account. Youth participation is planned " +
                "as a later phase and will require guardian consent, verified organisations " +
                "and background-checked adults before it opens."
        )
        Body(
            "One account per person. Accounts are not transferable, and you may not let " +
                "somebody else use yours."
        )

        Heading("2. What this platform is for")
        Body(
            "This is a place to serve, learn, build and support one another for the sake of " +
                "Allah. It is not a social network, not a dating service, and not a source " +
                "of religious rulings. Using it to seek attention, to privately pursue " +
                "people, or to exploit anyone's vulnerability is a breach of these terms " +
                "regardless of whether a specific rule below happens to cover it."
        )

        Heading("3. Conversations")
        Body(
            "Every conversation carries a stated purpose, and both parties can see it. " +
                "Persistently steering a conversation away from what it was opened for, " +
                "after being asked not to, is a breach. So is attempting to move a " +
                "conversation off the platform in order to escape safeguards the other " +
                "person has chosen."
        )
        Body(
            "You may not contact somebody whose safeguards decline the purpose you are " +
                "writing about, and you may not create a second account in order to reach " +
                "somebody who has blocked you."
        )

        Heading("4. Honesty about who you are")
        Body(
            "Do not claim qualifications, roles or affiliations you do not hold. Teaching " +
                "capacities that imply religious or professional standing are reviewed " +
                "before they become visible, and submitting false documents for that review " +
                "results in permanent removal."
        )

        Heading("5. Volunteering and commitments")
        Body(
            "A commitment you make is a promise to another person, not a click. If you " +
                "cannot attend, cancel in the app so the organiser can fill the place. " +
                "Repeatedly failing to attend without notice may restrict your ability to " +
                "commit to further work."
        )
        Body(
            "Organisers, not the platform, are responsible for how their activities run, " +
                "for the safety arrangements at them, and for any insurance those " +
                "activities require."
        )

        Heading("6. Verification and what it means")
        Body(
            "Verification badges say only what they say, and each one carries a plain " +
                "statement of what it does not mean. Identity verification does not vouch " +
                "for anyone's character. Organisation verification is a check of " +
                "registration documents, not an audit of how money is spent. Do not treat " +
                "any badge as the platform's endorsement of a person."
        )

        Heading("7. Moderation, restrictions and appeals")
        Body(
            "Reports are reviewed by people. A restriction comes with a stated reason and a " +
                "right of appeal, and an appeal is decided by somebody who was not part of " +
                "the original decision. Consequential moderation actions are written to an " +
                "audit log that cannot be edited or deleted by anyone, including platform " +
                "administrators."
        )

        Heading("8. Your content")
        Body(
            "You keep ownership of what you write. You grant the platform the narrow licence " +
                "it needs in order to store your content, show it to the people you " +
                "addressed it to, and retain it where a safety investigation or a legal " +
                "obligation requires. That licence covers nothing else — your content is " +
                "not used for advertising and is not sold."
        )

        Heading("9. Ending your account")
        Body(
            "You may close your account at any time. Some records are retained after " +
                "closure where safety or law requires it, and the privacy policy sets out " +
                "which ones and for how long."
        )

        Heading("10. Limits")
        Body(
            "The platform is provided as it is. It does not guarantee that any member is " +
                "trustworthy, that any activity is safe, or that any information shared here " +
                "is correct. No software can guarantee an environment free of fitnah, and " +
                "nothing in these terms should be read as promising one."
        )

        SectionDivider()

        Body(
            "Placeholder version 0.1. Governing law, jurisdiction, dispute resolution, " +
                "limitation of liability, indemnity and the notice period for changes are " +
                "all still to be drafted with legal advice."
        )
    }
}

// ── Privacy policy ───────────────────────────────────────────────────────────

@Composable
internal fun PrivacyPolicyScreen(onBack: () -> Unit) {
    LegalScaffold(title = "Privacy policy", onBack = onBack) {
        PlaceholderNotice(
            "A working draft, not yet reviewed by a data protection lawyer and not yet " +
                "checked against UK GDPR, the EU GDPR or any other regime the platform will " +
                "operate under. It must be replaced before launch. It describes what the " +
                "software actually does today, which is the part worth reviewing now."
        )

        Heading("What is collected")
        Body(
            "A display name, whether you are a brother or a sister, your year of birth, your " +
                "city, the languages you speak, and the categories of help you can offer or " +
                "would like to receive. A legal name is optional. A photograph is never " +
                "required and is never the default."
        )
        Body(
            "Your messages, the stated purpose of each conversation, the commitments you " +
                "make, and the record of whether you attended. Reports you make and reports " +
                "made about you."
        )

        Heading("Location")
        Body(
            "Two precisions are held. Your approximate area — normally your city — is what " +
                "other members can see and what distance filters use. An exact address is " +
                "held only where you have given one for a specific purpose, such as a " +
                "delivery to your home, and releasing it always requires a separate decision " +
                "by you, which is recorded."
        )

        Heading("Who can see what")
        Body(
            "Your safeguards decide this, not the platform's defaults. By default other " +
                "members see your display name and your city. Your legal name, what you have " +
                "asked for help with, your private record of service, and your safeguard " +
                "settings themselves are not shown to other members at all."
        )

        Heading("Automated safety checks")
        Body(ContentSignals.DISCLOSURE)

        Heading("Human access")
        Body(
            "Nobody at the platform reads your messages routinely. A moderator sees a " +
                "conversation when a report is made about it or a safety flag is raised on " +
                "it, and every such access is written to the audit log with the moderator's " +
                "identity attached."
        )

        Heading("How long things are kept")
        Body(
            "Conversations are archived after the work they existed for is finished, on a " +
                "schedule you control. Archiving hides a thread and stops new messages; it " +
                "does not destroy evidence that a report may depend on. Audit log entries " +
                "are permanent by design and have no deletion path for anyone. Retention " +
                "periods for everything else are still to be set with legal advice."
        )

        Heading("Your rights")
        Body(
            "You can export your data, correct it, withdraw an optional consent, and close " +
                "your account. Withdrawing a required consent closes the account, because " +
                "the platform cannot operate on terms you no longer agree to. Some records " +
                "survive closure where safety or law requires it."
        )

        Heading("Sharing with others")
        Body(
            "Your data is not sold and is not used for advertising. It is shared with " +
                "processors the platform depends on to function — hosting, and later a " +
                "payment provider and an identity verification provider — and with law " +
                "enforcement where there is a legal obligation or a credible risk to life."
        )

        SectionDivider()

        PrivacyNote(
            text = "This development build stores everything in memory on your own device. " +
                "Nothing is transmitted anywhere, and nothing survives closing the app.",
            modifier = Modifier.padding(
                horizontal = FiSabilillahTheme.spacing.screenHorizontal,
            ),
        )

        Spacer(Modifier.height(FiSabilillahTheme.spacing.xs))

        Body(
            "Placeholder version 0.1. The lawful basis for each processing purpose, the " +
                "named data controller, international transfer arrangements, the retention " +
                "schedule and the data protection contact are all still to be completed."
        )
    }
}

// ── Community guidelines ─────────────────────────────────────────────────────

@Composable
internal fun CommunityGuidelinesScreen(onBack: () -> Unit) {
    LegalScaffold(title = "Community guidelines", onBack = onBack) {
        PlaceholderNotice(
            "A working draft. These guidelines need review by the scholars and community " +
                "leaders the platform intends to serve, as well as by a lawyer, before " +
                "launch. They are written down now so that review has something concrete to " +
                "push back on."
        )

        Heading("Assume the best, and say the difficult thing kindly")
        Body(
            "Most misunderstandings here will be misunderstandings. Before deciding that " +
                "somebody meant harm, consider that a message written quickly reads more " +
                "coldly than it was meant. When you do have to raise something difficult, " +
                "raise it plainly and without an audience."
        )

        Heading("Stay with the purpose you were given")
        Body(
            "A conversation opened about a masjid clean-up is about the masjid clean-up. If " +
                "you need to talk about something else, open a conversation for that. " +
                "Drifting is usually innocent and the app will simply remind you; persisting " +
                "after someone has asked you to stop is not."
        )

        Heading("Do not pursue people")
        Body(
            "Compliments on somebody's appearance, private romantic approaches, and repeated " +
                "contact after a decline all belong to a kind of platform this is not. Where " +
                "marriage is the intention, there is a formal route that involves the wali " +
                "from the start, and it exists precisely so that nobody has to be approached " +
                "in private."
        )

        Heading("Respect the safeguards somebody else has chosen")
        Body(
            "If a member's settings mean a third party joins your conversation, that is " +
                "their decision and it is not an accusation against you. Asking somebody to " +
                "loosen their safeguards, or suggesting a different application where the " +
                "safeguards do not apply, is a serious breach."
        )

        Heading("Be honest about what you know")
        Body(
            "Sharing what you have learned is welcome. Presenting it as a ruling is not. If " +
                "a question is beyond you, say so and point the person towards somebody " +
                "qualified — that is a service in itself, and it is the single most useful " +
                "habit on a platform where people come looking for reliable knowledge."
        )

        Heading("Keep your commitments, or cancel them")
        Body(
            "Somebody is counting on you being there. If you cannot come, cancel in the app " +
                "so your place can be filled. A cancellation is a small inconvenience; a " +
                "silent absence leaves an organiser short at the moment it matters."
        )

        Heading("Guard what people tell you")
        Body(
            "Somebody who asks for food, for a lift, or for help after a bereavement has " +
                "trusted you with something. Do not repeat it, do not discuss it in a group " +
                "thread, and do not mention it to their family. Confidentiality here is not " +
                "a policy; it is the reason people are able to ask at all."
        )

        Heading("Money")
        Body(
            "Do not solicit money in conversations, do not ask anyone to move a payment off " +
                "the platform, and do not offer investments. Requests for financial help go " +
                "through the requests area, where they can be seen and checked."
        )

        Heading("Never contact a child privately")
        Body(
            "Work involving children runs through verified organisations, with background " +
                "checks and with other adults present. There is no circumstance in which " +
                "private contact between a volunteer and a child is appropriate here."
        )

        Heading("When something is wrong, report it")
        Body(
            "Reporting is not backbiting. It goes to a moderator rather than to the " +
                "community, and it is the mechanism by which a person who is harming others " +
                "is stopped. Blocking somebody is always available to you and never requires " +
                "an explanation."
        )

        SectionDivider()

        SectionHeader(title = "What happens when these are broken")
        ContentCard {
            Text(
                text = "Most breaches are met with a note explaining what went wrong. " +
                    "Repeated or serious ones lead to restrictions on particular features, " +
                    "and the most serious to permanent removal. Every outcome comes with a " +
                    "stated reason and a right of appeal, decided by somebody who was not " +
                    "part of the original decision.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Giving and payment compliance ────────────────────────────────────────────

@Composable
internal fun GivingComplianceScreen(onBack: () -> Unit) {
    LegalScaffold(title = "Giving", onBack = onBack) {
        PlaceholderNotice(
            "A working draft describing how giving works and what has to be true before any " +
                "campaign is allowed to collect. It requires review by a charity lawyer and " +
                "by a payments compliance specialist before launch."
        )

        DisclaimerCard(
            title = "Most campaigns cannot take money",
            text = DonationFeatureFlags.unverifiedNotice,
        )

        Heading("Where your card details go")
        Body(
            "Nowhere near this app. Payment is taken on the payment processor's own page, " +
                "opened in your browser so that you can see the address of the site you are " +
                "paying. No card number, expiry or security code is ever typed into this " +
                "application, and none is stored by it."
        )

        Heading("Which appeals can collect")
        Body(
            "An appeal can take money only when two separate things are currently true: the " +
                "organisation behind it holds a verification that has not expired or been " +
                "withdrawn, and the appeal itself has passed a financial review. Both are " +
                "checked at the moment you give rather than remembered from when the appeal " +
                "was approved, so an organisation whose registration lapses stops collecting " +
                "that day."
        )
        Body(
            "The organisation running an appeal cannot switch this on for itself. Only a " +
                "platform administrator can, they must record a reason, and the decision is " +
                "written to a permanent log."
        )

        Heading("What is taken out")
        Body(DonationFeatureFlags.feeNotice)

        Heading("What is still not offered")
        Body(
            "Regular monthly giving. Setting up a standing arrangement needs a cancellation " +
                "route you can find without asking anyone, and a clear answer about what " +
                "happens to a monthly gift when the appeal it was for closes. Until both " +
                "exist, every donation here is a single one."
        )

        Heading("What must be completed before launch")
        Body(
            "The mechanism being built does not mean the obligations below are met. Each is " +
                "a separate piece of work with a separate owner."
        )

        Requirement(
            "Charity registration",
            "The platform, or the entity receiving funds through it, must be a registered " +
                "charity or hold the equivalent status in each jurisdiction it operates in.",
        )
        Requirement(
            "Tax and Gift Aid handling",
            "Correct treatment of donations for tax, including Gift Aid declarations and " +
                "claims in the United Kingdom and the equivalent reliefs elsewhere, with " +
                "receipts that satisfy the relevant revenue authority.",
        )
        Requirement(
            "Charitable solicitation registration",
            "Registration to solicit donations, obtained separately for every jurisdiction " +
                "where donors will be asked. This is per state in the United States, and it " +
                "is not covered by charity registration alone.",
        )
        Requirement(
            "Identity verification of organisations",
            "Know-your-customer checks on every organisation that can receive funds, and on " +
                "the individuals who control it, before a single campaign of theirs is able " +
                "to take money.",
        )
        Requirement(
            "Anti-fraud and sanctions screening",
            "Screening against sanctions and politically exposed person lists, transaction " +
                "monitoring, and a documented process for freezing a campaign and returning " +
                "funds when fraud is suspected.",
        )
        Requirement(
            "A licensed payment provider",
            "A provider licensed to handle charitable funds, integrated so that card details " +
                "never touch this platform's own systems.",
        )
        Requirement(
            "Refund and dispute policy",
            "A written, published policy covering refunds, chargebacks and disputes, " +
                "including what happens to a donation when a campaign is later suspended.",
        )

        Heading("Zakat")
        Body(
            "The platform never asserts that a campaign is zakat eligible. Where a campaign " +
                "carries a zakat marking, it is because a qualified body or scholar stated it " +
                "and that statement is recorded against the campaign with their name on it. " +
                "The platform records the attestation; it does not make the ruling, and it " +
                "will not add one of its own. If your circumstances are unusual, ask a " +
                "qualified scholar who knows them."
        )

        Heading("What the platform will not do")
        Body(
            "It will not take a percentage of a donation as a fee. It will not display " +
                "donor leaderboards or amounts alongside names. It will not send reminders " +
                "designed to pressure somebody into giving. Anonymous giving will always be " +
                "available."
        )

        SectionDivider()

        PrivacyNote(
            text = "No payment information of any kind is collected by this build, and no " +
                "payment provider is integrated with it.",
            modifier = Modifier.padding(
                horizontal = FiSabilillahTheme.spacing.screenHorizontal,
            ),
        )

        Spacer(Modifier.height(FiSabilillahTheme.spacing.xs))

        Body(
            "Placeholder version 0.1. The full checklist is kept alongside the code in " +
                "docs/payment-compliance.md, and this screen must be kept in step with it."
        )
    }
}

// ── Shared pieces ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegalScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        ScreenColumn(contentPadding = padding) {
            Spacer(Modifier.height(spacing.xs))
            content()
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

@Composable
private fun PlaceholderNotice(text: String) {
    DisclaimerCard(title = "Placeholder — not yet reviewed by a lawyer", text = text)
}

@Composable
private fun Heading(text: String) {
    val spacing = FiSabilillahTheme.spacing
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(
                start = spacing.screenHorizontal,
                end = spacing.screenHorizontal,
                top = spacing.lg,
                bottom = spacing.xxs,
            )
            .semantics { heading() },
    )
}

@Composable
private fun Body(text: String) {
    val spacing = FiSabilillahTheme.spacing
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = spacing.screenHorizontal,
            vertical = spacing.xxs,
        ),
    )
}

@Composable
private fun Requirement(title: String, body: String) {
    val spacing = FiSabilillahTheme.spacing
    ContentCard {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
