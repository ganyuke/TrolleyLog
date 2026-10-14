package org.nextcolor.trolleylog

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.nextcolor.trolleylog.data.PlaceInCar
import org.nextcolor.trolleylog.data.TrainPart
import org.nextcolor.trolleylog.ui.StationPicker
import org.nextcolor.trolleylog.ui.TrolleyLogTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders each screen to app/build/screenshots so the layout can be looked at without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h900dp-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val evening = Samples.ride("t2", Samples.today, "17:21", car = "5009A", position = 1, from = "oldS", to = "utcS", line = "blue")

    private fun model(vararg extra: org.nextcolor.trolleylog.data.Ride, empty: Boolean = false): MainViewModel {
        val app = RuntimeEnvironment.getApplication() as Application
        app.filesDir.resolve("rides.json").delete()
        return MainViewModel(app).also { if (!empty) it.store.import(Samples.history + Samples.morning + extra) }
    }

    private fun shot(name: String, dark: Boolean = false, tab: String? = null, map: Boolean = false, content: @Composable () -> Unit) {
        rule.setContent { TrolleyLogTheme(dark = dark, dynamicColor = false, content = content) }
        if (tab != null) rule.onNodeWithText(tab).performClick()
        if (map) {
            // Tiles are only asked for once the map is drawn, so draw it once,
            // then give them time to download before the real picture is taken.
            rule.onRoot().captureRoboImage("build/screenshots/$name.png")
            rule.waitForIdle()
            val deadline = System.currentTimeMillis() + 20_000
            var quiet = 0
            while (System.currentTimeMillis() < deadline && quiet < 5) {
                Thread.sleep(200)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                rule.waitForIdle()
                quiet = if (org.nextcolor.trolleylog.ui.MapTiles.pending.get() == 0) quiet + 1 else 0
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun app(name: String, model: MainViewModel, dark: Boolean = false, tab: String? = null) =
        shot(name, dark, tab) { App(model, Samples.today) }

    @Test fun today() = app("today", model(evening))
    @Test fun todayEmptyDark() = app("today-empty-dark", model(empty = true), dark = true)
    @Test fun history() = app("history", model(), tab = "History")
    @Test fun settingsDark() {
        val model = model()
        rule.setContent { TrolleyLogTheme(dark = true, dynamicColor = false) { App(model, Samples.today) } }
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onRoot().captureRoboImage("build/screenshots/settings-dark.png")
    }

    /** Rides from a log kept before the train length was noted. */
    private val unnoted = listOf(
        Samples.ride("u1", Samples.today.minusDays(40), "08:24", car = "4051B", from = "msdS", to = "sdsS", line = "green"),
        Samples.ride("u2", Samples.today.minusDays(40), "17:45", car = "4030A", from = "sdsS", to = "msdS", line = "green"),
        Samples.ride("u3", Samples.today.minusDays(41), "08:40", car = "4013A", from = "msdS", to = "sdsS", line = "green"),
    ).mapIndexed { i, r ->
        r.copy(trainLength = null, trainPart = if (i == 1) TrainPart.FRONT else TrainPart.REAR, placeInCar = if (i == 1) PlaceInCar.FRONT else PlaceInCar.BACK)
    }

    @Test @Config(qualifiers = "w412dp-h3300dp-xhdpi")
    fun stats() = app("stats", model(*unnoted.toTypedArray()), tab = "Statistics")

    @Test @Config(qualifiers = "w412dp-h3300dp-xhdpi")
    fun statsDark() = app("stats-dark", model(*unnoted.toTypedArray()), dark = true, tab = "Statistics")

    @Test @Config(qualifiers = "w412dp-h1500dp-xhdpi")
    fun editorGoingBack() = app("editor-going-back", model().apply { startRide(Samples.today.atTime(17, 21)); draft = draft!!.copy(ride = draft!!.ride.copy(car = "5009A", position = 1)) })

    @Test @Config(qualifiers = "w412dp-h1500dp-xhdpi")
    fun editorEmptyDark() = app("editor-empty-dark", model(empty = true).apply {
        draft = Draft(Samples.ride("n", Samples.today, "09:05", car = "2031"), true)
    }, dark = true)

    @Test @Config(qualifiers = "w412dp-h1500dp-xhdpi")
    fun editorSharedTrack() = app("editor-shared-track", model().apply {
        draft = Draft(Samples.ride("n", Samples.today, "09:05", "09:21", car = "3004B", length = 2, position = 2, from = "sfdS", to = "oldS",
            brochures = listOf("Rider Alert")), false)
    })

    @Test @Config(qualifiers = "w412dp-h1500dp-xhdpi")
    fun editorTrainLengthNotNoted() = app("editor-train-length-not-noted", model().apply {
        draft = Draft(unnoted[0], false)
    })

    @Test fun mapWhole() = shot("map-whole", map = true) { StationPicker(null, null, {}, {}, {}) }
    @Test fun mapTrip() = shot("map-trip", map = true) { StationPicker("utcS", "oldS", {}, {}, {}) }
    @Test fun mapDowntownDark() = shot("map-downtown-dark", dark = true, map = true) { StationPicker("apzS", "gasS", {}, {}, {}) }

    @Test fun stationList() {
        rule.setContent { TrolleyLogTheme(dynamicColor = false) { StationPicker("utcS", "iriS", {}, {}, {}, listOf("oldS", "utcS", "sdsS")) } }
        rule.onNodeWithContentDescription("Show the list").performClick()
        rule.onNodeWithText("Palm Avenue").performClick()
        rule.onRoot().captureRoboImage("build/screenshots/station-list.png")
    }
}
