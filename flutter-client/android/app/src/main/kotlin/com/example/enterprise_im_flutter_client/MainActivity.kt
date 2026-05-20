package com.example.enterprise_im_flutter_client

import android.Manifest
import android.content.pm.PackageManager
import android.net.sip.SipAudioCall
import android.net.sip.SipException
import android.net.sip.SipManager
import android.net.sip.SipProfile
import android.net.Uri
import android.os.Build
import android.content.Context
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory
import org.pjsip.pjsua2.Account
import org.pjsip.pjsua2.AccountConfig
import org.pjsip.pjsua2.AudioMedia
import org.pjsip.pjsua2.AuthCredInfo
import org.pjsip.pjsua2.AuthCredInfoVector
import org.pjsip.pjsua2.Call
import org.pjsip.pjsua2.CallInfo
import org.pjsip.pjsua2.CallMediaInfo
import org.pjsip.pjsua2.CallOpParam
import org.pjsip.pjsua2.Endpoint
import org.pjsip.pjsua2.EpConfig
import org.pjsip.pjsua2.OnCallMediaStateParam
import org.pjsip.pjsua2.OnCallStateParam
import org.pjsip.pjsua2.OnIncomingCallParam
import org.pjsip.pjsua2.OnRegStateParam
import org.pjsip.pjsua2.TransportConfig
import org.pjsip.pjsua2.VideoWindow
import org.pjsip.pjsua2.VideoWindowHandle
import org.pjsip.pjsua2.WindowHandle
import org.pjsip.pjsua2.pjmedia_type
import org.pjsip.pjsua2.pjsip_status_code
import org.pjsip.pjsua2.pjsip_transport_type_e
import org.pjsip.pjsua2.pjsua_call_media_status

