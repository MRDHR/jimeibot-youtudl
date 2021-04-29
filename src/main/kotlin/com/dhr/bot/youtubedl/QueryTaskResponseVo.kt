package com.dhr.bot.youtubedl

class QueryTaskResponseVo : ArrayList<QueryTaskResponseVoItem>()

data class QueryTaskResponseVoItem(
    val canon: String,
    val dbytes: Int,
    val eta: String,
    val filename: String,
    val id: String,
    val mode: String,
    val name: String,
    val path: String,
    val percent: Double,
    val speed: String,
    val status: String,
    val tbytes: String,
    val time: Double,
    val url: String
)