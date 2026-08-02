package org.fisabilillah.app.ui.screens.requests

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import org.fisabilillah.app.ui.components.ChipRow
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.LoadingState
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.RefusalNotice
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.app.ui.viewmodel.ScreenState
import org.fisabilillah.core.model.RequestId
import org.fisabilillah.core.model.VisibleServiceRequest

/**
 * People asking for help.
 *
 * The hardest screen in the product to get right. Somebody who needs food this week is not
 * content to be browsed, so: no counts of who has looked, no donation thermometers, an
 * area rather than an address, and where a person chose not to be named, no attempt is
 * made to identify them by inference.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RequestsScreen(
    state: ScreenState<List<VisibleServiceRequest>>,
    onSelect: (RequestId) -> Unit,
    onCreate: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing
    val requests: List<VisibleServiceRequest> = state.data ?: emptyList()
    val refusal: String? = state.refusal

    Scaffold(
        topBar = { TopAppBar(title = { Text("Requests") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item {
                SectionHeader(
                    title = "Where help has been asked for",
                    subtitle = "Treat what people have written here as something entrusted " +
                        "to you",
                    action = {
                        TextButton(
                            onClick = onCreate,
                            modifier = Modifier.heightIn(min = spacing.minimumTouchTarget),
                        ) {
                            Text("Ask for help")
                        }
                    },
                )
            }

            item {
                PrivacyNote(
                    text = "Requests show an area, never a street address. An exact address " +
                        "is shared only with a helper the person has accepted, and only when " +
                        "they choose to send it.",
                    modifier = Modifier.padding(
                        horizontal = spacing.screenHorizontal,
                        vertical = spacing.xs,
                    ),
                )
            }

            when {
                state.loading -> item { LoadingState(label = "Loading requests") }

                refusal != null -> item { RefusalNotice(message = refusal) }

                requests.isEmpty() -> item {
                    EmptyState(
                        title = "No open requests",
                        body = "Nothing is outstanding in the areas you can see. If you need " +
                            "something yourself, you can post a request and choose how much " +
                            "of your identity is shown.",
                        actionLabel = "Ask for help",
                        onAction = onCreate,
                    )
                }

                else -> items(requests) { request ->
                    RequestSummaryCard(
                        request = request,
                        onClick = { onSelect(request.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestSummaryCard(
    request: VisibleServiceRequest,
    onClick: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    ContentCard(
        onClick = onClick,
        contentDescription = "${request.title}. ${request.category.displayName}. " +
            "${request.locationLabel}.",
    ) {
        Text(text = request.title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = request.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(spacing.sm))
        ChipRow(
            labels = buildList {
                add(request.category.displayName)
                add(request.urgency.displayName)
                add(request.locationLabel)
            },
        )

        Spacer(Modifier.height(spacing.sm))
        Text(
            text = "Asked by ${requesterLabel(request)}",
            style = MaterialTheme.typography.bodySmall,
        )
        val mediator = request.mediatingOrganizationName
        if (mediator != null) {
            Text(
                text = "Handled by $mediator",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = "Open until ${request.expiresAt.toString().take(10)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The name to show. Where the domain layer withheld one, that is a decision the person
 * made, and the interface says so plainly rather than inventing an identity for them.
 */
internal fun requesterLabel(request: VisibleServiceRequest): String =
    request.requesterDisplayName ?: "A member of the community"
