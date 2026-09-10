package com.kpop.gummy

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------- Цветовая палитра из SVG ----------
private val DeepBg = Color(0xFF03040B)
private val BgTop = Color(0xFF110A29)
private val BgMid = Color(0xFF070C22)
private val BgBot = Color(0xFF180923)
private val PinkHaze = Color(0xFFFF19BA)
private val BlueHaze = Color(0xFF00CFFF)
private val Mint = Color(0xFF9EFFEF)
private val NeonPink = Color(0xFFFF91E4)
private val GlassPink = Color(0xFFFF8DE0)
private val GlassMid = Color(0xFFBA54DA)
private val GlassCyan = Color(0xFF66DBF0)
private val PanelBg = Color(0xFF291B3E)
private val ControlBg = Color(0xFF161327)

// ---------- Touch-зоны ----------
private enum class HitZone { LEFT_EAR, RIGHT_EAR, LEFT_PAW, RIGHT_PAW, BELLY, LEFT_FOOT, RIGHT_FOOT, NONE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GummyRadioApp() }
    }
}

@Composable
private fun rememberRadioController(): MediaController? {
    val context = LocalContext.current.applicationContext
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        var disposed = false
        val token = SessionToken(context, ComponentName(context, RadioService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            if (!disposed) controller = runCatching { future.get() }.getOrNull()
        }, context.mainExecutor)
        onDispose { disposed = true; MediaController.releaseFuture(future) }
    }
    return controller
}

