package com.tu.iptv.data

data class Channel(
    val name: String,
    val urls: List<String>,      // 多个流地址（从高到低质量），自动failover
    val logo: String? = null,
    val group: String = "其他",
    val resolution: String? = null
) {
    val url: String get() = urls.first()
}
