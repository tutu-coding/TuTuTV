package com.tu.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tu.iptv.data.Channel
import com.tu.iptv.ui.ChannelListScreen
import com.tu.iptv.ui.PlayerScreen
import com.tu.iptv.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var playingChannel by remember { mutableStateOf<Channel?>(null) }
            var lastPlayedChannel by remember { mutableStateOf<Channel?>(null) }

            if (playingChannel != null) {
                BackHandler { playingChannel = null }
                PlayerScreen(channel = playingChannel!!)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0D0D0D))
                ) {
                    when (val state = viewModel.uiState) {

                        is MainViewModel.UiState.Loading -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = Color(0xFF2979FF))
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "正在加载频道列表…",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 16.sp
                                )
                            }
                        }

                        is MainViewModel.UiState.Success -> {
                            ChannelListScreen(
                                channels = state.channels,
                                groups = state.groups,
                                lastPlayedChannel = lastPlayedChannel,
                                onChannelClick = { channel ->
                                    lastPlayedChannel = channel
                                    playingChannel = channel
                                }
                            )
                        }

                        is MainViewModel.UiState.Error -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "加载失败",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = state.message,
                                    color = Color(0xFFEF5350),
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.height(24.dp))
                                Button(onClick = { viewModel.loadChannels() }) {
                                    Text("重新加载")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
