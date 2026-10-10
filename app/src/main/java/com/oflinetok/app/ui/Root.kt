package com.oflinetok.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class Tab(val label: String, val selected: ImageVector, val unselected: ImageVector) {
    Home("Home", Icons.Filled.Home, Icons.Outlined.Home),
    Search("Search", Icons.Filled.Search, Icons.Outlined.Search),
    Library("Me", Icons.Filled.Person, Icons.Outlined.Person),
}

@Composable
fun OflineTokRoot(vm: AppViewModel = viewModel()) {
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(hasVideoPermission(context)) }
    var partialOnly by remember { mutableStateOf(hasPartialAccessOnly(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permitted = hasVideoPermission(context)
        partialOnly = hasPartialAccessOnly(context)
        if (permitted) vm.load()
    }

    // Re-check on return: the user may have changed access in system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permitted = hasVideoPermission(context)
        partialOnly = hasPartialAccessOnly(context)
        if (permitted) vm.load()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flushExport() }

    vm.restoreMessage.collectAsStateWithLifecycle().value?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::dismissRestoreMessage,
            title = { Text(msg.title) },
            text = { Text(msg.body) },
            confirmButton = { TextButton(onClick = vm::dismissRestoreMessage) { Text("OK") } },
        )
    }

    if (!permitted) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PermissionScreen(onRequest = { launcher.launch(videoPermissions) })
        }
        return
    }

    val openedFeed by vm.openedFeed.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }

    BackHandler(enabled = openedFeed == null && tab != Tab.Home) { tab = Tab.Home }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Scaffold(
            containerColor = Color.Black,
            bottomBar = {
                NavigationBar(containerColor = Color.Black) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = {
                                if (t == Tab.Home && tab == Tab.Home) vm.reshuffle()
                                tab = t
                            },
                            icon = { Icon(if (tab == t) t.selected else t.unselected, t.label) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    Tab.Home -> HomeFeed(vm, active = openedFeed == null)
                    Tab.Search -> SearchScreen(vm)
                    Tab.Library -> LibraryScreen(
                        vm = vm,
                        partialOnly = partialOnly,
                        onManageAccess = { launcher.launch(videoPermissions) },
                    )
                }
            }
        }

        openedFeed?.let { request ->
            BackHandler { vm.closeFeed() }
            OpenedFeed(vm, request)
        }
    }
}
