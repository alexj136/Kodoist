package net.alexjeffery.kodoist

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The two icons the app needs, using the standard Material Design path data (Apache 2.0).
 * Defined here so the app doesn't need the large material-icons-extended library.
 */
object AppIcons {
    /** Circular arrow ("refresh"). */
    val Refresh: ImageVector = icon(
        "Refresh",
        "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8" +
            "c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4" +
            "c-3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z",
    )

    /** Arrow leaving through a door ("logout"). */
    val Logout: ImageVector = icon(
        "Logout",
        "M17,7l-1.41,1.41L18.17,11H8v2h10.17l-2.58,2.58L17,17l5,-5z" +
            "M4,5h8V3H4c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h8v-2H4V5z",
    )

    private fun icon(name: String, path: String) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}