class MainActivity : FlutterActivity() {
    private val channelName = "enterprise_im/sip"
    private var sipChannel: MethodChannel? = null
    private var activeCallId: String? = null
    private var activeMediaType: String = "audio"
    private var sipManager: SipManager? = null
    private var localProfile: SipProfile? = null
    private var audioCall: SipAudioCall? = null
    private var pjsipEndpoint: Endpoint? = null
    private var pjsipAccount: NativeAccount? = null
    private var pjsipCall: NativeCall? = null
    private var remoteVideoSurface: Surface? = null
    private var remoteVideoWindow: VideoWindow? = null
    private val nativePjsipAvailable: Boolean by lazy { loadNativePjsip() }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        flutterEngine
            .platformViewsController
            .registry
            .registerViewFactory("enterprise_im/pjsip_video_view", PjsipVideoViewFactory(this))
        sipChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
        sipChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "start" -> {
                    val args = call.arguments as? Map<*, *>
                    val callId = args?.get("callId")?.toString().orEmpty()
                    val registrar = args?.get("sipRegistrar")?.toString().orEmpty()
                    val username = args?.get("sipUsername")?.toString().orEmpty()
                    val password = args?.get("sipPassword")?.toString().orEmpty()
                    val calleeUri = args?.get("calleeSipUri")?.toString().orEmpty()
                    val mediaType = args?.get("mediaType")?.toString().orEmpty().ifBlank { "audio" }
                    val outbound = args?.get("outbound") as? Boolean ?: true
                    if (callId.isBlank() || registrar.isBlank() || username.isBlank()) {
                        result.error("invalid_config", "callId, sipRegistrar, and sipUsername are required", null)
                        return@setMethodCallHandler
                    }
                    val startResult = startSipCall(callId, registrar, username, password, calleeUri, mediaType, outbound)
                    if (startResult["status"] == "error") {
                        result.error(startResult["code"]?.toString() ?: "sip_error", startResult["message"]?.toString(), startResult)
                    } else {
                        result.success(startResult)
                    }
                }
                "stop" -> result.success(stopSipCall())
                else -> result.notImplemented()
            }
        }
    }

    private fun startSipCall(
        callId: String,
        registrar: String,
        username: String,
        password: String,
        calleeUri: String,
        mediaType: String,
        outbound: Boolean
    ): Map<String, Any?> {
        if (nativePjsipAvailable) {
            return startPjsua2Call(callId, registrar, username, password, calleeUri, mediaType, outbound)
        }
        if (mediaType == "video") {
            activeCallId = callId
            return mapOf(
                "status" to "bridge_ready",
                "callId" to callId,
                "registrar" to registrar,
                "username" to username,
                "calleeSipUri" to calleeUri,
                "mediaType" to mediaType,
                "nativeEngine" to "android-sip-unavailable",
                "message" to "Android platform SIP supports audio only here; pjsua2 is required for video."
            )
        }
        if (!SipManager.isApiSupported(this) || !SipManager.isVoipSupported(this)) {
            activeCallId = callId
            return mapOf(
                "status" to "bridge_ready",
                "callId" to callId,
                "registrar" to registrar,
                "username" to username,
                "calleeSipUri" to calleeUri,
                "mediaType" to mediaType,
                "nativeEngine" to "android-sip-unavailable",
                "message" to "Current device does not support Android platform SIP/VoIP; bundle pjsua2 for native media."
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 7101)
            return mapOf("status" to "permission_required", "callId" to callId, "nativeEngine" to "android-sip")
        }

        val target = parseSipTarget(registrar)
        if (target.host.isBlank()) {
            return mapOf("status" to "error", "code" to "invalid_registrar", "message" to "Invalid SIP registrar: $registrar")
        }
        val remote = if (calleeUri.isBlank()) null else calleeUri

        return try {
            stopSipCall()
            val manager = SipManager.newInstance(this)
            val profileBuilder = SipProfile.Builder(username, target.domain)
                .setPassword(password)
                .setAuthUserName(username)
                .setPort(target.port)
                .setProtocol("UDP")
            if (target.host != target.domain) {
                profileBuilder.setOutboundProxy(target.host)
            }
            val profile = profileBuilder.build()
            manager.open(profile)
            sipManager = manager
            localProfile = profile
            activeCallId = callId

            if (remote != null) {
                audioCall = manager.makeAudioCall(profile.uriString, remote, object : SipAudioCall.Listener() {
                    override fun onCallEstablished(call: SipAudioCall?) {
                        call?.startAudio()
                        call?.setSpeakerMode(true)
                    }
                }, 30)
            }

            mapOf(
                "status" to if (remote == null) "registered" else "dialing",
                "callId" to callId,
                "registrar" to registrar,
                "username" to username,
                "localUri" to profile.uriString,
                "calleeSipUri" to remote,
                "nativeEngine" to "android-sip"
            )
        } catch (error: SipException) {
            mapOf("status" to "error", "code" to "sip_exception", "message" to error.message)
        } catch (error: Exception) {
            mapOf("status" to "error", "code" to "native_exception", "message" to error.message)
        }
    }

    private fun startPjsua2Call(
        callId: String,
        registrar: String,
        username: String,
        password: String,
        calleeUri: String,
        mediaType: String,
        outbound: Boolean
    ): Map<String, Any?> {
        val target = parseSipTarget(registrar)
        if (target.host.isBlank()) {
            return mapOf("status" to "error", "code" to "invalid_registrar", "message" to "Invalid SIP registrar: $registrar")
        }
        if (outbound && calleeUri.isBlank()) {
            return mapOf("status" to "error", "code" to "invalid_callee", "message" to "calleeSipUri is required")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            val permissions = if (mediaType == "video") {
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            } else {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            }
            requestPermissions(permissions, 7102)
            return mapOf("status" to "permission_required", "callId" to callId, "nativeEngine" to "pjsua2", "mediaType" to mediaType)
        }
        if (mediaType == "video" &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA), 7102)
            return mapOf("status" to "permission_required", "callId" to callId, "nativeEngine" to "pjsua2", "mediaType" to mediaType)
        }
        return try {
            stopPjsua2()
            activeCallId = callId
            activeMediaType = mediaType
            val endpoint = Endpoint()
            endpoint.libCreate()
            val epConfig = EpConfig()
            epConfig.logConfig.level = 4
            epConfig.logConfig.consoleLevel = 4
            endpoint.libInit(epConfig)
            endpoint.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_UDP, TransportConfig())
            endpoint.libStart()
            if (!endpoint.libIsThreadRegistered()) {
                endpoint.libRegisterThread("flutter-sip")
            }

            val accountConfig = AccountConfig()
            accountConfig.idUri = "sip:$username@${target.domain}"
            accountConfig.regConfig.registrarUri = registrar
            accountConfig.regConfig.registerOnAdd = true
            accountConfig.videoConfig.autoShowIncoming = true
            accountConfig.videoConfig.autoTransmitOutgoing = true
            val creds = AuthCredInfoVector()
            creds.add(AuthCredInfo("digest", "*", username, 0, password))
            accountConfig.sipConfig.authCreds = creds

            val account = NativeAccount()
            account.create(accountConfig)
            waitForRegistration(account)
            val nativeCall = if (outbound) {
                val call = NativeCall(account)
                val param = CallOpParam(true)
                param.opt.audioCount = 1
                param.opt.videoCount = if (mediaType == "video") 1 else 0
                call.makeCall(calleeUri, param)
                call
            } else {
                null
            }

            pjsipEndpoint = endpoint
            pjsipAccount = account
            pjsipCall = nativeCall
            emitSipEvent("state", if (outbound) "dialing" else "listening")
            attachRemoteVideoWindow()
            mapOf(
                "status" to if (outbound) "dialing" else "listening",
                "callId" to callId,
                "registrar" to registrar,
                "username" to username,
                "calleeSipUri" to calleeUri,
                "mediaType" to mediaType,
                "outbound" to outbound,
                "nativeEngine" to "pjsua2",
                "message" to if (outbound) "PJSIP pjsua2 outbound call started" else "PJSIP pjsua2 incoming listener started"
            )
        } catch (error: Exception) {
            emitSipEvent("error", error.message ?: "pjsua2 exception")
            stopPjsua2()
            mapOf("status" to "error", "code" to "pjsua2_exception", "message" to error.message)
        }
    }

    private fun stopSipCall(): Map<String, Any?> {
        val stopped = activeCallId
        stopPjsua2()
        try {
            audioCall?.close()
            audioCall = null
            localProfile?.let { profile ->
                sipManager?.close(profile.uriString)
            }
        } catch (_: Exception) {
        } finally {
            localProfile = null
            sipManager = null
            activeCallId = null
            activeMediaType = "audio"
        }
        return mapOf(
            "status" to "stopped",
            "callId" to stopped,
            "nativeEngine" to if (nativePjsipAvailable) "pjsua2" else "android-sip"
        )
    }

    private fun stopPjsua2() {
        try {
            pjsipCall?.hangup(CallOpParam().apply { statusCode = pjsip_status_code.PJSIP_SC_DECLINE })
        } catch (_: Exception) {
        }
        try {
            pjsipAccount?.shutdown()
        } catch (_: Exception) {
        }
        try {
            pjsipEndpoint?.hangupAllCalls()
            pjsipEndpoint?.libDestroy()
        } catch (_: Exception) {
        } finally {
            pjsipCall = null
            pjsipAccount = null
            pjsipEndpoint = null
            remoteVideoWindow = null
        }
    }

    private fun loadNativePjsip(): Boolean {
        return try {
            System.loadLibrary("c++_shared")
            System.loadLibrary("pjsua2")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private inner class NativeAccount : Account() {
        override fun onRegState(prm: OnRegStateParam) {
            emitSipEvent("registration", "${prm.code} ${prm.reason}".trim())
        }

        override fun onIncomingCall(prm: OnIncomingCallParam) {
            try {
                val call = NativeCall(this, prm.callId)
                val answer = CallOpParam(true)
                answer.statusCode = pjsip_status_code.PJSIP_SC_OK
                answer.opt.audioCount = 1
                answer.opt.videoCount = if (activeMediaType == "video") 1 else 0
                call.answer(answer)
                pjsipCall = call
                emitSipEvent("incoming", "answered ${activeMediaType}")
            } catch (error: Exception) {
                emitSipEvent("error", "incoming answer failed: ${error.message}")
            }
        }
    }

    private inner class NativeCall(account: Account, callId: Int = -1) : Call(account, callId) {
        override fun onCallState(prm: OnCallStateParam) {
            try {
                val info: CallInfo = getInfo()
                val reason = info.lastReason ?: ""
                val status = info.lastStatusCode
                val state = info.stateText ?: "unknown"
                emitSipEvent("call", "$state $status $reason".trim())
            } catch (error: Exception) {
                emitSipEvent("error", "call state failed: ${error.message}")
            }
        }

        override fun onCallMediaState(prm: OnCallMediaStateParam) {
            try {
                val info: CallInfo = getInfo()
                for (mediaInfo: CallMediaInfo in info.media) {
                    if (mediaInfo.type == pjmedia_type.PJMEDIA_TYPE_AUDIO &&
                        mediaInfo.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE
                    ) {
                        val audioMedia = AudioMedia.typecastFromMedia(getMedia(mediaInfo.index))
                        val manager = pjsipEndpoint?.audDevManager() ?: return
                        audioMedia.startTransmit(manager.playbackDevMedia)
                        manager.captureDevMedia.startTransmit(audioMedia)
                        emitSipEvent("media", "audio active")
                    }
                    if (mediaInfo.type == pjmedia_type.PJMEDIA_TYPE_VIDEO &&
                        mediaInfo.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE
                    ) {
                        remoteVideoWindow = mediaInfo.videoWindow ?: VideoWindow(mediaInfo.videoIncomingWindowId)
                        emitSipEvent("media", "video active")
                        attachRemoteVideoWindow()
                    }
                }
            } catch (error: Exception) {
                emitSipEvent("error", "media state failed: ${error.message}")
            }
        }
    }

    private fun setRemoteVideoSurface(surface: Surface?) {
        remoteVideoSurface = surface
        attachRemoteVideoWindow()
    }

    private fun attachRemoteVideoWindow() {
        val surface = remoteVideoSurface ?: return
        val videoWindow = remoteVideoWindow ?: return
        try {
            val windowHandle = WindowHandle()
            windowHandle.setWindow(surface)
            val videoWindowHandle = VideoWindowHandle()
            videoWindowHandle.setHandle(windowHandle)
            videoWindow.setWindow(videoWindowHandle)
            videoWindow.Show(true)
            emitSipEvent("video", "remote window attached")
        } catch (error: Exception) {
            emitSipEvent("error", "remote video attach failed: ${error.message}")
        }
    }

    private fun waitForRegistration(account: Account) {
        val deadline = System.currentTimeMillis() + 3500
        var lastStatus = 0
        var lastStatusText = ""
        while (System.currentTimeMillis() < deadline) {
            val info = account.getInfo()
            lastStatus = info.regStatus
            lastStatusText = info.regStatusText ?: ""
            if (lastStatus == pjsip_status_code.PJSIP_SC_OK) {
                emitSipEvent("registration", "200 OK")
                return
            }
            if (lastStatus >= 300) {
                throw IllegalStateException("SIP registration failed: $lastStatus $lastStatusText")
            }
            Thread.sleep(100)
        }
        throw IllegalStateException("SIP registration timeout: $lastStatus $lastStatusText")
    }

    private fun emitSipEvent(type: String, message: String) {
        runOnUiThread {
            sipChannel?.invokeMethod(
                "sipEvent",
                mapOf(
                    "type" to type,
                    "message" to message,
                    "callId" to activeCallId,
                    "mediaType" to activeMediaType
                )
            )
        }
    }

    private inner class PjsipVideoViewFactory(private val activity: MainActivity) :
        PlatformViewFactory(StandardMessageCodec.INSTANCE) {
        override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
            return PjsipVideoPlatformView(context, activity)
        }
    }

    private class PjsipVideoPlatformView(context: Context, private val activity: MainActivity) :
        PlatformView, SurfaceHolder.Callback {
        private val view = SurfaceView(context)

        init {
            view.holder.addCallback(this)
        }

        override fun getView(): View = view

        override fun dispose() {
            view.holder.removeCallback(this)
            activity.setRemoteVideoSurface(null)
        }

        override fun surfaceCreated(holder: SurfaceHolder) {
            activity.setRemoteVideoSurface(holder.surface)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            activity.setRemoteVideoSurface(holder.surface)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            activity.setRemoteVideoSurface(null)
        }
    }

    private fun parseSipTarget(registrar: String): SipTarget {
        val rawTarget = registrar
            .trim()
            .removePrefix("sip:")
            .removePrefix("sips:")
            .substringBefore(';')
            .substringBefore('?')
            .substringAfter('@')
        val hostPort = rawTarget.removePrefix("//")
        val host = hostPort.substringBefore(':').trim()
        val parsedPort = hostPort.substringAfter(':', "").toIntOrNull()
        val port = parsedPort?.takeIf { it > 0 } ?: 5060
        val domain = if (host == "127.0.0.1" || host == "localhost") "enterprise-im.local" else host
        return SipTarget(host, domain, port)
    }

    private data class SipTarget(val host: String, val domain: String, val port: Int)
}