@Composable
fun GummyRadioApp() {
    val controller = rememberRadioController()
    var wantsPlayback by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.7f) }
    var stationName by remember { mutableStateOf("LISTEN.moe K-POP") }
    var buffering by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        val player = controller ?: return@DisposableEffect onDispose {}
        fun sync() {
            wantsPlayback = player.playWhenReady
            buffering = player.playbackState == Player.STATE_BUFFERING
            volume = player.volume
            error = player.playerError != null
            stationName = stations.getOrNull(player.currentMediaItemIndex)?.name ?: "K-Pop Radio"
        }
        val listener = object : Player.Listener { override fun onEvents(p: Player, e: Player.Events) { sync() } }
        player.addListener(listener); sync()
        onDispose { player.removeListener(listener) }
    }

    fun togglePlayback() {
        val p = controller ?: return
        if (p.playWhenReady && p.playerError == null) p.pause()
        else { if (p.playerError != null || p.playbackState == Player.STATE_IDLE) p.prepare(); p.play() }
    }
    fun switchStation(dir: Int) {
        val p = controller ?: return
        val cnt = p.mediaItemCount; if (cnt == 0) return
        val cur = p.currentMediaItemIndex.coerceAtLeast(0)
        p.seekToDefaultPosition((cur + dir + cnt) % cnt)
        p.prepare(); p.play()
    }
    fun changeVolume(delta: Float) { controller?.volume = (controller!!.volume + delta).coerceIn(0f, 1f) }

    // Анимации
    val beatAnim = rememberInfiniteTransition(label = "beat")
    val pulse by beatAnim.animateFloat(1f, if (wantsPlayback) 1.03f else 1f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val earWiggle by beatAnim.animateFloat(-2f, 2f,
        infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ear")
    var tapPart by remember { mutableStateOf<HitZone?>(null) }
    var tapFlash by remember { mutableFloatStateOf(0f) }
    val tapScale by animateFloatAsState(if (tapFlash > 0f) 0.92f else 1f,
        spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow), label = "tap")
    LaunchedEffect(tapFlash) { if (tapFlash > 0f) { delay(250); tapFlash = 0f } }

    val statusText = when {
        error -> "Связь потеряна…"
        buffering -> "Буферизация…"
        else -> "$stationName • K-POP"
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BgTop, BgMid, BgBot)))) {
        Column(Modifier.fillMaxSize()) {
            // ШАПКА
            Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("K-POP", color = NeonPink, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                    Text("GUMMY RADIO", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
                Surface(color = Color(0xFF193131), shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF62CBB9).copy(alpha = 0.5f))) {
                    Text(" ON AIR ", Modifier.padding(horizontal = 14.dp, vertical = 6.dp), color = Mint, fontSize = 12.sp, letterSpacing = 1.sp)
                }
            }
            // Now Playing
            Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp), color = Color(0xFF111124),
                shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCE60C1))) {
                Row(Modifier.padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("NOW PLAYING", color = Color(0xFFFF89D8), fontSize = 10.sp, letterSpacing = 1.5.sp)
                    Spacer(Modifier.width(12.dp))
                    Canvas(Modifier.size(6.dp)) { drawCircle(Mint, 3.dp.toPx()) }
                    Spacer(Modifier.width(10.dp))
                    Text(statusText, color = Mint, fontSize = 14.sp, maxLines = 1)
                }
            }

            // МИШКА
            Box(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                detectTapGestures { offset ->
                    val zone = hitTest(offset, size.width.toFloat(), size.height.toFloat())
                    tapPart = zone; tapFlash = 1f
                    when (zone) {
                        HitZone.LEFT_EAR -> changeVolume(-0.05f)
                        HitZone.RIGHT_EAR -> changeVolume(0.05f)
                        HitZone.LEFT_PAW -> switchStation(-1)
                        HitZone.RIGHT_PAW -> switchStation(1)
                        HitZone.BELLY -> togglePlayback()
                        HitZone.LEFT_FOOT, HitZone.RIGHT_FOOT, HitZone.NONE -> {}
                    }
                }
            }) {
                Canvas(Modifier.fillMaxSize()) { drawBear(wantsPlayback, tapPart, pulse * tapScale, earWiggle) }
            }

            // ПОДСКАЗКА
            Text("Коснись мишки — включи своё настроение", Modifier.fillMaxWidth().padding(bottom = 4.dp),
                textAlign = TextAlign.Center, color = Color(0xFFA99ABB), fontSize = 11.sp, letterSpacing = 0.5.sp)

            // НИЖНЯЯ ПАНЕЛЬ
            Surface(Modifier.fillMaxWidth().padding(8.dp).padding(bottom = 12.dp), color = ControlBg,
                shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF49324F))) {
                Row(Modifier.padding(12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Радио / Мои треки / Избранное
                    TextButton(onClick = {}) { Text("Радио", color = Mint, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.weight(0.3f))
                    TextButton(onClick = {}) { Text("Мои треки", color = Color(0xFFA99ABB)) }
                    Spacer(Modifier.weight(0.15f))
                    TextButton(onClick = {}) { Text("Избранное", color = Color(0xFFA99ABB)) }
                    // Volume
                    Spacer(Modifier.weight(0.3f))
                    Canvas(Modifier.size(22.dp)) {
                        drawCircle(Mint, 9.dp.toPx(), center = center)
                        drawCircle(Color(0xFF161327), 7.dp.toPx(), center = center)
                        drawCircle(Mint.copy(alpha = 0.6f), 5.dp.toPx(), center = center)
                    }
                    Text(" ${(volume * 100).toInt()}%", color = Mint, fontSize = 13.sp, modifier = Modifier.width(38.dp))
                    // Prev / Play / Next
                    IconButton(onClick = { switchStation(-1) }, modifier = Modifier.size(36.dp)) {
                        Canvas(Modifier.fillMaxSize()) { prevIcon(size) }
                    }
                    IconButton(onClick = { togglePlayback() }, modifier = Modifier.size(40.dp)) {
                        Canvas(Modifier.fillMaxSize()) { playPauseIcon(size, wantsPlayback) }
                    }
                    IconButton(onClick = { switchStation(1) }, modifier = Modifier.size(36.dp)) {
                        Canvas(Modifier.fillMaxSize()) { nextIcon(size) }
                    }
                }
            }
        }
    }
}

// ---------- HIT-TEST ----------
private fun hitTest(offset: Offset, w: Float, h: Float): HitZone {
    val cx = w / 2f; val s = minOf(w * 0.82f, h * 0.62f); val cy = h * 0.44f
    fun dx(f: Float) = (offset.x - cx) / s
    fun dy(f: Float) = (offset.y - cy) / s
    fun inOval(dx: Float, dy: Float, rx: Float, ry: Float) = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) <= 1f
    return when {
        inOval(dx(-0.31f), dy(-0.62f), 0.17f, 0.17f) -> HitZone.LEFT_EAR
        inOval(dx(0.31f), dy(-0.62f), 0.17f, 0.17f) -> HitZone.RIGHT_EAR
        inOval(dx(-0.44f), dy(-0.04f), 0.14f, 0.18f) -> HitZone.LEFT_PAW
        inOval(dx(0.44f), dy(-0.04f), 0.14f, 0.18f) -> HitZone.RIGHT_PAW
        inOval(dx(0f), dy(0.08f), 0.22f, 0.24f) -> HitZone.BELLY
        inOval(dx(-0.2f), dy(0.4f), 0.13f, 0.11f) -> HitZone.LEFT_FOOT
        inOval(dx(0.2f), dy(0.4f), 0.13f, 0.11f) -> HitZone.RIGHT_FOOT
        else -> HitZone.NONE
    }
}

