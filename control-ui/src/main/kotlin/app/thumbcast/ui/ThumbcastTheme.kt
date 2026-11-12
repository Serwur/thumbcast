package app.thumbcast.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Paleta Thumbcast (docs/ui-audit.md, D1): tylko ciemna, bez dynamic color. Te same wartości są w
 * `app/src/main/res/values/thumbcast_colors.xml` (nazwy w komentarzach); przy zmianie poprawić oba miejsca.
 *
 * Kolor nigdy nie jest jedynym sygnałem: stan zawsze idzie z ikoną albo tekstem.
 */
object ThumbcastColors {
    // Ekrany
    /** `thumbcast_bg`: tło ekranu. */
    val Bg: Color = Color(0xFF0F1115)
    /** `thumbcast_surface`: karty, panele. */
    val Surface: Color = Color(0xFF181B22)
    /** `thumbcast_surface_raised`: dialogi, menu, karta na karcie. */
    val SurfaceRaised: Color = Color(0xFF222632)
    /** `thumbcast_outline`: obrys dekoracyjny i linie podziału na ekranach. */
    val Outline: Color = Color(0xFF2E3340)
    /** `thumbcast_outline_strong`: obrys, który musi być widoczny (pola tekstowe); ok. 4:1 z tłem. Poza tabelą D1. */
    val OutlineStrong: Color = Color(0xFF737A8C)
    /** `thumbcast_text`. */
    val Text: Color = Color(0xFFE8EAF0)
    /** `thumbcast_text_secondary`. */
    val TextSecondary: Color = Color(0xFFA3A9B8)
    /** `thumbcast_accent`. */
    val Accent: Color = Color(0xFF5B8CFF)
    /** `thumbcast_on_accent`. */
    val OnAccent: Color = Color(0xFFFFFFFF)
    /** `thumbcast_success`: sukces, PC dostępny. */
    val Success: Color = Color(0xFF3DDC97)
    /** `thumbcast_warning`. */
    val Warning: Color = Color(0xFFFFB84D)
    /** `thumbcast_error`. */
    val Error: Color = Color(0xFFFF6B6B)
    /** `thumbcast_error_container`: wypełnienie przycisku akcji nieodwracalnej. */
    val ErrorContainer: Color = Color(0xFF93000A)

    // Tła plakietek (20 % koloru stanu), jak `thumbcast_*_dim`.
    val SuccessDim: Color = Color(0x333DDC97)
    val WarningDim: Color = Color(0x33FFB84D)
    val ErrorDim: Color = Color(0x33FF6B6B)
    val AccentDim: Color = Color(0x335B8CFF)

    // Nad wideo
    /** `thumbcast_scrim`: przyciemnienie wideo pod menu. */
    val Scrim: Color = Color(0x99000000)
    /** `thumbcast_menu_surface`: panel menu nad wideo. */
    val MenuSurface: Color = Color(0xF2181B22)
    /** `thumbcast_divider`: linie nad wideo. */
    val Divider: Color = Color(0x33FFFFFF)
}

/** Odstępy i rozmiary jak w `thumbcast_dimens.xml`. */
object ThumbcastDimens {
    val SpaceXSmall = 4.dp
    val SpaceSmall = 8.dp
    val SpaceMedium = 16.dp
    val SpaceLarge = 24.dp
    val TouchTarget = 48.dp
    val IconSize = 24.dp
    val CornerSmall = 12.dp
    val CornerMedium = 16.dp
    val CornerLarge = 28.dp
}

/**
 * Schemat Material 3 z palety D1. Role spoza tabeli D1 (kontenery) są wyprowadzone z niej:
 * `primaryContainer` i `tertiaryContainer` = 20 % akcentu / sukcesu na powierzchni.
 */
val ThumbcastColorScheme: ColorScheme = darkColorScheme(
    primary = ThumbcastColors.Accent,
    onPrimary = ThumbcastColors.OnAccent,
    primaryContainer = Color(0xFF25324E),
    onPrimaryContainer = ThumbcastColors.Text,
    inversePrimary = Color(0xFF2F5BD3),
    secondary = ThumbcastColors.Accent,
    onSecondary = ThumbcastColors.OnAccent,
    secondaryContainer = ThumbcastColors.SurfaceRaised,
    onSecondaryContainer = ThumbcastColors.Text,
    tertiary = ThumbcastColors.Success,
    onTertiary = ThumbcastColors.Bg,
    tertiaryContainer = Color(0xFF1F4239),
    onTertiaryContainer = ThumbcastColors.Text,
    background = ThumbcastColors.Bg,
    onBackground = ThumbcastColors.Text,
    surface = ThumbcastColors.Surface,
    onSurface = ThumbcastColors.Text,
    surfaceVariant = ThumbcastColors.SurfaceRaised,
    onSurfaceVariant = ThumbcastColors.TextSecondary,
    // Podniesienie tonalne rozjaśnia w stronę powierzchni podniesionej, bez niebieskiego odcienia.
    surfaceTint = ThumbcastColors.SurfaceRaised,
    inverseSurface = ThumbcastColors.Text,
    inverseOnSurface = ThumbcastColors.Surface,
    error = ThumbcastColors.Error,
    onError = Color(0xFF690005),
    errorContainer = ThumbcastColors.ErrorContainer,
    onErrorContainer = Color(0xFFFFDAD6),
    outline = ThumbcastColors.OutlineStrong,
    outlineVariant = ThumbcastColors.Outline,
    // Komponenty M3 same nakładają przezroczystość na scrim; nad wideo używaj ThumbcastColors.Scrim.
    scrim = Color.Black,
    surfaceBright = ThumbcastColors.Outline,
    surfaceDim = ThumbcastColors.Bg,
    surfaceContainerLowest = ThumbcastColors.Bg,
    surfaceContainerLow = ThumbcastColors.Surface,
    surfaceContainer = ThumbcastColors.Surface,
    surfaceContainerHigh = ThumbcastColors.SurfaceRaised,
    surfaceContainerHighest = ThumbcastColors.SurfaceRaised,
)

/** Rogi: 12 dp (pola), 16 dp (karty), 28 dp (dialogi, arkusze, panele); przyciski M3 są pigułkami. */
val ThumbcastShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(ThumbcastDimens.CornerSmall),
    medium = RoundedCornerShape(ThumbcastDimens.CornerMedium),
    large = RoundedCornerShape(ThumbcastDimens.CornerLarge),
    extraLarge = RoundedCornerShape(ThumbcastDimens.CornerLarge),
)

/**
 * Typografia: skala Material 3 bez zmian (bodyLarge 16 sp, bodyMedium 14 sp, titleLarge 22 sp, headlineSmall 24 sp),
 * czcionka systemowa. Te same rozmiary są w `thumbcast_dimens.xml` (`thumbcast_text_*`).
 */
val ThumbcastTypography: Typography = Typography()

/** Motyw wszystkich ekranów Compose w Thumbcast (kreator, poradnik, edytor układu). */
@Composable
fun ThumbcastTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ThumbcastColorScheme,
        shapes = ThumbcastShapes,
        typography = ThumbcastTypography,
        content = content,
    )
}
