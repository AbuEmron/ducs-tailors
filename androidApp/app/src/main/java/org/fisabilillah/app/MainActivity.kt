package org.fisabilillah.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.fisabilillah.app.di.AppGraph
import org.fisabilillah.app.di.canModerate
import org.fisabilillah.app.ui.navigation.FiSabilillahNavHost
import org.fisabilillah.app.ui.navigation.PrimaryDestination
import org.fisabilillah.app.ui.theme.FiSabilillahTheme

internal class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val graph = (application as FiSabilillahApplication).graph

        setContent {
            FiSabilillahTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    FiSabilillahApp(graph)
                }
            }
        }
    }
}

@Composable
private fun FiSabilillahApp(graph: AppGraph) {
    val navController = rememberNavController()
    val principal by graph.session.principal.collectAsState()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // The bottom bar appears only once someone is signed in and only on the six primary
    // destinations. Detail screens, forms, and everything in the safety and moderation
    // areas are full-screen, so a person doing something consequential is not one stray
    // tap away from losing it.
    val showBottomBar = principal != null &&
        PrimaryDestination.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                PrimaryNavigationBar(navController = navController, currentRoute = currentRoute)
            }
        },
    ) { padding ->
        FiSabilillahNavHost(
            navController = navController,
            graph = graph,
            contentPadding = padding,
            showModeration = principal.canModerate(),
        )
    }
}

@Composable
private fun PrimaryNavigationBar(
    navController: NavHostController,
    currentRoute: String?,
) {
    NavigationBar {
        for (destination in PrimaryDestination.entries) {
            val selected = currentRoute == destination.route ||
                navController.currentBackStackEntry?.destination?.hierarchy
                    ?.any { it.route == destination.route } == true

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.contentDescription,
                    )
                },
                label = { Text(destination.label) },
                alwaysShowLabel = true,
            )
        }
    }
}
