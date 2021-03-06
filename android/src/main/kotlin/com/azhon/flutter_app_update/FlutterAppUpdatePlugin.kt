package com.azhon.flutter_app_update

import android.app.Activity
import android.content.Context
import android.text.TextUtils
import androidx.annotation.NonNull
import com.azhon.appupdate.config.UpdateConfiguration
import com.azhon.appupdate.listener.OnButtonClickListener
import com.azhon.appupdate.listener.OnDownloadListener
import com.azhon.appupdate.manager.DownloadManager
import com.azhon.appupdate.utils.ApkUtil
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import org.json.JSONObject
import java.io.File
import java.lang.Exception

/**
 *版本更新插件
 */
class FlutterAppUpdatePlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
        EventChannel.StreamHandler {

    private lateinit var channel: MethodChannel
    private lateinit var applicationContext: Context
    private lateinit var activity: Activity
    private var events: EventChannel.EventSink? = null
    private var manager: DownloadManager? = null

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "azhon_app_update")
        channel.setMethodCallHandler(this)
        val eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "azhon_app_update_listener")
        eventChannel.setStreamHandler(this)
        applicationContext = flutterPluginBinding.applicationContext
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.activity = binding.activity
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        if (events != null) {
            this.events = events
        }
    }

    override fun onCancel(arguments: Any?) {

    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            Constants.GET_VERSION_CODE_METHOD -> {
                getVersionCode(result)
            }
            Constants.GET_VERSION_NAME_METHOD -> {
                getVersionName(result)
            }
            Constants.UPDATE_METHOD -> {
                update(call, result)
            }
            Constants.CANCEL_METHOD -> {
                cancel(result)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun getVersionCode(result: Result) {
        val versionCode = ApkUtil.getVersionCode(applicationContext)
        result.success(versionCode)
    }

    private fun getVersionName(result: Result) {
        val versionName = ApkUtil.getVersionName(applicationContext)
        result.success(versionName)
    }

    /**
     * 判断参数是否为空来设置
     */
    private fun update(call: MethodCall, result: Result) {
        val model = call.argument<HashMap<String, Any>>("model")
        //释放之前的
        manager?.release()
        manager = DownloadManager.getInstance(activity)
        //获取图标
        val smallIcon = applicationContext.resources.getIdentifier(model!!["smallIcon"] as String,
                "mipmap", applicationContext.packageName)
        manager?.apkName = model["apkName"] as String
        manager?.apkUrl = model["apkUrl"] as String
        manager?.smallIcon = smallIcon

        if (notNull(model, "apkVersionCode")) {
            manager?.apkVersionCode = model["apkVersionCode"] as Int
        }
        if (notNull(model, "apkVersionName")) {
            manager?.apkVersionName = model["apkVersionName"] as String
        }
        if (notNull(model, "apkDescription")) {
            manager?.apkDescription = model["apkDescription"] as String
        }
        if (notNull(model, "apkSize")) {
            manager?.apkSize = model["apkSize"] as String
        }
        if (notNull(model, "apkMD5")) {
            manager?.apkMD5 = model["apkMD5"] as String
        }
        manager?.isShowNewerToast = model["showNewerToast"] as Boolean

        val config = UpdateConfiguration()
        config.isShowNotification = model["showNotification"] as Boolean
        config.isJumpInstallPage = model["jumpInstallPage"] as Boolean
        config.isShowBgdToast = model["showBgdToast"] as Boolean
        config.isForcedUpgrade = model["forcedUpgrade"] as Boolean
        config.isUsePlatform = model["usePlatform"] as Boolean
        config.setOnDownloadListener(downloadListener)
        config.setButtonClickListener(buttonListener)
        manager?.configuration = config
        manager?.download()

        result.success(true)
    }

    private fun cancel(result: Result) {
        manager?.cancel()
        result.success(true)
    }

    //判断是否为空
    private fun notNull(model: HashMap<String, Any>, key: String): Boolean {
        if (model[key] is String) {
            return !TextUtils.isEmpty(model[key].toString())
        }
        return model[key] != null
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onDetachedFromActivity() {
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    }

    override fun onDetachedFromActivityForConfigChanges() {
    }

    private var downloadListener: OnDownloadListener = object : OnDownloadListener {
        override fun start() {
            events?.success(json("start").toString())
        }

        override fun downloading(max: Int, progress: Int) {
            val json = json("downloading")
            json.put("max", max)
            json.put("progress", progress)
            events?.success(json.toString())
        }

        override fun done(apk: File?) {
            val json = json("done")
            json.put("apk", apk?.path)
            events?.success(json.toString())
        }

        override fun cancel() {
            events?.success(json("cancel").toString())
        }

        override fun error(e: Exception?) {
            val json = json("error")
            json.put("exception", e?.message)
            events?.success(json.toString())
        }
    }
    private var buttonListener: OnButtonClickListener = OnButtonClickListener { id ->
        val json = json("onButtonClick")
        json.put("id", id)
        events?.success(json.toString())
    }

    private fun json(type: String): JSONObject {
        val json = JSONObject()
        json.put("type", type)
        return json
    }
}
