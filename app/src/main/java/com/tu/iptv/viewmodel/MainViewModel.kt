package com.tu.iptv.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tu.iptv.data.Channel
import com.tu.iptv.data.M3uParser
import com.tu.iptv.network.M3uDownloader
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    sealed class UiState {
        object Loading : UiState()
        data class Success(
            val channels: List<Channel>,
            val groups: List<String>
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    var uiState by mutableStateOf<UiState>(UiState.Loading)
        private set

    private val downloader = M3uDownloader()

    init {
        loadChannels()
    }

    fun loadChannels() {
        viewModelScope.launch {
            uiState = UiState.Loading
            downloader.download()
                .onSuccess { content ->
                    val channels = M3uParser.parse(content)
                    val groups = channels.map { it.group }.distinct()
                    uiState = if (channels.isEmpty()) {
                        UiState.Error("未解析到任何频道，请检查 M3U 格式")
                    } else {
                        UiState.Success(channels, groups)
                    }
                }
                .onFailure { e ->
                    uiState = UiState.Error(e.message ?: "未知错误")
                }
        }
    }
}
