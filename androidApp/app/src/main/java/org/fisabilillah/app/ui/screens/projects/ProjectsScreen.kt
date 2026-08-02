package org.fisabilillah.app.ui.screens.projects

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.fisabilillah.app.ui.components.ChipRow
import org.fisabilillah.app.ui.components.ContentCard
import org.fisabilillah.app.ui.components.EmptyState
import org.fisabilillah.app.ui.components.SectionHeader
import org.fisabilillah.app.ui.theme.FiSabilillahTheme
import org.fisabilillah.core.model.GenderArrangement
import org.fisabilillah.core.model.Project
import org.fisabilillah.core.model.ProjectId

/**
 * Longer pieces of work that several people are doing together.
 *
 * A project is the honest unit for anything that will not finish in an afternoon. The card
 * shows what stage it is at and what is still needed, never how popular it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectsScreen(
    projects: List<Project>,
    onSelect: (ProjectId) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Projects") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item {
                SectionHeader(
                    title = "Work under way",
                    subtitle = "Longer pieces of work you can join partway through",
                )
            }

            if (projects.isEmpty()) {
                item {
                    EmptyState(
                        title = "No projects listed",
                        body = "Nothing is running publicly at the moment. Projects in " +
                            "communities you have not joined are not shown here.",
                    )
                }
            } else {
                items(projects) { project ->
                    ProjectSummaryCard(
                        project = project,
                        onClick = { onSelect(project.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectSummaryCard(
    project: Project,
    onClick: () -> Unit,
) {
    val spacing = FiSabilillahTheme.spacing

    ContentCard(
        onClick = onClick,
        contentDescription = "${project.title}. ${project.status.displayName}.",
    ) {
        Text(text = project.title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = project.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(spacing.sm))
        ChipRow(
            labels = buildList {
                add(project.status.displayName)
                add(project.category.displayName)
                add(project.format.displayName)
                val place = project.place
                if (place != null) add(place.publicLabel)
                if (project.genderArrangement != GenderArrangement.NOT_APPLICABLE) {
                    add(project.genderArrangement.displayName)
                }
            },
        )

        if (project.volunteersNeeded > 0) {
            Spacer(Modifier.height(spacing.sm))
            Text(
                text = "${project.volunteersNeeded} volunteers still needed",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        val targetCompletionAt = project.targetCompletionAt
        if (targetCompletionAt != null) {
            Text(
                text = "Aiming to finish by ${targetCompletionAt.toString().take(10)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
