package com.example.invcalc

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** 畫面風格：1 個預設 + 5 種可選（含深色）。選擇會存起來，整個 App 都套用。 */
object AppTheme {
    class Option(val name: String, val note: String, val scheme: ColorScheme)

    val options: List<Option> = listOf(
        Option("經典", "預設的紫色風格", lightColorScheme()),
        Option(
            "深夜", "深色背景，夜間看盤不刺眼",
            darkColorScheme(
                primary = Color(0xFFB4C5FF), onPrimary = Color(0xFF002A78),
                primaryContainer = Color(0xFF284190), onPrimaryContainer = Color(0xFFDBE1FF),
                secondary = Color(0xFFC0C6DC), onSecondary = Color(0xFF2A3042),
                secondaryContainer = Color(0xFF404659), onSecondaryContainer = Color(0xFFDCE2F9),
                tertiary = Color(0xFFE2BBDB),
                background = Color(0xFF121318), onBackground = Color(0xFFE3E2E9),
                surface = Color(0xFF121318), onSurface = Color(0xFFE3E2E9),
                surfaceVariant = Color(0xFF45464F), onSurfaceVariant = Color(0xFFC6C6D0),
                outline = Color(0xFF90909A), outlineVariant = Color(0xFF45464F),
                surfaceContainerHighest = Color(0xFF343439), surfaceContainerHigh = Color(0xFF292A2F),
                surfaceContainer = Color(0xFF1F1F25)
            )
        ),
        Option(
            "海洋", "清爽的藍色系",
            lightColorScheme(
                primary = Color(0xFF0061A4), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFD1E4FF), onPrimaryContainer = Color(0xFF001D36),
                secondary = Color(0xFF535F70), secondaryContainer = Color(0xFFD7E3F8), onSecondaryContainer = Color(0xFF101C2B),
                tertiary = Color(0xFF6B5778),
                background = Color(0xFFF6FAFF), onBackground = Color(0xFF191C1E),
                surface = Color(0xFFF6FAFF), onSurface = Color(0xFF191C1E),
                surfaceVariant = Color(0xFFDFE2EB), onSurfaceVariant = Color(0xFF42474E),
                outline = Color(0xFF73777F), outlineVariant = Color(0xFFC3C7CF),
                surfaceContainerHighest = Color(0xFFDCE8F4), surfaceContainerHigh = Color(0xFFE3EDF7),
                surfaceContainer = Color(0xFFE9F1F9)
            )
        ),
        Option(
            "森林", "與 App 圖示同色的綠色系",
            lightColorScheme(
                primary = Color(0xFF1B7F5C), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFB7F0D6), onPrimaryContainer = Color(0xFF002116),
                secondary = Color(0xFF4C6358), secondaryContainer = Color(0xFFCEE9DB), onSecondaryContainer = Color(0xFF092016),
                tertiary = Color(0xFF3D6373),
                background = Color(0xFFF5FBF6), onBackground = Color(0xFF171D19),
                surface = Color(0xFFF5FBF6), onSurface = Color(0xFF171D19),
                surfaceVariant = Color(0xFFDBE5DD), onSurfaceVariant = Color(0xFF404943),
                outline = Color(0xFF707973), outlineVariant = Color(0xFFBFC9C2),
                surfaceContainerHighest = Color(0xFFDCE8E0), surfaceContainerHigh = Color(0xFFE3EEE6),
                surfaceContainer = Color(0xFFE9F2EB)
            )
        ),
        Option(
            "暖陽", "溫暖的橘色系",
            lightColorScheme(
                primary = Color(0xFF9A4600), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFFFDBC6), onPrimaryContainer = Color(0xFF331200),
                secondary = Color(0xFF765846), secondaryContainer = Color(0xFFFFDCC7), onSecondaryContainer = Color(0xFF2B1708),
                tertiary = Color(0xFF646032),
                background = Color(0xFFFFF8F5), onBackground = Color(0xFF201A17),
                surface = Color(0xFFFFF8F5), onSurface = Color(0xFF201A17),
                surfaceVariant = Color(0xFFF4DED3), onSurfaceVariant = Color(0xFF52443B),
                outline = Color(0xFF85746A), outlineVariant = Color(0xFFD7C2B8),
                surfaceContainerHighest = Color(0xFFF3E3DA), surfaceContainerHigh = Color(0xFFF7E9E1),
                surfaceContainer = Color(0xFFFAEEE6)
            )
        ),
        Option(
            "玫瑰", "柔和的粉紫色系",
            lightColorScheme(
                primary = Color(0xFF9C2F6F), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFFFD8E8), onPrimaryContainer = Color(0xFF3D0026),
                secondary = Color(0xFF74565F), secondaryContainer = Color(0xFFFFD9E3), onSecondaryContainer = Color(0xFF2B151D),
                tertiary = Color(0xFF7C5635),
                background = Color(0xFFFFF8F8), onBackground = Color(0xFF201A1B),
                surface = Color(0xFFFFF8F8), onSurface = Color(0xFF201A1B),
                surfaceVariant = Color(0xFFF2DDE2), onSurfaceVariant = Color(0xFF514347),
                outline = Color(0xFF837377), outlineVariant = Color(0xFFD5C2C6),
                surfaceContainerHighest = Color(0xFFF1E1E6), surfaceContainerHigh = Color(0xFFF6E7EB),
                surfaceContainer = Color(0xFFF9ECEF)
            )
        )
    )

    var index by mutableIntStateOf(0)

    fun scheme(): ColorScheme = options[index.coerceIn(0, options.lastIndex)].scheme
}
