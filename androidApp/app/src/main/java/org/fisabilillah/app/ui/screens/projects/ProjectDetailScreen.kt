package org.fisabilillah.app.ui.screens.projects

import androidx.compose.foundation.layout.Column
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
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.DetailRow
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.PrivacyNote
import org.fisabilillah.app.ui.components.ScreenColumn
import org.fisabilillah.app.ui.components.SecondaryButton
import org.fisabilillah.app.ui.components.SectionDivider
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.model.Project
import org.fisabilillah.core.model.ProjectTask

/**
 * One project, with the work broken into tasks.
 *
 * Tasks are shown with their status and nothing else about the people doing them. Who
 * claimed what is between them and the organiser; it is not a scoreboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectDetailScreen(
    project: Project?,
    tasks: List<ProjectTask>,
    organiserName: String,
    onMessageOrganiser: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Project") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (project == null) {
            ScreenColumn(contentPadding = padding) {
                EmptyState(
                    title = "This project is not available",
                    body = "It may have been closed, or it may belong to a community you " +
                        "are not part of.",
                    actionLabel = "Go back",
                    onAction = onBack,
                )
            }
            return@Scaffold
        }

        ScreenColumn(contentPadding = padding) {
            SectionHeader(title = project.title, subtitle = project.status.displayName)

            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                Text(text = project.summary, style = MaterialTheme.typography.bodyLarge)
            }

            SectionHeader(title = "Details")
            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                DetailRow(label = "Organised by", value = organiserName)
                DetailRow(label = "Kind of work", value = project.category.displayName)
                DetailRow(label = "Format", value = project.format.displayName)
                DetailRow(
                    label = "Where",
                    value = project.place?.publicLabel
                        ?: "Not tied to one place.",
                )
                DetailRow(
                    label = "Gender arrangement",
                    value = project.genderArrangement.displayName,
                )
                val startsAt = project.startsAt
                if (startsAt != null) {
                    DetailRow(label = "Starts", value = startsAt.toString().take(10))
                }
                val targetCompletionAt = project.targetCompletionAt
                if (targetCompletionAt != null) {
                    DetailRow(
                        label = "Aiming to finish by",
                        value = targetCompletionAt.toString().take(10),
                    )
                }
                DetailRow(
                    label = "Volunteers needed",
                    value = if (project.volunteersNeeded > 0) {
                        "${project.volunteersNeeded} more"
                    } else {
                        "The organiser has not asked for more people at the moment."
                    },
                )
            }

            SectionDivider()

            SectionHeader(
                title = "Tasks",
                subtitle = "What the work is actually made of",
            )

            if (tasks.isEmpty()) {
                EmptyState(
                    title = "No tasks yet",
                    body = "The organiser has not broken this project into tasks. Message " +
                        "them if you would like to know where help is needed.",
                )
            } else {
                for (task in tasks) {
                    TaskCard(task)
                }
            }

            Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                Spacer(Modifier.height(spacing.md))
                PrivacyNote(
                    text = "Messaging the organiser shares your display name and profile " +
                        "with them, and states which project you are writing about.",
                )
                Spacer(Modifier.height(spacing.md))
                SecondaryButton(text = "Message the organiser", onClick = onMessageOrganiser)
            }
        }
    }
}

@Composable
private fun TaskCard(task: ProjectTask) {
    val spacing = FiSabilillahTheme.spacing

    ContentCard(contentDescription = "${task.title}. ${task.status.displayName}.") {
        Text(text = task.title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = task.status.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val detail = task.detail
        if (detail != null) {
            Spacer(Modifier.height(spacing.xs))
            Text(text = detail, style = MaterialTheme.typography.bodyMedium)
        }
        val dueAt = task.dueAt
        if (dueAt != null) {
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = "Due by ${dueAt.toString().take(10)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (task.requiredSkills.isNotEmpty()) {
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = "Needs: " + task.requiredSkills.joinToString(", ") { it.displayName },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
