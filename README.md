# TuTuTV

Android TV IPTV 直播播放器，支持 M3U 播放列表，专为电视遥控器操作优化。

---

## 功能特性

- **M3U 播放列表**：启动时自动从网络拉取 M3U 频道列表并解析
- **频道分组**：左侧分组面板 + 右侧频道列表，双栏布局
- **多线路备用**：同一频道支持多条流地址，播放失败自动切换备用线路，并记忆上次成功的线路
- **上次播放记忆**：返回频道列表时自动定位到上次播放的频道
- **播放控制栏**：覆盖层支持暂停/继续、刷新、手动切换线路，4 秒无操作自动隐藏
- **缓冲状态显示**：底部实时显示缓冲进度条和播放状态（缓冲中 / 播放中 / 已暂停）
- **防熄屏**：播放期间保持屏幕常亮
- **遥控器全键盘支持**：方向键导航、确认键选择、返回键退出控制栏或返回列表

## 技术栈

| 组件 | 库 |
|------|-----|
| UI | Jetpack Compose + Material3 |
| 播放器 | Media3 ExoPlayer 1.3.1（含 HLS 支持）|
| 网络 | OkHttp 4.12 |
| 架构 | ViewModel + Compose State |
| 语言 | Kotlin 2.0 |
| 最低 API | Android TV（API 21+）|

## 项目结构

```
app/src/main/java/com/tu/iptv/
├── MainActivity.kt          # 入口，管理频道列表与播放器之间的页面切换
├── data/
│   ├── Channel.kt           # 频道数据模型
│   └── M3uParser.kt         # M3U 格式解析
├── network/
│   └── M3uDownloader.kt     # 从网络下载 M3U 文件
├── ui/
│   ├── ChannelListScreen.kt # 双栏频道列表界面
│   └── PlayerScreen.kt      # 播放界面（ExoPlayer + 控制栏）
└── viewmodel/
    └── MainViewModel.kt     # 状态管理，驱动 UI
```

## 遥控器操作说明

**频道列表**

| 按键 | 操作 |
|------|------|
| 上/下 | 在当前面板内移动 |
| 右键 | 从分组面板跳到频道列表 |
| 左键 | 从频道列表跳回分组面板 |
| 确认 | 播放选中频道 |

**播放界面**

| 按键 | 操作 |
|------|------|
| 确认 | 显示/隐藏控制栏 / 执行选中按钮 |
| 左/右 | 在控制按钮间切换（暂停、刷新、切线路）|
| 返回 | 隐藏控制栏 / 退出播放 |

## 构建运行

1. 用 Android Studio 打开项目
2. 在 `app/src/main/java/com/tu/iptv/network/M3uDownloader.kt` 中填入你的 M3U 订阅地址
3. 连接 Android TV 设备或启动 TV 模拟器
4. 点击 Run 即可

## License

MIT
