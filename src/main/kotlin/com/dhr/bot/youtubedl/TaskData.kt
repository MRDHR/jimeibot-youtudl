package com.dhr.bot.youtubedl

import kotlinx.serialization.Serializable
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value

object TaskData : AutoSavePluginData("DownloadTask") {
    var task: MutableList<Task> by value()
}

@Serializable
class Task(
    var groupId: Long,
    var url: String,
    var status: String = ""
)