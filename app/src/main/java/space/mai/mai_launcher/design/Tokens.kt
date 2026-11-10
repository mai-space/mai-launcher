package space.mai.mai_launcher.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Farb-Tokens aus dem Konzept (project-docs/KONZEPT.md, Abschnitt 1). */
object MaiColors {
    val Void = Color(0xFF04070A)
    val VoidTop = Color(0xFF071018)
    val Panel = Color(0xFF0A1218)
    val Panel2 = Color(0xFF0E1A22)
    /** Konturen und Trenner; bei hohem Kontrast heller. */
    val Line: Color get() = if (ComfortState.current.highContrast) Color(0xFF4E6A77) else Color(0xFF1C2C35)
    val Text = Color(0xFFE6F1F5)
    /** Zweite Textstufe; bei hohem Kontrast heller. */
    val Text2: Color get() = if (ComfortState.current.highContrast) Color(0xFFC2D5DD) else Color(0xFF8AA4B0)
    val Text3: Color get() = if (ComfortState.current.highContrast) Color(0xFF8AA4B0) else Color(0xFF4A606B)

    val Ion = Color(0xFF4CC9F0)
    val IonDim = Color(0xFF1E5A6E)
    val Signal = Color(0xFF5BE49B)
    val Amber = Color(0xFFFFB547)
    val Alert = Color(0xFFFF4D5E)
}

/** Kontextfarbe = wo bin ich. Je Bildschirm genau eine. */
@Immutable
data class Accent(val main: Color, val dim: Color)

object Accents {
    val Ion = Accent(MaiColors.Ion, MaiColors.IonDim)
    val Signal = Accent(MaiColors.Signal, Color(0xFF1F5A3D))
    val Amber = Accent(MaiColors.Amber, Color(0xFF6B4A1A))
    val Violet = Accent(Color(0xFFB69CFF), Color(0xFF473A6E))
    val White = Accent(Color(0xFFE6F1F5), Color(0xFF4A5A62))
}

val LocalAccent = staticCompositionLocalOf { Accents.Ion }

/** Maße: Fasen, Abstände, Mindest-Tippfläche. */
object MaiDims {
    val FrameChamfer = 22.dp
    val PanelChamfer = 10.dp
    val ButtonChamfer = 7.dp
    val Gutter = 16.dp
    val MinTouch = 48.dp
    /** Listenzeilen: 48 dp, in der großen Ansicht 56 dp. */
    val ListRow: Dp get() = if (ComfortState.current.large) 56.dp else 48.dp
    val RailWidth = 52.dp
}
