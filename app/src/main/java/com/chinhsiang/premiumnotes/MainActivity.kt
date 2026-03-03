package com.chinhsiang.premiumnotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chinhsiang.premiumnotes.auth.GoogleAuthManager
import com.chinhsiang.premiumnotes.ui.views.EditorView
import com.chinhsiang.premiumnotes.ui.views.FoldersView
import com.chinhsiang.premiumnotes.ui.views.NotesListView
import com.chinhsiang.premiumnotes.ui.views.SettingsView

val LightColors = lightColorScheme(
    background = Color(0xFFF7F7F2),
    surface = Color(0xFFFFFFFF),
    primary = Color(0xFFFF9500),
    onPrimary = Color.White,
    onBackground = Color(0xFF1C1C1E),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF8E8E93)
)

val DarkColors = darkColorScheme(
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    primary = Color(0xFFFF9F0A),
    onPrimary = Color.White,
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF38383A),
    onSurfaceVariant = Color(0xFF8E8E93)
)

@Composable
fun GNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val googleAuthManager = GoogleAuthManager(this)

        setContent {
            GNoteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.systemBarsPadding()) {
                        AppNavigation(googleAuthManager)
                    }
                }
            }
        }
    }
}

@Composable
fun AppNavigation(googleAuthManager: GoogleAuthManager) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "folders_view") {
        
        composable("folders_view") {
            FoldersView(
                onNavigateToSettings = { navController.navigate("settings_view") },
                onNavigateToFolder = { folderId, folderName ->
                    navController.navigate("notes_list_view/$folderId/$folderName")
                }
            )
        }

        composable("notes_list_view/{folderId}/{folderName}") { backStackEntry ->
            val folderId = backStackEntry.arguments?.getString("folderId") ?: ""
            val folderName = backStackEntry.arguments?.getString("folderName") ?: ""
            NotesListView(
                folderId = folderId,
                folderName = folderName,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEditor = { noteId, fId ->
                    navController.navigate("editor_view/$noteId/$fId")
                }
            )
        }

        composable("editor_view/{noteId}/{folderId}") { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId") ?: ""
            val folderId = backStackEntry.arguments?.getString("folderId") ?: ""
            EditorView(
                noteId = noteId,
                folderId = folderId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("settings_view") {
            SettingsView(
                onNavigateBack = { navController.popBackStack() },
                googleAuthManager = googleAuthManager
            )
        }
    }
}
