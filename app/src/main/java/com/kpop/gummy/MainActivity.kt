package com.kpop.gummy

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.launch

private val Pink = Color(0xFFFF48C8)
private val Mint = Color(0xFF70FFE2)
private val Background = Color(0xFF100D22)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Pink,
                    secondary = Mint,
                    background = Background,
                    surface = Color(0xFF211A35)
                )
            ) {
                RadioScreen()
            }
        }
    }
}

@Composable
private fun rememberRadioController(): MediaController? {
    val context = LocalContext.current.applicationContext
    var controller by remember {
        mutableStateOf<MediaController?>(null)
    }

    DisposableEffect(context) {
        var disposed = false

        val token = SessionToken(
            context,
            ComponentName(context, RadioService::class.java)
        )

        val future = MediaController.Builder(context, token)
            .buildAsync()

        future.addListener(
            {
                if (!disposed) {
                    controller = runCatching { future.get() }.getOrNull()
                }
            },
            context.mainExecutor
        )

        onDispose {
            disposed = true
            MediaController.releaseFuture(future)
        }
    }

    return controller
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RadioScreen() {
    val controller = rememberRadioController()

    var wantsPlayback by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.7f) }
    var stationName by remember { mutableStateOf(stations.first().name) }
    var error by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    DisposableEffect(controller) {
        val player = controller

        if (player == null) {
            onDispose {}
        } else {
            fun sync() {
                wantsPlayback = player.playWhenReady
                buffering = player.playbackState == Player.STATE_BUFFERING
                volume = player.volume
                error = player.playerError != null

                stationName = stations.getOrNull(
                    player.currentMediaItemIndex
                )?.name ?: "K-Pop Radio"
            }

            val listener = object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events
                ) {
                    sync()
                }
            }

            player.addListener(listener)
            sync()

            onDispose {
                player.removeListener(listener)
            }
        }
    }

    fun togglePlayback() {
        val player = controller ?: return

        if (player.playWhenReady && player.playerError == null) {
            player.pause()
        } else {
            if (
                player.playerError != null ||
                player.playbackState == Player.STATE_IDLE
            ) {
                player.prepare()
            }
            player.play()
        }
    }

    fun switchStation(direction: Int) {
        val player = controller ?: return
        val count = player.mediaItemCount
        if (count == 0) return

        val current = player.currentMediaItemIndex.coerceAtLeast(0)
        val next = (current + direction + count) % count

        player.seekToDefaultPosition(next)
        player.prepare()
        player.play()
    }

    fun changeVolume(delta: Float) {
        val player = controller ?: return
        player.volume = (player.volume + delta).coerceIn(0f, 1f)
    }

    fun showHint(message: String) {
        scope.launch {
            snackbar.showSnackbar(message)
        }
    }

    val ticker = when {
        controller == null -> "Подключение к плееру…"
        error -> "Связь потеряна… • 연결이 끊겼어요…"
        buffering -> "$stationName • Буферизация…"
        else -> stationName
    }

    Scaffold(
        containerColor = Background,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "K-POP",
                color = Mint,
                fontWeight = FontWeight.Black,
                fontSize = 34.sp,
                letterSpacing = 7.sp
            )

            Text(
                text = "GUMMY BEAR RADIO",
                color = Pink,
                fontSize = 12.sp,
                letterSpacing = 3.sp
            )

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFF211A35),
                        MaterialTheme.shapes.large
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ticker,
                    color = Mint,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }

            JellyBear(
                active = wantsPlayback && !error,
                onPart = { part ->
                    when (part) {
                        "leftEar" -> changeVolume(-0.05f)
                        "rightEar" -> changeVolume(0.05f)
                        "leftArm" -> switchStation(-1)
                        "rightArm" -> switchStation(1)
                        "belly" -> togglePlayback()
                        "leftFoot" -> showHint(
                            "Локальные треки — в следующей версии"
                        )
                        "rightFoot" -> showHint(
                            "Избранное — в следующей версии"
                        )
                    }
                },
                modifier = Modifier
                    .padding(top = 8.dp)
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .aspectRatio(0.85f)
            )

            Text(
                text = "Громкость ${(volume * 100).toInt()}%",
                color = Mint
            )

            Slider(
                value = volume,
                onValueChange = { controller?.volume = it },
                enabled = controller != null,
                modifier = Modifier.widthIn(max = 340.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { switchStation(-1) },
                    enabled = controller != null
                ) {
                    Text("Назад")
                }

                Button(
                    onClick = { togglePlayback() },
                    enabled = controller != null
                ) {
                    Text(
                        when {
                            error -> "Повторить"
                            wantsPlayback -> "Пауза"
                            else -> "Играть"
                        }
                    )
                }

                OutlinedButton(
                    onClick = { switchStation(1) },
                    enabled = controller != null
                ) {
                    Text("Далее")
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Ушки — громкость\n" +
                    "Лапки — станции • Животик — Play / Pause",
                color = Color(0xFFB5A7CB),
                textAlign = TextAlign.Center,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "MVP 0.1 • Нужен интернет",
                color = Color(0xFF80728F),
                fontSize = 11.sp
            )
        }
    }
}

