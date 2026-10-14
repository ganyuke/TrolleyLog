package org.nextcolor.trolleylog.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Trolley red. Colors are grown from it when the phone has no wallpaper colors to offer. */
private val Seed = Color(0xFFD2232A)

/**
 * Material 3 Expressive theme. On Android 12 and later the colors follow the
 * wallpaper like the rest of the system, unless [dynamicColor] is off.
 */
@Composable
fun TrolleyLogTheme(
    dark: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        rememberDynamicColorScheme(seedColor = Seed, isDark = dark, style = PaletteStyle.TonalSpot)
    }
    MaterialExpressiveTheme(colorScheme = scheme, content = content)
}

private val clock = DateTimeFormatter.ofPattern("h:mm a")

fun LocalTime.clock(): String = format(clock)

fun ordinal(n: Int): String = when (n) {
    1 -> "1st"
    2 -> "2nd"
    3 -> "3rd"
    else -> "${n}th"
}

fun minutesText(minutes: Long): String =
    when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
