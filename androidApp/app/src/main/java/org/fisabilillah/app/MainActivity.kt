package org.fisabilillah.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.fisabilillah.app.di.AppGraph
import org.fisabilillah.app.di.SignInResult
import org.fisabilillah.app.di.canModerate
import org.fisabilillah.app.ui.navigation.FiSabilillahNavHost
import org.fisabilillah.app.ui.navigation.PrimaryDestination
import org.fisabilillah.app.ui.navigation.Routes
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
    var startUp by remember { mutableStateOf<SignInResult?>(null) }

    // One attempt to bring back the session saved on this device. Until it finishes the
    // app shows nothing but a spinner: flashing the sign-in screen at somebody who is in
    // fact still signed in is both alarming and an invitation to type a password that was
    // not needed.
    LaunchedEffect(Unit) {
        startUp = graph.session.restore()
    }

    // The outcome becomes the graph's start destination rather than a navigate() call.
    // Navigating from here would run before the NavHost below has been composed, and a
    // NavController with no graph throws rather than queuing.
    val start = startUp ?: run {
        StartUpScreen()
        return
    }
    val startDestination = when (start) {
        is SignInResult.Ready -> Routes.HOME
        is SignInResult.NeedsOnboarding -> Routes.ONBOARDING_PROFILE
        else -> Routes.LANDING
    }

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
            startDestination = startDestination,
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

/**
 * Held while the stored session is checked.
 *
 * Deliberately almost empty. A splash with a logo animation would add a second to every
 * cold start for decoration; this is a spinner and a line of text that says what is
 * happening, and it is gone as soon as the answer arrives.
 */
@Composable
private fun StartUpScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = "Checking your session",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
