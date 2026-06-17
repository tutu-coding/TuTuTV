package com.tu.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tu.iptv.data.Channel

private val BgColor = Color(0xFF0D0D0D)
private val PanelBg = Color(0xFF141414)
private val FocusedBg = Color(0xFF2979FF)
private val SelectedGroupBg = Color(0xFF1E1E1E)
private val LastPlayedBg = Color(0xFF1A237E)   // 上次播放的频道底色
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF9E9E9E)
private val Divider = Color(0xFF2A2A2A)

@Composable
fun ChannelListScreen(
    channels: List<Channel>,
    groups: List<String>,
    lastPlayedChannel: Channel?,
    onChannelClick: (Channel) -> Unit
) {
    // 返回时恢复上次的分组
    var selectedGroup by remember {
        mutableStateOf(lastPlayedChannel?.group ?: groups.firstOrNull() ?: "")
    }
    val displayChannels = remember(channels, selectedGroup) {
        channels.filter { it.group == selectedGroup }
    }

    // 每个分组对应一个 FocusRequester，用于从右侧列表按左键时精准跳回
    val groupFocusRequesters = remember(groups) {
        groups.associateWith { FocusRequester() }
    }
    val firstChannelFocusRequester = remember { FocusRequester() }
    val channelListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        GroupPanel(
            groups = groups,
            selectedGroup = selectedGroup,
            groupFocusRequesters = groupFocusRequesters,
            onGroupSelected = { selectedGroup = it },
            onNavigateToChannels = {
                coroutineScope.launch {
                    channelListState.scrollToItem(0)
                    delay(100)
                    try { firstChannelFocusRequester.requestFocus() } catch (_: Exception) { }
                }
            },
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(PanelBg)
        )

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Divider)
        )

        ChannelPanel(
            channels = displayChannels,
            lastPlayedChannel = lastPlayedChannel,
            onChannelClick = onChannelClick,
            onNavigateToGroup = { groupFocusRequesters[selectedGroup]?.requestFocus() },
            listState = channelListState,
            firstChannelFocusRequester = firstChannelFocusRequester,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun GroupPanel(
    groups: List<String>,
    selectedGroup: String,
    groupFocusRequesters: Map<String, FocusRequester>,
    onGroupSelected: (String) -> Unit,
    onNavigateToChannels: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "频道分组",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
        )
        LazyColumn {
            items(groups) { group ->
                GroupItem(
                    group = group,
                    isSelected = group == selectedGroup,
                    focusRequester = groupFocusRequesters[group] ?: FocusRequester(),
                    onFocused = { onGroupSelected(group) },
                    onClick = { onGroupSelected(group) },
                    onNavigateToChannels = onNavigateToChannels
                )
            }
        }
    }
}

@Composable
private fun GroupItem(
    group: String,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onNavigateToChannels: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp, 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isFocused -> FocusedBg
                    isSelected -> SelectedGroupBg
                    else -> Color.Transparent
                }
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    onNavigateToChannels(); true
                } else false
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                when (event.key) {
                    Key.Enter, Key.DirectionCenter -> { onClick(); true }
                    else -> false
                }
            }
            .padding(12.dp, 10.dp)
    ) {
        Text(
            text = group,
            color = if (isFocused || isSelected) TextPrimary else TextSecondary,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChannelPanel(
    channels: List<Channel>,
    lastPlayedChannel: Channel?,
    onChannelClick: (Channel) -> Unit,
    onNavigateToGroup: () -> Unit,
    listState: LazyListState,
    firstChannelFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val lastPlayedFocusRequester = remember { FocusRequester() }

    // 返回时自动滚动并将光标放到上次播放的频道
    LaunchedEffect(lastPlayedChannel) {
        if (lastPlayedChannel == null) return@LaunchedEffect
        val index = channels.indexOfFirst { it.name == lastPlayedChannel.name }
        if (index >= 0) {
            listState.scrollToItem(index = (index - 2).coerceAtLeast(0))
            delay(100)
            // 第一个频道用 firstChannelFocusRequester，其余用 lastPlayedFocusRequester
            val fr = if (index == 0) firstChannelFocusRequester else lastPlayedFocusRequester
            try { fr.requestFocus() } catch (_: Exception) { }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(8.dp)
    ) {
        itemsIndexed(channels) { index, channel ->
            ChannelItem(
                channel = channel,
                isLastPlayed = channel.name == lastPlayedChannel?.name,
                focusRequester = when {
                    index == 0                             -> firstChannelFocusRequester
                    channel.name == lastPlayedChannel?.name -> lastPlayedFocusRequester
                    else                                   -> null
                },
                onClick = { onChannelClick(channel) },
                onNavigateToGroup = onNavigateToGroup
            )
        }
    }
}

@Composable
private fun ChannelItem(
    channel: Channel,
    isLastPlayed: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onNavigateToGroup: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp, 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused    -> FocusedBg
                    isLastPlayed -> LastPlayedBg
                    else         -> Color(0xFF1A1A1A)
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            // onPreviewKeyEvent 在默认焦点移动之前拦截，防止左键跳到平行位置
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    onNavigateToGroup()
                    true
                } else false
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick(); true
                } else false
            }
            .padding(16.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 上次播放指示点
        if (isLastPlayed) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF2979FF))
            )
            Spacer(Modifier.width(8.dp))
        }

        Text(
            text = channel.name,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = if (isLastPlayed) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        channel.resolution?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                text = it,
                color = if (isFocused) Color.White.copy(alpha = 0.8f) else Color(0xFF4CAF50),
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isFocused) Color.White.copy(alpha = 0.15f)
                        else Color(0xFF1B5E20)
                    )
                    .padding(6.dp, 2.dp)
            )
        }
    }
}