// ---------- GUMY BEAR (Canvas) — SVG-стиль ----------
private fun DrawScope.drawBear(playing: Boolean, tapPart: HitZone?, scale: Float, earAngle: Float) {
    val w = size.width; val h = size.height
    val s = minOf(w * 0.82f, h * 0.62f)
    val cx = w / 2f; val cy = h * 0.44f
    fun px(x: Float) = cx + x * s
    fun py(y: Float) = cy + y * s
    fun p(x: Float, y: Float) = Offset(px(x), py(y))
    fun sz(rx: Float, ry: Float) = Size(rx * s * 2, ry * s * 2)

    // Градиенты
    val glassBrush = Brush.linearGradient(
        listOf(Color(0xFFFFE5FA), GlassPink, Color(0xFFF543C3), GlassMid, GlassCyan, Color(0xFF63C2E9), Color(0xFFD363D1)),
        start = p(-0.5f, -0.35f), end = p(0.5f, 0.4f))
    val faceBrush = Brush.radialGradient(
        listOf(Color(0xFFFFB4E9), Color(0xFFFA65D2), Color(0xFFC563D6), Color(0xFF6CE9F3)),
        center = p(0f, -0.52f), radius = s * 0.55f)
    val bellyBrush = Brush.radialGradient(
        listOf(Color(0xFFFFBFE8).copy(alpha = 0.64f), Color(0xFFFA89E0).copy(alpha = 0.14f),
            Color(0xFF4AF4F2).copy(alpha = 0.23f), Color(0xFFBB36CD).copy(alpha = 0.5f)),
        center = p(0f, 0.05f), radius = s * 0.32f)

    val rimColors = listOf(Color(0xFFFFD4F7), Color(0xFFFF99E8), Color(0xFFC8FFFF), Color(0xFF55EDFF))
    val glowPink = Color(0xFFFF19BA); val glowBlue = Color(0xFF00CFFF)

    scale(scale, scale, pivot = Offset(cx, cy)) {
        // Фоновые ореолы
        drawCircle(Brush.radialGradient(listOf(glowPink.copy(alpha = 0.28f), Color.Transparent),
            center = p(-0.25f, 0f), radius = s * 0.9f), radius = s * 0.9f, center = p(-0.25f, 0f))
        drawCircle(Brush.radialGradient(listOf(glowBlue.copy(alpha = 0.22f), Color.Transparent),
            center = p(0.2f, -0.05f), radius = s * 0.85f), radius = s * 0.85f, center = p(0.2f, -0.05f))

        // Тень под мишкой
        drawOval(Color(0xFFEF36BD).copy(alpha = 0.18f), topLeft = p(-0.38f, 0.48f), size = sz(0.38f, 0.06f))

        // === ТЕЛО ===
        // Ноги
        drawOval(glassBrush, topLeft = p(-0.26f, 0.32f), size = sz(0.16f, 0.14f))
        drawOval(glassBrush, topLeft = p(0.10f, 0.32f), size = sz(0.16f, 0.14f))
        // Лапы
        drawOval(glassBrush, topLeft = p(-0.52f, -0.10f), size = sz(0.17f, 0.22f))
        drawOval(glassBrush, topLeft = p(0.35f, -0.10f), size = sz(0.17f, 0.22f))
        // Торс
        drawOval(glassBrush, topLeft = p(-0.32f, -0.02f), size = sz(0.64f, 0.44f))

        // === ГОЛОВА ===
        drawOval(faceBrush, topLeft = p(-0.24f, -0.70f), size = sz(0.48f, 0.38f))

        // === УШКИ ===
        rotate(earAngle, pivot = p(-0.26f, -0.70f)) {
            drawOval(glassBrush, topLeft = p(-0.34f, -0.80f), size = sz(0.16f, 0.18f))
            drawOval(Color(0xFFFFC1F2).copy(alpha = 0.6f), topLeft = p(-0.30f, -0.76f), size = sz(0.10f, 0.12f))
        }
        rotate(-earAngle, pivot = p(0.26f, -0.70f)) {
            drawOval(glassBrush, topLeft = p(0.18f, -0.80f), size = sz(0.16f, 0.18f))
            drawOval(Color(0xFFD1FAFF).copy(alpha = 0.6f), topLeft = p(0.20f, -0.76f), size = sz(0.10f, 0.12f))
        }

        // === ОБВОДКА силуэта ===
        val outlinePath = Path().apply {
            addOval(Rect(p(-0.24f, -0.70f), sz(0.48f, 0.38f))) // head
            addOval(Rect(p(-0.32f, -0.02f), sz(0.64f, 0.44f))) // body
            addOval(Rect(p(-0.52f, -0.10f), sz(0.17f, 0.22f))) // left arm
            addOval(Rect(p(0.35f, -0.10f), sz(0.17f, 0.22f))) // right arm
            addOval(Rect(p(-0.26f, 0.32f), sz(0.16f, 0.14f))) // left leg
            addOval(Rect(p(0.10f, 0.32f), sz(0.16f, 0.14f))) // right leg
        }
        val rimBrush = Brush.linearGradient(rimColors, start = p(-0.4f, -0.5f), end = p(0.4f, 0.4f))
        drawPath(outlinePath, rimBrush, style = Stroke(width = s * 0.012f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // === ЛИЦО ===
        // Блик на лбу
        drawArc(Color(0xFFFFF1FB).copy(alpha = 0.55f), 250f, 80f, false,
            topLeft = p(-0.18f, -0.66f), size = sz(0.32f, 0.18f), style = Stroke(width = s * 0.022f, cap = StrokeCap.Round))
        // Глаза
        drawOval(Color(0xFFFFCCF2).copy(alpha = 0.35f), topLeft = p(-0.15f, -0.56f), size = sz(0.08f, 0.09f))
        drawOval(Color(0xFFC9FFFF).copy(alpha = 0.3f), topLeft = p(0.07f, -0.56f), size = sz(0.08f, 0.09f))
        val eyeBrush = Brush.radialGradient(listOf(Color(0xFFFFDCFA), Color(0xFFFF83E4), Color(0xFFB721A3), Color(0xFF69176F)),
            center = p(-0.10f, -0.53f), radius = s * 0.06f)
        drawOval(eyeBrush, topLeft = p(-0.13f, -0.54f), size = sz(0.06f, 0.07f))
        drawOval(eyeBrush, topLeft = p(0.07f, -0.54f), size = sz(0.06f, 0.07f))
        drawCircle(Color.White, s * 0.008f, p(-0.10f, -0.56f))
        drawCircle(Color.White, s * 0.008f, p(0.10f, -0.56f))
        // Мордочка
        drawOval(Brush.radialGradient(listOf(Color(0xFFFFD0F1), Color(0xFFF982D5), Color(0xFFBD69CC)),
            center = p(0f, -0.41f), radius = s * 0.16f), topLeft = p(-0.10f, -0.45f), size = sz(0.20f, 0.16f))
        // Нос
        drawOval(Color(0xFFC03A9D), topLeft = p(-0.02f, -0.47f), size = sz(0.04f, 0.025f))
        // Румянец
        drawOval(Color(0xFFFFB7DF).copy(alpha = 0.4f), topLeft = p(-0.22f, -0.42f), size = sz(0.07f, 0.04f))
        drawOval(Color(0xFFC3F5F2).copy(alpha = 0.3f), topLeft = p(0.15f, -0.42f), size = sz(0.07f, 0.04f))

        // === ЖИВОТИК ===
        drawOval(bellyBrush, topLeft = p(-0.18f, 0.02f), size = sz(0.36f, 0.28f))

        // Play в животике
        val playBg = p(-0.08f, 0.08f)
        val playSz = sz(0.16f, 0.16f)
        drawOval(Color(0xFF3E245B).copy(alpha = 0.55f), topLeft = playBg, size = playSz)
        drawOval(Color(0xFFFF95E5), topLeft = playBg, size = playSz, style = Stroke(width = s * 0.004f))
        val playCenter = p(0f, 0.16f)
        if (playing) {
            // Pause
            drawLine(Mint, Offset(playCenter.x - s * 0.025f, playCenter.y - s * 0.04f),
                Offset(playCenter.x - s * 0.025f, playCenter.y + s * 0.04f), s * 0.014f, cap = StrokeCap.Round)
            drawLine(Mint, Offset(playCenter.x + s * 0.025f, playCenter.y - s * 0.04f),
                Offset(playCenter.x + s * 0.025f, playCenter.y + s * 0.04f), s * 0.014f, cap = StrokeCap.Round)
        } else {
            val path = Path().apply {
                moveTo(playCenter.x - s * 0.03f, playCenter.y - s * 0.04f)
                lineTo(playCenter.x + s * 0.045f, playCenter.y)
                lineTo(playCenter.x - s * 0.03f, playCenter.y + s * 0.04f); close()
            }
            drawPath(path, Color(0xFF91FFF0).copy(alpha = 0.15f))
            drawPath(path, Color(0xFFB6FFF5), style = Stroke(width = s * 0.006f, join = StrokeJoin.Round))
        }

        // === ЗНАЧКИ (неоновые) ===
        val neonColor = Color(0xFFBAFFF4)
        val neonW = s * 0.0045f
        // Левое ухо — громкость минус
        drawVolumeIcon(p(-0.33f, -0.72f), sz(0.06f, 0.07f), false, neonColor, neonW)
        // Правое ухо — громкость плюс
        drawVolumeIcon(p(0.27f, -0.72f), sz(0.06f, 0.07f), true, neonColor, neonW)
        // Левая лапа — предыдущая
        drawPrevIcon(p(-0.42f, 0f), sz(0.05f, 0.05f), neonColor, neonW)
        // Правая лапа — следующая
        drawNextIcon(p(0.37f, 0f), sz(0.05f, 0.05f), neonColor, neonW)
        // Левая нога — библиотека
        drawLibraryIcon(p(-0.18f, 0.38f), sz(0.05f, 0.06f), neonColor, neonW)
        // Правая нога — сердечко
        drawHeartIcon(p(0.18f, 0.38f), sz(0.05f, 0.05f), neonColor, neonW)

        // === ПОДСВЕТКА НАЖАТОЙ ЗОНЫ ===
        tapPart?.let { zone ->
            val (zx, zy, zrx, zry) = when (zone) {
                HitZone.LEFT_EAR -> listOf(-0.31f, -0.62f, 0.19f, 0.19f)
                HitZone.RIGHT_EAR -> listOf(0.31f, -0.62f, 0.19f, 0.19f)
                HitZone.LEFT_PAW -> listOf(-0.44f, -0.04f, 0.16f, 0.20f)
                HitZone.RIGHT_PAW -> listOf(0.44f, -0.04f, 0.16f, 0.20f)
                HitZone.BELLY -> listOf(0f, 0.08f, 0.24f, 0.26f)
                HitZone.LEFT_FOOT -> listOf(-0.2f, 0.4f, 0.15f, 0.13f)
                HitZone.RIGHT_FOOT -> listOf(0.2f, 0.4f, 0.15f, 0.13f)
                HitZone.NONE -> return@let
            }
            drawOval(Mint.copy(alpha = 0.32f), topLeft = p(zx - zrx, zy - zry), size = sz(zrx, zry))
        }
    }
}

// ---------- ИКОНКИ ----------
private fun DrawScope.drawVolumeIcon(center: Offset, size: Size, plus: Boolean, color: Color, w: Float) {
    val x = center.x; val y = center.y
    val pw = size.width * 0.85f; val ph = size.height
    drawPath(Path().apply {
        moveTo(x - pw * 0.5f, y - ph * 0.2f); lineTo(x - pw * 0.3f, y - ph * 0.12f); lineTo(x - pw * 0.12f, y - ph * 0.35f)
        lineTo(x - pw * 0.12f, y + ph * 0.35f); lineTo(x - pw * 0.3f, y + ph * 0.12f); lineTo(x - pw * 0.5f, y + ph * 0.2f); close()
    }, color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (plus) {
        drawLine(color, Offset(x + pw * 0.1f, y - ph * 0.25f), Offset(x + pw * 0.1f, y + ph * 0.25f), w, cap = StrokeCap.Round)
        drawLine(color, Offset(x + pw * 0.1f - ph * 0.25f, y), Offset(x + pw * 0.1f + ph * 0.25f, y), w, cap = StrokeCap.Round)
    } else {
        drawLine(color, Offset(x + pw * 0.1f - ph * 0.2f, y), Offset(x + pw * 0.1f + ph * 0.2f, y), w, cap = StrokeCap.Round)
    }
}
private fun DrawScope.drawPrevIcon(center: Offset, size: Size, color: Color, w: Float) {
    val cx = center.x; val cy = center.y; val a = size.width * 0.5f
    drawPath(Path().apply { moveTo(cx - a * 0.2f, cy); lineTo(cx - a, cy - a); lineTo(cx - a, cy + a); close() }, color,
        style = Stroke(w, join = StrokeJoin.Round))
    drawLine(color, Offset(cx - a * 0.5f, cy - a), Offset(cx - a * 0.5f, cy + a), w, cap = StrokeCap.Round)
}
private fun DrawScope.drawNextIcon(center: Offset, size: Size, color: Color, w: Float) {
    val cx = center.x; val cy = center.y; val a = size.width * 0.5f
    drawPath(Path().apply { moveTo(cx + a * 0.2f, cy); lineTo(cx + a, cy - a); lineTo(cx + a, cy + a); close() }, color,
        style = Stroke(w, join = StrokeJoin.Round))
    drawLine(color, Offset(cx + a * 0.5f, cy - a), Offset(cx + a * 0.5f, cy + a), w, cap = StrokeCap.Round)
}
private fun DrawScope.drawLibraryIcon(center: Offset, size: Size, color: Color, w: Float) {
    val cx = center.x; val cy = center.y; val a = size.width * 0.5f
    drawPath(Path().apply {
        moveTo(cx - a * 0.7f, cy - a); lineTo(cx - a * 0.3f, cy - a)
        lineTo(cx - a * 0.3f, cy + a * 0.8f); lineTo(cx + a * 0.7f, cy + a * 0.8f)
        lineTo(cx + a * 0.7f, cy - a); lineTo(cx + a * 0.3f, cy - a)
        lineTo(cx + a * 0.3f, cy + a * 0.8f)
    }, color, style = Stroke(w, join = StrokeJoin.Round))
    drawLine(color, Offset(cx - a * 0.3f, cy + a * 0.2f), Offset(cx - a * 0.3f, cy + a * 0.7f), w, cap = StrokeCap.Round)
}
private fun DrawScope.drawHeartIcon(center: Offset, size: Size, color: Color, w: Float) {
    val cx = center.x; val cy = center.y; val a = size.width * 0.45f; val b = a * 0.85f
    val path = Path().apply {
        moveTo(cx, cy + b)
        cubicTo(cx - a * 1.3f, cy + b * 0.3f, cx - a * 1.3f, cy - b * 0.5f, cx, cy - b * 0.1f)
        cubicTo(cx + a * 1.3f, cy - b * 0.5f, cx + a * 1.3f, cy + b * 0.3f, cx, cy + b)
    }
    drawPath(path, Color(0xFFFFB8EC).copy(alpha = 0.18f))
    drawPath(path, color, style = Stroke(w, join = StrokeJoin.Round))
}
private fun DrawScope.playPauseIcon(size: Size, playing: Boolean) {
    val cx = size.width / 2f; val cy = size.height / 2f; val a = size.width * 0.38f
    if (playing) {
        drawLine(Mint, Offset(cx - a * 0.45f, cy - a), Offset(cx - a * 0.45f, cy + a), 3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Mint, Offset(cx + a * 0.45f, cy - a), Offset(cx + a * 0.45f, cy + a), 3.dp.toPx(), cap = StrokeCap.Round)
    } else {
        val path = Path().apply {
            moveTo(cx - a * 0.55f, cy - a); lineTo(cx + a * 0.7f, cy); lineTo(cx - a * 0.55f, cy + a); close()
        }
        drawPath(path, Mint)
    }
}
private fun DrawScope.prevIcon(size: Size) {
    val cx = size.width / 2f; val cy = size.height / 2f; val a = size.width * 0.35f
    drawPath(Path().apply { moveTo(cx - a * 0.15f, cy); lineTo(cx - a, cy - a); lineTo(cx - a, cy + a); close() },
        Mint, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
    drawLine(Color(0xFFFFD5F1), Offset(cx - a * 0.4f, cy - a), Offset(cx - a * 0.4f, cy + a), 2.dp.toPx(), cap = StrokeCap.Round)
}
private fun DrawScope.nextIcon(size: Size) {
    val cx = size.width / 2f; val cy = size.height / 2f; val a = size.width * 0.35f
    drawPath(Path().apply { moveTo(cx + a * 0.15f, cy); lineTo(cx + a, cy - a); lineTo(cx + a, cy + a); close() },
        Mint, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
    drawLine(Color(0xFFFFD5F1), Offset(cx + a * 0.4f, cy - a), Offset(cx + a * 0.4f, cy + a), 2.dp.toPx(), cap = StrokeCap.Round)
}