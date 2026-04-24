package org.role.samples_button

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import org.role.samples_button.core.designsystem.SamplesButtonTheme
import org.role.samples_button.feature.browser.impl.FileBrowserScreen
import org.role.samples_button.feature.onboarding.impl.OnboardingScreen
import org.role.samples_button.feature.onboarding.impl.OnboardingViewModel
import org.role.samples_button.feature.soundboard.impl.GroupDetailScreen
import org.role.samples_button.feature.soundboard.impl.GroupDetailViewModel
import org.role.samples_button.feature.soundboard.impl.SoundBoardScreen
import org.role.samples_button.feature.soundboard.impl.SoundBoardViewModel

@AndroidEntryPoint(ComponentActivity::class)
class MainActivity : Hilt_MainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SamplesButtonTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "soundboard") {
                    composable("soundboard") {
                        val viewModel: SoundBoardViewModel =
                            androidx.hilt.navigation.compose.hiltViewModel()
                        SoundBoardScreen(
                            viewModel = viewModel,
                            onNavigateToGroup = { groupId ->
                                navController.navigate("group_detail/$groupId")
                            },
                            onNavigateToFileBrowser = { groupId ->
                                navController.navigate("file_browser/$groupId")
                            },
                            onNavigateToOnboarding = {
                                navController.navigate("onboarding?firstRun=true")
                            }
                        )
                    }
                    composable(
                        route = "group_detail/{groupId}",
                        arguments = listOf(navArgument("groupId") { type = NavType.LongType })
                    ) {
                        val viewModel: GroupDetailViewModel =
                            androidx.hilt.navigation.compose.hiltViewModel()
                        GroupDetailScreen(
                            viewModel = viewModel,
                            onNavigateToFileBrowser = { groupId ->
                                navController.navigate("file_browser/$groupId")
                            },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "file_browser/{groupId}",
                        arguments = listOf(navArgument("groupId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val groupId = backStackEntry.arguments!!.getLong("groupId")
                        FileBrowserScreen(
                            groupId = groupId,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = "onboarding?firstRun={firstRun}",
                        arguments = listOf(navArgument("firstRun") {
                            type = NavType.BoolType
                            defaultValue = false
                        })
                    ) { backStackEntry ->
                        val isFirstRun = backStackEntry.arguments?.getBoolean("firstRun") ?: false
                        val viewModel: OnboardingViewModel =
                            androidx.hilt.navigation.compose.hiltViewModel()
                        OnboardingScreen(
                            isFirstRun = isFirstRun,
                            onFinish = { navController.popBackStack() },
                            onMarkSeen = { viewModel.markSeen() }
                        )
                    }
                }
            }
        }
    }
}
