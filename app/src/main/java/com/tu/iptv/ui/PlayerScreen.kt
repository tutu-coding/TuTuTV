package com.tu.iptv.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import android.app.Activity
import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tu.iptv.data.Channel
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(channel: Channel) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val prefs = remember { context.getSharedPreferences("stream_prefs", Context.MODE_PRIVATE) }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var bufferedPercent by remember { mutableStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }
    // 0 = 播放/暂停按钮, 1 = 刷新按钮
    var selectedControl by remember { mutableStateOf(0) }
    // 当前线路索引：优先读取上次记忆的线路
    val prefKey = "${channel.group}|${channel.name}"
    var urlIndex by remember(channel) {
        val saved = prefs.getInt(prefKey, 0).coerceIn(0, (channel.urls.size - 1).coerceAtLeast(0))
        mutableStateOf(saved)
    }
    val currentUrl by remember(channel, urlIndex) {
        derivedStateOf { channel.urls.getOrNull(urlIndex) ?: channel.url }
    }

    val dataSourceFactory = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Android TV)")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
    }

    // 直播流优化：缓冲不要太大，否则会等太久
    val loadControl = remember {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                3_000,   // 最小缓冲 3 秒，减少等待
                20_000,  // 最大缓冲 20 秒
                1_500,   // 首次播放前缓冲 1.5 秒即可开始
                3_000    // 卡顿恢复后缓冲 3 秒
            )
            .setPrioritizeTimeOverSizeThresholds(true) // 直播优先时间而非大小
            .build()
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_READY) errorMessage = null
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        isBuffering = false
                        // 自动切换到下一个备用流地址
                        if (urlIndex < channel.urls.size - 1) {
                            urlIndex++
                        } else {
                            errorMessage = "所有线路均不可用 (${error.errorCodeName})"
                        }
                    }
                })
            }
    }

    // 定时轮询缓冲进度
    LaunchedEffect(exoPlayer) {
        while (true) {
            bufferedPercent = exoPlayer.bufferedPercentage / 100f
            delay(500)
        }
    }

    // 线路变化时持久化记忆（手动切换和自动 failover 都保存）
    LaunchedEffect(urlIndex) {
        prefs.edit().putInt(prefKey, urlIndex).apply()
    }

    // 切换频道时显示控制栏
    LaunchedEffect(channel) {
        showControls = true
    }

    // 加载/切换流地址（频道切换或 failover 时触发）
    LaunchedEffect(currentUrl) {
        errorMessage = null
        isBuffering = true
        exoPlayer.setMediaItem(MediaItem.fromUri(currentUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // 刷新：回到第一条线路重新连接
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger == 0) return@LaunchedEffect
        isRefreshing = true
        errorMessage = null
        isBuffering = true
        exoPlayer.stop()
        exoPlayer.setMediaItem(MediaItem.fromUri(currentUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        delay(1_500)
        isRefreshing = false
    }

    // 控制栏显示时重置选中到第一个按钮
    LaunchedEffect(showControls) {
        if (showControls) selectedControl = 0
    }

    // 控制栏自动隐藏（4 秒无操作）
    LaunchedEffect(showControls, isPlaying, selectedControl) {
        if (showControls && isPlaying) {
            delay(4_000)
            showControls = false
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE  -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            exoPlayer.release()
        }
    }

    // 请求焦点，确保遥控器按键可以响应
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                when (event.key) {
                    Key.Enter, Key.DirectionCenter -> {
                        if (showControls) {
                            when (selectedControl) {
                                0 -> exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                                1 -> refreshTrigger++
                                2 -> urlIndex = (urlIndex + 1) % channel.urls.size
                            }
                        } else {
                            showControls = true
                        }
                        true
                    }
                    // 左右键在控制按钮间切换
                    Key.DirectionLeft -> {
                        if (showControls) {
                            selectedControl = (selectedControl - 1).coerceAtLeast(0)
                            true
                        } else {
                            showControls = true; false
                        }
                    }
                    Key.DirectionRight -> {
                        if (showControls) {
                            selectedControl = (selectedControl + 1).coerceAtMost(2)
                            true
                        } else {
                            showControls = true; false
                        }
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        showControls = true
                        false
                    }
                    Key.Back -> {
                        if (showControls) { showControls = false; true } else false
                    }
                    else -> false
                }
            }
    ) {
        // 播放器视图
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 缓冲转圈
        if (isBuffering && errorMessage == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF2979FF),
                strokeWidth = 3.dp
            )
        }

        // 错误提示
        errorMessage?.let { msg ->
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("播放失败", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(msg, color = Color(0xFFEF5350), fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text("按确认键重试", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            }
        }

        // 控制栏覆盖层
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ControlsOverlay(
                channel = channel,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                isRefreshing = isRefreshing,
                bufferedPercent = bufferedPercent,
                selectedControl = selectedControl,
                urlIndex = urlIndex
            )
        }
    }
}

@Composable
private fun ControlButton(
    label: String,
    isSelected: Boolean,
    isActive: Boolean
) {
    Text(
        text = label,
        color = when {
            isSelected -> Color.Black
            isActive   -> Color(0xFF90CAF9)
            else       -> Color.White
        },
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isSelected -> Color.White
                    isActive   -> Color(0xFF0D47A1)
                    else       -> Color.White.copy(alpha = 0.15f)
                }
            )
            .padding(20.dp, 12.dp)
    )
}

@Composable
private fun ControlsOverlay(
    channel: Channel,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isRefreshing: Boolean,
    bufferedPercent: Float,
    selectedControl: Int,
    urlIndex: Int
) {
    Box(modifier = Modifier.fillMaxSize()) {

        // 顶部渐变 + 频道信息
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                    )
                )
                .padding(24.dp, 20.dp, 24.dp, 40.dp)
        ) {
            Column {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = channel.group,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                    channel.resolution?.let {
                        Text(
                            text = it,
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1B5E20))
                                .padding(6.dp, 2.dp)
                        )
                    }
                }
            }
        }

        // 中央控制按钮行
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(
                label = if (isBuffering) "⏳ 缓冲中" else if (isPlaying) "⏸ 暂停" else "▶ 继续",
                isSelected = selectedControl == 0,
                isActive = false
            )
            ControlButton(
                label = if (isRefreshing) "↺ 刷新中…" else "↺ 刷新",
                isSelected = selectedControl == 1,
                isActive = isRefreshing
            )
            if (channel.urls.size > 1) {
                ControlButton(
                    label = "线路 ${urlIndex + 1} / ${channel.urls.size}",
                    isSelected = selectedControl == 2,
                    isActive = false
                )
            }
        }

        // 底部渐变 + 进度条 + LIVE 标签
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .padding(24.dp, 40.dp, 24.dp, 20.dp)
        ) {
            // 缓冲进度条
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // LIVE 徽章
                Text(
                    text = "● LIVE",
                    color = Color(0xFFEF5350),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF3E0000))
                        .padding(8.dp, 3.dp)
                )

                // 缓冲条
                LinearProgressIndicator(
                    progress = { bufferedPercent },
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF2979FF),
                    trackColor = Color.White.copy(alpha = 0.2f)
                )

                Text(
                    text = when {
                        isRefreshing -> "刷新中…"
                        isBuffering  -> "缓冲中…"
                        isPlaying    -> "播放中"
                        else         -> "已暂停"
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )

            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "← → 切换按钮  ·  确认键 执行  ·  上/下键 关闭控制栏",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp
            )
        }
    }
}
