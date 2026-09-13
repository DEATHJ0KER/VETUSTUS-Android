package mobi.vxd.vetustus.micro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val VetustusTeal = Color(0xFF39E6C4)
val VetustusBlue = Color(0xFF3EA7E7)
val VetustusViolet = Color(0xFF7C6CFF)
val VetustusInk = Color(0xFF070B12)
val VetustusPanel = Color(0xFF101824)
val VetustusPanelRaised = Color(0xFF172333)
val VetustusText = Color(0xFFE9F2FF)
val VetustusMuted = Color(0xFF9BADBE)
val VetustusDanger = Color(0xFFFF6B78)

private val VetustusColors = darkColorScheme(
    primary = VetustusTeal,
    onPrimary = VetustusInk,
    secondary = VetustusBlue,
    onSecondary = VetustusInk,
    tertiary = VetustusViolet,
    background = VetustusInk,
    onBackground = VetustusText,
    surface = VetustusPanel,
    onSurface = VetustusText,
    surfaceVariant = VetustusPanelRaised,
    onSurfaceVariant = VetustusMuted,
    error = VetustusDanger,
    onError = VetustusInk,
    outline = Color(0xFF385269),
)

@Composable
fun VetustusMicroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VetustusColors,
        content = content,
    )
}
