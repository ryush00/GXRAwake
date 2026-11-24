package com.gxrawake.app.setup

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.gxrawake.app.log.EventLogStore
import com.gxrawake.app.service.AccessibilityStateManager
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.android.AdbMdns
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Discovers local Wireless Debugging pairing/connect ports via Android mDNS
 * and executes the one-time GXRAwake setup commands directly on-device.
 */
class WirelessSetupRunner(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onDiscoveryUpdated(pairingEndpoint: Endpoint?, connectEndpoint: Endpoint?)
        fun onProgress(message: String)
        fun onFinished(success: Boolean, message: String)
    }

    data class Endpoint(val host: String, val port: Int)

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)

    @Volatile
    private var discoveredPairing: Endpoint? = null

    @Volatile
    private var discoveredConnect: Endpoint? = null

    private var pairingMdns: AdbMdns? = null
    private var connectMdns: AdbMdns? = null

    fun startDiscovery() {
        stopDiscovery()
        pairingMdns = AdbMdns(appContext, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { addr, port ->
            val ep = toEndpoint(addr, port)
            discoveredPairing = ep
            notifyDiscovery()
        }.apply { start() }

        connectMdns = AdbMdns(appContext, AdbMdns.SERVICE_TYPE_TLS_CONNECT) { addr, port ->
            val ep = toEndpoint(addr, port)
            discoveredConnect = ep
            notifyDiscovery()
        }.apply { start() }
    }

    fun stopDiscovery() {
        runCatching { pairingMdns?.stop() }
        runCatching { connectMdns?.stop() }
        pairingMdns = null
        connectMdns = null
    }

    fun isRunning(): Boolean = running.get()

    fun runOneTapSetup(pairingCodeInput: String, pairingPortInput: String) {
        if (!running.compareAndSet(false, true)) return

        val code = pairingCodeInput.trim()
        val explicitPort = pairingPortInput.trim().toIntOrNull()

        executor.execute {
            val manager = LocalAdbConnectionManager.getInstance(appContext)
            try {
                // Step 1: Pair if a 6-digit pairing code was provided
                if (code.isNotEmpty()) {
                    val pairingEp = discoveredPairing
                    val pairHost = pairingEp?.host ?: "127.0.0.1"
                    val pairPort = explicitPort ?: pairingEp?.port ?: -1
                    if (pairPort !in 1..65535) {
                        finish(
                            false,
                            "페어링 포트를 찾을 수 없습니다. '페어링 코드로 기기 페어링' 창을 열어두거나 포트를 직접 입력해 주세요."
                        )
                        return@execute
                    }

                    postProgress("무선 디버깅 페어링 중... ($pairHost:$pairPort)")
                    EventLogStore.append(appContext, "Wireless ADB pairing started ($pairHost:$pairPort).")
                    val paired = manager.pair(pairHost, pairPort, code)
                    if (!paired) {
                        finish(false, "페어링에 실패했습니다. 6자리 페어링 코드와 포트를 다시 확인해 주세요.")
                        return@execute
                    }
                    EventLogStore.append(appContext, "Wireless ADB pairing succeeded.")
                    // Give adbd a short moment to expose the connect endpoint after pairing
                    Thread.sleep(600L)
                }

                // Step 2: Connect to wireless ADB daemon
                postProgress("로컬 무선 ADB 연결 중...")
                val connected = connectToLocalAdb(manager)
                if (!connected) {
                    finish(
                        false,
                        "무선 디버깅에 연결할 수 없습니다. 개발자 옵션에서 '무선 디버깅'이 켜져 있는지 확인해 주세요."
                    )
                    return@execute
                }
                EventLogStore.append(appContext, "Wireless ADB connected.")

                // Step 3: Execute one-time setup commands via ADB shell
                postProgress("권한 부여 및 배터리 최적화 제외 적용 중...")
                val pkg = appContext.packageName
                val setupCmd = "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS; " +
                    "dumpsys deviceidle whitelist +$pkg"
                val shellOutput = runShellCommand(manager, setupCmd)
                EventLogStore.append(
                    appContext,
                    "Wireless setup shell finished: ${shellOutput.ifBlank { "OK" }}"
                )

                // Step 4: Enable AccessibilityService via the newly granted WRITE_SECURE_SETTINGS
                postProgress("접근성 서비스 활성화 및 상태 확인 중...")
                val restoreResult = AccessibilityStateManager.ensureServiceEnabled(
                    appContext,
                    "wireless-one-tap-setup"
                )

                val hasSecurePerm = AccessibilityStateManager.hasWriteSecureSettingsPermission(appContext)
                val serviceEnabled = AccessibilityStateManager.isServiceEnabledInSettings(appContext)
                val batteryOk = appContext.getSystemService(PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(pkg) == true

                if (hasSecurePerm && serviceEnabled) {
                    val detail = if (batteryOk) {
                        "설정 완료! 재부팅 후에도 자동으로 복구됩니다."
                    } else {
                        "권한 및 접근성 활성화 완료! (배터리 최적화 제외는 필요 시 설정에서 확인해 주세요.)"
                    }
                    finish(true, detail)
                } else {
                    finish(
                        false,
                        "명령 실행 후 확인 실패 (권한=$hasSecurePerm, 서비스=$serviceEnabled, 결과=$restoreResult)"
                    )
                }
            } catch (e: AdbPairingRequiredException) {
                EventLogStore.append(appContext, "Wireless setup requires pairing: ${e.message}")
                finish(false, "페어링이 필요합니다. '페어링 코드로 기기 페어링'의 6자리 코드를 입력해 주세요.")
            } catch (e: Exception) {
                val rawMsg = e.message ?: e.javaClass.simpleName
                EventLogStore.append(appContext, "Wireless setup error: $rawMsg")
                val userMsg = if (rawMsg.contains("Could not exchange peer info", ignoreCase = true)) {
                    "페어링 코드가 일치하지 않습니다. 6자리 코드를 다시 확인해 주세요."
                } else {
                    "자동 설정 실패: $rawMsg"
                }
                finish(false, userMsg)
            } finally {
                runCatching { manager.disconnect() }
                running.set(false)
            }
        }
    }

    private fun connectToLocalAdb(manager: LocalAdbConnectionManager): Boolean {
        val connectEp = discoveredConnect
        if (connectEp != null) {
            try {
                if (manager.connect(connectEp.host, connectEp.port)) {
                    return true
                }
            } catch (e: AdbPairingRequiredException) {
                throw e
            } catch (_: Exception) {
                // Fall back to autoConnect mDNS discovery below
            }
        }
        return manager.autoConnect(appContext, 8_000L)
    }

    private fun runShellCommand(manager: LocalAdbConnectionManager, command: String): String {
        val sb = StringBuilder()
        manager.openStream("shell:$command").use { stream ->
            BufferedReader(InputStreamReader(stream.openInputStream())).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (sb.isNotEmpty()) sb.append(" | ")
                    sb.append(line)
                }
            }
        }
        return sb.toString().trim()
    }

    private fun toEndpoint(addr: InetAddress?, port: Int): Endpoint? {
        if (addr == null || port !in 1..65535) return null
        val host = addr.hostAddress ?: return null
        return Endpoint(host, port)
    }

    private fun notifyDiscovery() {
        val p = discoveredPairing
        val c = discoveredConnect
        mainHandler.post { listener.onDiscoveryUpdated(p, c) }
    }

    private fun postProgress(message: String) {
        mainHandler.post { listener.onProgress(message) }
    }

    private fun finish(success: Boolean, message: String) {
        mainHandler.post { listener.onFinished(success, message) }
    }
}
