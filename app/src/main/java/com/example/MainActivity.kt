package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.OpenVViewModel
import com.example.ui.components.FormatBadge
import com.example.ui.screens.ExplorerScreen
import com.example.ui.screens.InspectorScreen
import com.example.ui.screens.OivManagerScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.OpenVStudioTheme
import kotlinx.coroutines.launch
import java.io.File

object Routes {
    const val WORKSPACE = "workspace"
    const val EXPLORER = "explorer"
    const val MODS = "mods"
    const val TOOLS = "tools"
    const val INSPECTOR = "inspector"
}

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topDestinations = listOf(
    TopLevelDestination(
        route = Routes.WORKSPACE,
        labelRes = R.string.nav_workspace,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_workspace"
    ),
    TopLevelDestination(
        route = Routes.EXPLORER,
        labelRes = R.string.nav_explorer,
        selectedIcon = Icons.Filled.FolderOpen,
        unselectedIcon = Icons.Outlined.FolderOpen,
        testTag = "nav_explorer"
    ),
    TopLevelDestination(
        route = Routes.MODS,
        labelRes = R.string.nav_mods,
        selectedIcon = Icons.Filled.Extension,
        unselectedIcon = Icons.Outlined.Extension,
        testTag = "nav_mods"
    ),
    TopLevelDestination(
        route = Routes.TOOLS,
        labelRes = R.string.nav_tools,
        selectedIcon = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build,
        testTag = "nav_tools"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenVStudioApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenVStudioApp(
    viewModel: OpenVViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val explorerState by viewModel.explorerState.collectAsStateWithLifecycle()
    val inspectorState by viewModel.inspectorState.collectAsStateWithLifecycle()
    val oivState by viewModel.oivState.collectAsStateWithLifecycle()
    val toolsState by viewModel.toolsState.collectAsStateWithLifecycle()
    val pendingExport by viewModel.pendingExportBytes.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.WORKSPACE

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // SAF Launchers (Zero-permission Play-compliant document access)
    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.openExternalDocumentUri(
                uri = uri,
                onOpenExplorer = { navigateTopLevel(Routes.EXPLORER) },
                onOpenOiv = { navigateTopLevel(Routes.MODS) },
                onOpenInspector = { navController.navigate(Routes.INSPECTOR) }
            )
        }
    }

    val injectDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.injectFileFromUri(uri)
        }
    }

    val openTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.openExternalTreeUri(uri)
            navigateTopLevel(Routes.EXPLORER)
        }
    }

    val analyzeHeaderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.analyzeHeaderFromUri(uri)
        }
    }

    val exportDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.completeExport(uri)
        } else {
            viewModel.clearPendingExport()
        }
    }

    LaunchedEffect(pendingExport) {
        val pending = pendingExport
        if (pending != null) {
            exportDocLauncher.launch(pending.first)
        }
    }

    OpenVStudioTheme(darkTheme = settings.isDarkTheme) {
        val isInspectorRoute = currentRoute == Routes.INSPECTOR

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    if (!isInspectorRoute) {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            actions = {
                                FormatBadge(
                                    label = if (settings.isEditMode) "EDIT" else "READ",
                                    accentColor = if (settings.isEditMode) ElectricEmerald else AmberGold,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                },
                bottomBar = {
                    if (!useNavRail && !isInspectorRoute) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            topDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavRail && !isInspectorRoute) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            topDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = Routes.WORKSPACE
                        ) {
                            composable(Routes.WORKSPACE) {
                                WorkspaceScreen(
                                    settings = settings,
                                    oivCount = oivState.packages.size,
                                    onToggleEditMode = viewModel::toggleEditMode,
                                    onOpenRpfOrOiv = { openDocLauncher.launch(arrayOf("*/*")) },
                                    onOpenFolderTree = { openTreeLauncher.launch(null) },
                                    onOpenSandboxExplorer = {
                                        viewModel.openSandboxRoot()
                                        navigateTopLevel(Routes.EXPLORER)
                                    },
                                    onOpenQuickPath = { path ->
                                        viewModel.openQuickPath(path)
                                        navigateTopLevel(Routes.EXPLORER)
                                    },
                                    onCreateNewRpf = { name ->
                                        viewModel.createNewRpf7(name)
                                        navigateTopLevel(Routes.EXPLORER)
                                    },
                                    onOpenRecent = { rec ->
                                        val f = File(rec.path)
                                        if (f.exists() && rec.type == "RPF") {
                                            viewModel.openRpfArchive(f)
                                            navigateTopLevel(Routes.EXPLORER)
                                        } else if (f.exists() && f.isDirectory) {
                                            viewModel.openFsDirectory(f)
                                            navigateTopLevel(Routes.EXPLORER)
                                        } else {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Berkas tidak ditemukan di lokasi sebelumnya.")
                                            }
                                        }
                                    },
                                    onNavigateToMods = { navigateTopLevel(Routes.MODS) }
                                )
                            }

                            composable(Routes.EXPLORER) {
                                ExplorerScreen(
                                    state = explorerState,
                                    isEditMode = settings.isEditMode,
                                    onToggleEditMode = viewModel::toggleEditMode,
                                    onStepUp = viewModel::stepUpDirectory,
                                    onBreadcrumbClick = viewModel::navigateBreadcrumb,
                                    onItemClick = { item ->
                                        viewModel.onItemClicked(
                                            item = item,
                                            onOpenInspector = { navController.navigate(Routes.INSPECTOR) },
                                            onOpenOiv = { navigateTopLevel(Routes.MODS) }
                                        )
                                    },
                                    onSafItemClick = { doc ->
                                        viewModel.onSafItemClick(
                                            doc = doc,
                                            onOpenExplorer = { navigateTopLevel(Routes.EXPLORER) },
                                            onOpenOiv = { navigateTopLevel(Routes.MODS) },
                                            onOpenInspector = { navController.navigate(Routes.INSPECTOR) }
                                        )
                                    },
                                    onSearchChange = viewModel::updateSearchQuery,
                                    onFilterChange = viewModel::setExplorerFilter,
                                    onImportFileIntoArchive = { injectDocLauncher.launch(arrayOf("*/*")) },
                                    onCreateFolder = viewModel::createFolder,
                                    onExportItem = viewModel::prepareExport,
                                    onRenameItem = viewModel::renameItem,
                                    onDeleteItem = viewModel::deleteItem
                                )
                            }

                            composable(Routes.MODS) {
                                OivManagerScreen(
                                    state = oivState,
                                    installedMods = settings.installedMods,
                                    onPickOivFile = { openDocLauncher.launch(arrayOf("*/*")) },
                                    onSelectPackage = viewModel::selectOivPackage,
                                    onInstallSelected = viewModel::installSelectedOiv,
                                    onUninstallMod = viewModel::uninstallMod
                                )
                            }

                            composable(Routes.TOOLS) {
                                ToolsScreen(
                                    state = toolsState,
                                    settings = settings,
                                    onHashInputChange = viewModel::updateHashInput,
                                    onDictionaryFilterChange = viewModel::updateDictionaryFilter,
                                    onAnalyzeHeaderClick = { analyzeHeaderLauncher.launch(arrayOf("*/*")) },
                                    onToggleDarkTheme = viewModel::toggleDarkTheme,
                                    onToggleAutoBackup = viewModel::toggleAutoBackup,
                                    onResetSandbox = viewModel::resetSandbox,
                                    onCopyFeedback = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }

                            composable(Routes.INSPECTOR) {
                                InspectorScreen(
                                    state = inspectorState,
                                    isEditMode = settings.isEditMode,
                                    isDarkTheme = settings.isDarkTheme,
                                    onToggleEditMode = viewModel::toggleEditMode,
                                    onBack = { navController.popBackStack() },
                                    onTextChanged = viewModel::updateEditedText,
                                    onRevertChanges = viewModel::revertInspectorChanges,
                                    onSaveChanges = viewModel::saveInspectorChanges,
                                    onExportItem = viewModel::prepareExport,
                                    onMessage = viewModel::notifyMessage
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
