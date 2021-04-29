package com.dhr.bot.youtubedl

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.BotOnlineEvent
import net.mamoe.mirai.event.events.BotReloginEvent
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.subscribeGroupMessages
import net.mamoe.mirai.message.data.content
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.math.BigDecimal
import java.math.RoundingMode


object YoutubeDlPlugin : KotlinPlugin(
    JvmPluginDescription(
        id = YoutubeDlPlugin::class.java.name,
        version = "0.0.1",
        name = "扒源 power by 一生的等待"
    ) {
    }
) {
    private var gson = Gson()
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build()
    private var job: Job? = null
    private lateinit var bot: Bot

    override fun onEnable() {
        TaskData.reload()
        initYouTubeDl()
        GlobalEventChannel.subscribeAlways<BotReloginEvent> {
            this@YoutubeDlPlugin.bot = bot
            queryTask()
        }
        GlobalEventChannel.subscribeAlways<BotOnlineEvent> {
            this@YoutubeDlPlugin.bot = bot
            queryTask()
        }
    }

    private fun initYouTubeDl() {
        GlobalEventChannel.subscribeGroupMessages {
            always {
                when {
                    message.content.startsWith("ytdl-add ") -> {
                        val replace = message.content.replace("ytdl-add ", "")
                        val split = replace.split(" ")
                        when (split.size) {
                            2 -> {
                                //url 文件夹
                                val url = split[0]
                                val folder = split[1]
                                addDownloadQueue(this, url, folder)
                            }
                            3 -> {
                                //url 文件夹 视频名字
                                val url = split[0]
                                val folder = split[1]
                                val name = split[2]
                                addDownloadQueue(this, url, folder, name)
                            }
                            else -> {
                                sender.group.sendMessage("扒源任务添加失败(参数格式错误)，正确示例: ytdl-add https://www.youtube.com/watch?v=xxxxx 其他/测试/ xxxx.mp4 \n 注意文件夹必须存在,视频名字名字可不写")
                            }
                        }
                    }
                    message.content.startsWith("ytdl-query ") -> {
                        val url = message.content.replace("ytdl-query ", "")
                        searchDownloadQueue(this, url)
                    }
                    message.content == "ytdl-help" -> {
                        sender.group.sendMessage(
                            "扒源相关功能 \n" +
                                "1：添加扒源任务\n" +
                                "ytdl-add https://www.youtube.com/watch?v=xxxxx 其他/测试/ xxxx.mp4 \n注意文件夹必须存在,视频名字名字可不写\n" +
                                "2:查询扒源任务\n" +
                                "ytdl-query https://www.youtube.com/watch?v=xxxxx"
                        )
                    }
                }
            }
        }
    }

    private fun addDownloadQueue(messageEvent: GroupMessageEvent, url: String, folder: String, name: String? = "") {
        launch(Dispatchers.IO) {
            val fileName = name ?: ""
            val formBody: FormBody = FormBody.Builder()
                .add("url", url)
                .add("videoPath", "/video/$folder")
                .add("videoName", fileName)
                .build()
            val request: Request = Request.Builder()
                .url("https://video.mrdvh.com/addToQueue")
                .post(formBody)
                .build()
            val response: Response = okHttpClient.newCall(request).execute()
            val string = response.body?.string()
            val addTaskResponseVo = fromJson(string, AddTaskResponseVo::class.java)
            if ("ok".equals(addTaskResponseVo?.state, true)) {
                TaskData.task.add(Task(messageEvent.group.id, url, "queued"))
                messageEvent.group.sendMessage("扒源任务添加成功")
            } else {
                messageEvent.group.sendMessage("扒源任务添加失败")
            }
        }
    }

    private fun searchDownloadQueue(messageEvent: GroupMessageEvent, url: String) {
        launch(Dispatchers.IO) {
            val request: Request = Request.Builder()
                .url("https://video.mrdvh.com/downloadQueue")
                .get()
                .build()
            val response: Response = okHttpClient.newCall(request).execute()
            val string = response.body?.string()
            val queryTaskResponseVo = fromJson(string, QueryTaskResponseVo::class.java)
            if (null != queryTaskResponseVo) {
                run breaking@{
                    queryTaskResponseVo.forEach {
                        if (it.url == url) {
                            val status = when (it.status) {
                                "error" -> {
                                    "下载失败"
                                }
                                "completed" -> {
                                    "已完成"
                                }
                                "queued" -> {
                                    "数据查询中"
                                }
                                "finished" -> {
                                    "音视频合并中"
                                }
                                "downloading" -> {
                                    "下载中"
                                }
                                else -> "未知状态"
                            }
                            val percent = BigDecimal(it.percent).setScale(0, RoundingMode.HALF_UP).toString()
                            messageEvent.group.sendMessage("查询到任务 链接：$url 状态：$status 下载进度：$percent")
                            return@breaking
                        }
                    }
                }
            } else {
                messageEvent.group.sendMessage("未查询到任务")
            }
        }
    }

    private fun queryTask() {
        job?.cancel()
        job = launch(Dispatchers.IO) {
            try {
                val completedTaskList = mutableListOf<String>()
                TaskData.task.forEach {
                    val request: Request = Request.Builder()
                        .url("https://video.mrdvh.com/downloadQueue")
                        .get()
                        .build()
                    val response: Response = okHttpClient.newCall(request).execute()
                    val string = response.body?.string()
                    val queryTaskResponseVo = fromJson(string, QueryTaskResponseVo::class.java)
                    if (null != queryTaskResponseVo) {
                        run breaking@{
                            queryTaskResponseVo.forEach { it1 ->
                                if (it.url == it.url) {
                                    if (it.status != "completed" && it1.status == "completed") {
                                        bot.getGroup(it.groupId)?.sendMessage("扒源任务 链接：${it.url} 下载完成")
                                        completedTaskList.add(it.url)
                                    }
                                    return@breaking
                                }
                            }
                        }
                    }
                    delay(50)
                }
                completedTaskList.forEach {
                    val find = TaskData.task.find { it1 -> it1.url == it }
                    TaskData.task.removeAt(TaskData.task.lastIndexOf(find))
                }
                delay(60000)
                queryTask()
            } catch (ex: Exception) {
                delay(60000)
                queryTask()
            }
        }
    }

    /**
     * 转成bean
     *
     * @param content
     * @param cls
     * @return
     */
    private fun <T> fromJson(content: String?, cls: Class<T>?): T? {
        if (content.isNullOrEmpty()) {
            return null
        }
        return gson.fromJson(content, cls)
    }
}