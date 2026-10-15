package io.github.iammxrn.strangershaders.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.iammxrn.strangershaders.teaching.captureAwareNavigationBarsPadding
import io.github.iammxrn.strangershaders.teaching.captureAwareStatusBarsPadding

val LocalProductBack = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun DemoCard(
  modifier: Modifier = Modifier,
  title: String = "Shader playground",
  chromeModifier: Modifier = Modifier,
  isUpsideDownActive: Boolean = false,
  onUpsideDownClick: () -> Unit = {},
  background: @Composable BoxScope.() -> Unit = {},
) {
  var count by rememberSaveable { mutableIntStateOf(0) }
  val back = LocalProductBack.current
  Box(modifier.fillMaxSize()) {
    background()
    // UI and video use the same full-screen origin for the horizontal glitch bands.
    Column(
      Modifier.fillMaxSize().then(chromeModifier)
        .captureAwareStatusBarsPadding().captureAwareNavigationBarsPadding().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = back) { Text("Back") }
        Spacer(Modifier.weight(1f))
        Button(onClick = onUpsideDownClick) { Text(if (isUpsideDownActive) "Effect ON" else "Effect OFF") }
      }
      Text(title, fontSize = 28.sp, color = Color.White,
        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).padding(16.dp))
      Spacer(Modifier.weight(1f))
      Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.7f)).padding(20.dp)) {
        Text("Same effect. Different video paths.", color = Color.White, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        Text("Tap the video to pause, then toggle the effect.", color = Color.White, fontSize = 16.sp)
        Button(onClick = { count++ }) { Text("UI updates: $count") }
      }
    }
  }
}