private data class BearPart(
    val id: String,
    val centerX: Float,
    val centerY: Float,
    val radiusX: Float,
    val radiusY: Float
) {
    fun contains(x: Float, y: Float): Boolean {
        val dx = (x - centerX) / radiusX
        val dy = (y - centerY) / radiusY
        return dx * dx + dy * dy <= 1f
    }
}

private val bearParts = listOf(
    BearPart("leftEar", 29f, 23f, 13f, 13f),
    BearPart("rightEar", 71f, 23f, 13f, 13f),
    BearPart("leftArm", 19f, 62f, 13f, 19f),
    BearPart("rightArm", 81f, 62f, 13f, 19f),
    BearPart("leftFoot", 32f, 96f, 15f, 16f),
    BearPart("rightFoot", 68f, 96f, 15f, 16f),
    BearPart("belly", 50f, 70f, 30f, 33f)
)

@Composable
private fun JellyBear(
    active: Boolean,
    onPart: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val squash = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val currentOnPart by rememberUpdatedState(onPart)

    Canvas(
        modifier = modifier
            .semantics {
                contentDescription =
                    "Мармеладный мишка. Управление также доступно кнопками ниже."
            }
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    val unit = minOf(
                        size.width / 100f,
                        size.height / 120f
                    )

                    val left = (size.width - unit * 100f) / 2f
                    val top = (size.height - unit * 120f) / 2f

                    val cx = size.width / 2f
                    val cy = size.height / 2f

                    val scaleX = 1f + (1f - squash.value) * 0.4f
                    val scaleY = squash.value

                    val localX =
                        ((position.x - cx) / scaleX + cx - left) / unit
                    val localY =
                        ((position.y - cy) / scaleY + cy - top) / unit

                    val part = bearParts.firstOrNull {
                        it.contains(localX, localY)
                    } ?: return@detectTapGestures

                    scope.launch {
                        squash.snapTo(0.9f)
                        squash.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioHighBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    }

                    currentOnPart(part.id)
                }
            }
    ) {
        val unit = minOf(size.width / 100f, size.height / 120f)
        val left = (size.width - unit * 100f) / 2f
        val top = (size.height - unit * 120f) / 2f

        fun point(x: Float, y: Float) =
            Offset(left + x * unit, top + y * unit)

        val bodyBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFA1E4),
                Color(0xFFF348BD),
                Color(0xFF8D35C9)
            ),
            start = point(20f, 20f),
            end = point(80f, 108f)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Pink.copy(alpha = if (active) 0.28f else 0.13f),
                    Color.Transparent
                ),
                center = point(50f, 64f),
                radius = 58f * unit
            ),
            radius = 58f * unit,
            center = point(50f, 64f)
        )

        scale(
            scaleX = 1f + (1f - squash.value) * 0.4f,
            scaleY = squash.value,
            pivot = center
        ) {
            // Ушки, лапки и ножки.
            bearParts.filter { it.id != "belly" }.forEach { part ->
                drawOval(
                    brush = bodyBrush,
                    topLeft = point(
                        part.centerX - part.radiusX,
                        part.centerY - part.radiusY
                    ),
                    size = Size(
                        part.radiusX * 2f * unit,
                        part.radiusY * 2f * unit
                    )
                )
            }

            // Корпус и голова.
            drawOval(
                brush = bodyBrush,
                topLeft = point(20f, 38f),
                size = Size(60f * unit, 65f * unit)
            )

            drawOval(
                brush = bodyBrush,
                topLeft = point(21f, 20f),
                size = Size(58f * unit, 44f * unit)
            )

            // Блики.
            drawOval(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = point(28f, 27f),
                size = Size(10f * unit, 19f * unit)
            )

            drawOval(
                color = Color.White.copy(alpha = 0.12f),
                topLeft = point(31f, 56f),
                size = Size(38f * unit, 35f * unit)
            )

            // Глаза.
            drawCircle(
                color = Background,
                radius = 2.3f * unit,
                center = point(40f, 39f)
            )
            drawCircle(
                color = Background,
                radius = 2.3f * unit,
                center = point(60f, 39f)
            )

            // Носик.
            drawOval(
                color = Background,
                topLeft = point(46.5f, 44f),
                size = Size(7f * unit, 5f * unit)
            )

            // Румянец.
            drawCircle(
                color = Color(0xFFFFD0F1).copy(alpha = 0.6f),
                radius = 4f * unit,
                center = point(32f, 46f)
            )
            drawCircle(
                color = Color(0xFFFFD0F1).copy(alpha = 0.6f),
                radius = 4f * unit,
                center = point(68f, 46f)
            )

            // Play / Pause на животике.
            if (active) {
                listOf(45f, 55f).forEach { x ->
                    drawLine(
                        color = Color.White,
                        start = point(x, 67f),
                        end = point(x, 81f),
                        strokeWidth = 4f * unit,
                        cap = StrokeCap.Round
                    )
                }
            } else {
                val playPath = androidx.compose.ui.graphics.Path().apply {
                    val a = point(44f, 65f)
                    val b = point(60f, 74f)
                    val c = point(44f, 83f)
                    moveTo(a.x, a.y)
                    lineTo(b.x, b.y)
                    lineTo(c.x, c.y)
                    close()
                }
                drawPath(playPath, Color.White)
            }
        }
    }
}
