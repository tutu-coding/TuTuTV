package com.tu.iptv.data

object M3uParser {

    private val IGNORED_GROUPS = setOf("General", "general", "undefined", "")

    private val GROUP_REMAP = mapOf(
        "央视台"  to "央视频道",
        "其他频道" to "卫视频道"
    )

    // 按频道名强制覆盖分组（优先级高于 M3U 的 group-title）
    private val CCTV5_PATTERN = Regex("""CCTV[-\s]?5""", RegexOption.IGNORE_CASE)
    private fun applyNameOverride(name: String, group: String): String = when {
        CCTV5_PATTERN.containsMatchIn(name) -> "CCTV5 体育"
        else -> group
    }

    // 从频道名推断分组（用于没有 group-title 的条目）
    private fun inferGroup(name: String): String = when {
        name.contains("CCTV", ignoreCase = true) ||
        name.contains("央视") || name.contains("中央") -> "央视频道"
        name.contains("卫视") -> "卫视频道"
        name.contains("凤凰") -> "卫视频道"
        name.contains("体育") || name.contains("Sport", ignoreCase = true) -> "体育频道"
        name.contains("卡通") || name.contains("动漫") || name.contains("少儿") -> "卡通频道"
        else -> "其他"
    }

    // 去掉频道名里的分辨率后缀，用于去重 key
    // 例："CCTV-1 (1080p)" → "CCTV-1"，"CCTV1[1080][S]" → "CCTV1"
    private val RES_SUFFIX = Regex("""[\s\-_]*[\[(（]?\d{3,4}[pP]?[\]）)]*$|[\s\-_]*[\[(（][^\]）)]*[\]）)]$""")
    private fun normalizeName(name: String) = name.replace(RES_SUFFIX, "").trim()

    fun parse(content: String): List<Channel> {
        val channelMap = LinkedHashMap<String, ChannelBuilder>()
        val lines = content.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.startsWith("#EXTINF")) {
                val displayName = line.substringAfterLast(",").trim()
                val tvgName = extractAttr(line, "tvg-name")
                val normDisplay = normalizeName(displayName)
                // 以 "+" 作为频道区分标志：显示名有 "+" 而 tvg-name 没有，说明显示名更精确（如 CCTV5+）
                val name = when {
                    tvgName.isNullOrEmpty()                              -> normDisplay
                    normDisplay.contains("+") && !tvgName.contains("+") -> normDisplay
                    tvgName.contains("+") && !normDisplay.contains("+") -> normalizeName(tvgName)
                    else                                                 -> normalizeName(tvgName)
                }
                val logo = extractAttr(line, "tvg-logo")
                val rawGroup = extractAttr(line, "group-title")
                    ?.takeIf { it !in IGNORED_GROUPS }
                val baseGroup = rawGroup?.let { GROUP_REMAP[it] ?: it } ?: inferGroup(name)
                val group = applyNameOverride(name, baseGroup)
                val resolution = extractResolution(line) ?: extractResolution(displayName)

                // 找下一个非注释行作为流地址
                var j = i + 1
                while (j < lines.size && lines[j].trim().startsWith("#")) j++

                val url = lines.getOrNull(j)?.trim() ?: ""
                if (url.isNotEmpty() &&
                    (url.startsWith("http") || url.startsWith("rtmp") || url.startsWith("rtp"))
                ) {
                    // 用归一化名字去重，优先使用已有的 logo/resolution
                    val key = "$group|$name"
                    val builder = channelMap.getOrPut(key) {
                        ChannelBuilder(name, logo, group, resolution)
                    }
                    if (builder.logo == null && logo != null) builder.logo = logo
                    if (builder.resolution == null && resolution != null) builder.resolution = resolution
                    builder.urls.add(url)
                    i = j + 1
                    continue
                }
            }
            i++
        }

        return channelMap.values.map { it.toChannel() }
    }

    private class ChannelBuilder(
        val name: String,
        var logo: String?,
        val group: String,
        var resolution: String?
    ) {
        val urls = mutableListOf<String>()
        fun toChannel() = Channel(name, urls.toList(), logo, group, resolution)
    }

    private fun extractAttr(line: String, attr: String): String? {
        val pattern = Regex("""$attr="([^"]*)"""")
        return pattern.find(line)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
    }

    private fun extractResolution(line: String): String? {
        // 匹配 "1080p" / "720p" / "1920x1080" / "(1080p)" / "[1080]" / "4K"
        val resPattern = Regex("""(\d{3,4}[xX×]\d{3,4})|([48]K|1080[pi]?|720[pi]?|576[pi]?|480[pi]?)""")
        val raw = resPattern.find(line)?.value ?: return null
        // 统一格式：纯数字 "1080" → "1080p"
        return if (raw.last().isDigit()) "${raw}p" else raw
    }
}
