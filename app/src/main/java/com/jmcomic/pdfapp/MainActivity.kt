package com.jmcomic.pdfapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jmcomic.pdfapp.data.ThemePrefs
import com.jmcomic.pdfapp.ui.screen.HomeScreen
import com.jmcomic.pdfapp.ui.screen.SettingsScreen
import com.jmcomic.pdfapp.ui.theme.JMComicPDFTheme
import com.jmcomic.pdfapp.ui.theme.ThemeMode
import com.jmcomic.pdfapp.viewmodel.HomeViewModel
import com.jmcomic.pdfapp.viewmodel.SettingsViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    companion object { private const val TAG = "JMComicPDF" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate start")

        try {
            enableEdgeToEdge()
        } catch (e: Exception) {
            Log.e(TAG, "enableEdgeToEdge failed", e)
        }

        setContent {
            var themeMode by remember { mutableStateOf(ThemePrefs.load(this)) }
            val cycleTheme = {
                themeMode = ThemePrefs.next(themeMode)
                ThemePrefs.save(this, themeMode)
            }

            JMComicPDFTheme(themeMode = themeMode) {
                val homeViewModel: HomeViewModel = viewModel()
                val settingsViewModel: SettingsViewModel = viewModel()
                MainShell(
                    homeViewModel = homeViewModel,
                    settingsViewModel = settingsViewModel,
                    themeMode = themeMode,
                    onCycleTheme = cycleTheme,
                    onOpenPdf = { filePath -> openPdf(filePath) },
                    onTabChanged = { tab ->
                        // Refresh history when switching to settings tab
                        if (tab == 1) settingsViewModel.refreshHistory()
                    }
                )
            }
        }
        Log.d(TAG, "onCreate done")
    }

    private fun openPdf(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.w(TAG, "PDF not found: $filePath")
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                this,
                "com.jmcomic.pdfapp.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openPdf failed", e)
        }
    }
}

// ── Tab definitions ──────────────────────────────────────────

private data class Tab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val TABS = listOf(
    Tab("首页", Icons.Rounded.Home, Icons.Outlined.Home),
    Tab("设置", Icons.Rounded.Settings, Icons.Outlined.Settings),
)

// ── Main shell with bottom nav ───────────────────────────────

@Composable
private fun MainShell(
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    onOpenPdf: (String) -> Unit,
    onTabChanged: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = scheme.surface,
                tonalElevation = 0.dp,
            ) {
                TABS.forEachIndexed { index, tab ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (selectedTab != index) {
                                selectedTab = index
                                onTabChanged(index)
                            }
                        },
                        icon = {
                            Icon(
                                if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                            )
                        },
                        label = {
                            Text(
                                tab.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = scheme.primary,
                            selectedTextColor = scheme.primary,
                            unselectedIconColor = scheme.onSurfaceVariant,
                            unselectedTextColor = scheme.onSurfaceVariant,
                            indicatorColor = scheme.primary.copy(alpha = 0.10f),
                        ),
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(durationMillis = 250),
                label = "tab-crossfade"
            ) { tab ->
                when (tab) {
                    0 -> HomeScreen(
                        viewModel = homeViewModel,
                        onOpenPdf = onOpenPdf,
                    )
                    1 -> SettingsScreen(
                        viewModel = settingsViewModel,
                        onOpenPdf = onOpenPdf,
                        themeMode = themeMode,
                        onCycleTheme = onCycleTheme,
                    )
                }
            }
        }
    }
}
