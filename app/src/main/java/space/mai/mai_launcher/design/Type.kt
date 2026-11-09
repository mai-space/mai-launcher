package space.mai.mai_launcher.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import space.mai.mai_launcher.R

/** Schriften liegen im APK (SIL OFL, siehe licenses/), werden nie geladen. */
object MaiFonts {
    val Condensed = FontFamily(
        Font(R.font.barlow_condensed_medium, FontWeight.Medium),
        Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    )
    val Body = FontFamily(
        Font(R.font.barlow_regular, FontWeight.Normal),
        Font(R.font.barlow_medium, FontWeight.Medium),
        Font(R.font.barlow_semibold, FontWeight.SemiBold),
    )
    val Mono = FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
        Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    )
}

object MaiType {
    /** Große Uhr. */
    val Clock = TextStyle(
        fontFamily = MaiFonts.Condensed, fontWeight = FontWeight.Medium,
        fontSize = 92.sp, lineHeight = 92.sp, letterSpacing = (-1).sp, color = MaiColors.Text,
    )
    /** Titel-Lasche und Abschnittsköpfe. */
    val Tab = TextStyle(
        fontFamily = MaiFonts.Condensed, fontWeight = FontWeight.Bold,
        fontSize = 13.sp, letterSpacing = 2.4.sp, color = MaiColors.Text,
    )
    /** Kleine Versal-Beschriftung (immer Großbuchstaben übergeben). */
    val Label = TextStyle(
        fontFamily = MaiFonts.Condensed, fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp, letterSpacing = 1.6.sp, color = MaiColors.Text2,
    )
    /** Listeneinträge in Versalien (App-Namen). */
    val ListCaps = TextStyle(
        fontFamily = MaiFonts.Condensed, fontWeight = FontWeight.Medium,
        fontSize = 17.sp, letterSpacing = 1.2.sp, color = MaiColors.Text,
    )
    val Letter = TextStyle(
        fontFamily = MaiFonts.Condensed, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, color = MaiColors.Ion,
    )
    val Title = TextStyle(
        fontFamily = MaiFonts.Body, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, color = MaiColors.Text,
    )
    val Body = TextStyle(
        fontFamily = MaiFonts.Body, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, color = MaiColors.Text2,
    )
    /** Zahlen: Uhrzeit, Prozent, Dauer – Monospace, damit nichts springt. */
    val Mono = TextStyle(
        fontFamily = MaiFonts.Mono, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, letterSpacing = 1.sp, color = MaiColors.Text2,
    )
    val Token = TextStyle(
        fontFamily = MaiFonts.Condensed, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, color = MaiColors.Ion,
    )
}
