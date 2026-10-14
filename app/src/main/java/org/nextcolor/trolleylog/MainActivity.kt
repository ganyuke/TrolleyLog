package org.nextcolor.trolleylog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import org.nextcolor.trolleylog.ui.SettingsScreen
import org.nextcolor.trolleylog.ui.EditRideScreen
import org.nextcolor.trolleylog.ui.HistoryScreen
import org.nextcolor.trolleylog.ui.StatsScreen
import org.nextcolor.trolleylog.ui.TodayScreen
import org.nextcolor.trolleylog.ui.TrolleyLogTheme
import org.nextcolor.trolleylog.ui.daySummary
import org.nextcolor.trolleylog.ui.rideCount
import org.nextcolor.trolleylog.ui.todayTitle
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { TrolleyLogTheme { App(model) } }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Rounded.Tram),
    HISTORY("History", Icons.Rounded.History),
    STATS("Statistics", Icons.Rounded.Insights),
}

@Composable
fun App(model: MainViewModel, today: LocalDate = LocalDate.now()) {
    val rides by model.store.rides.collectAsState()
    val draft = model.draft
    if (draft != null) {
        EditRideScreen(
            draft = draft,
            allRides = rides,
            onChange = { model.draft = draft.copy(ride = it) },
            onSave = model::saveDraft,
            onDelete = model::deleteDraft,
            onCancel = { model.draft = null },
        )
        return
    }

    var inSettings by rememberSaveable { mutableStateOf(false) }
    if (inSettings) {
        SettingsScreen(rides, model.store, onBack = { inSettings = false })
        return
    }

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.entries[tabIndex]
    // Each tab starts with its large title open again.
    val scroll = key(tab) { TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState()) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(if (tab == Tab.TODAY) todayTitle(today) else tab.label) },
                subtitle = {
                    when (tab) {
                        Tab.TODAY -> Text(daySummary(rides.filter { it.date == today }))
                        Tab.HISTORY -> Text(rideCount(rides))
                        Tab.STATS -> {}
                    }
                },
                actions = {
                    IconButton(onClick = { inSettings = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                for (t in Tab.entries) {
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { tabIndex = t.ordinal },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.TODAY) {
                ExtendedFloatingActionButton(
                    onClick = { model.startRide() },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Board") },
                )
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (tab) {
                Tab.TODAY -> TodayScreen(rides, today, onEdit = model::edit, onArrived = model::markArrived)
                Tab.HISTORY -> HistoryScreen(rides, onEdit = model::edit)
                Tab.STATS -> StatsScreen(rides, today)
            }
        }
    }
}
