package org.role.audio_group.feature.onboarding.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class OnboardingPage(val emoji: String, val title: String, val body: String)

private val pages = listOf(
    OnboardingPage(
        emoji = "🎛️",
        title = "Bienvenido a SoundBoard",
        body = "Tu sampler portátil. Organiza audios en grupos y dispáralos al instante."
    ),
    OnboardingPage(
        emoji = "📁",
        title = "Crea un grupo",
        body = "Toca ＋ para crear tu primer grupo.\nEjemplo: \"Percusión\", \"Efectos\", \"Melodías\"."
    ),
    OnboardingPage(
        emoji = "🎵",
        title = "Añade samples",
        body = "Dentro de un grupo toca \"Añadir sample\" para explorar tus archivos de audio."
    ),
    OnboardingPage(
        emoji = "▶️",
        title = "Reproduce",
        body = "Toca ▶ para reproducir. Usa la barra de progreso para saltar a cualquier punto del audio."
    ),
    OnboardingPage(
        emoji = "⚙️",
        title = "Controles extra",
        body = "🔁 Bucle — repite el sample en bucle\n↩️ Restart — vuelve al inicio\n↕️ Mantén pulsado para reordenar"
    )
)

@Composable
fun OnboardingScreen(
    isFirstRun: Boolean,
    onFinish: () -> Unit,
    onMarkSeen: suspend () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    var isFinishing by remember { mutableStateOf(false) }

    fun finish() {
        if (isFinishing) return
        isFinishing = true
        scope.launch {
            if (isFirstRun) onMarkSeen()  // espera a que DataStore confirme la escritura
            onFinish()
        }
    }

    BackHandler(enabled = isFirstRun && !isFinishing) { finish() }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                OnboardingPageContent(page = pages[pageIndex])
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = { finish() },
                    enabled = !isFinishing
                ) {
                    Text("Omitir")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(pages.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Surface(
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(4.dp)
                                    .padding(horizontal = if (isSelected) 8.dp else 4.dp)
                            )
                        }
                    }
                }

                val isLastPage = pagerState.currentPage == pages.size - 1
                Button(
                    onClick = {
                        if (isLastPage) {
                            finish()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    enabled = !isFinishing && !pagerState.isScrollInProgress
                ) {
                    Text(
                        if (isLastPage) {
                            if (isFirstRun) "¡Empezar!" else "Cerrar"
                        } else {
                            "Siguiente →"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = page.emoji, style = MaterialTheme.typography.displayMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
