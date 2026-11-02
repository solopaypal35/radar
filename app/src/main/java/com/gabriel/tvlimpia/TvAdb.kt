package com.gabriel.tvlimpia

import android.content.Context
import android.net.ConnectivityManager
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

/** Conexión ADB con la TV por la Wi-Fi (puerto 5555). */
class TvAdb(private val ctx: Context) {
    private var dadb: Dadb? = null

    /** Clave propia de la app: la TV la recuerda si marcas «Permitir siempre». */
    private fun keyPair(): AdbKeyPair {
        val priv = File(ctx.filesDir, "adbkey")
        val pub = File(ctx.filesDir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    fun connect(ip: String) {
        close()
        val d = Dadb.create(ip, 5555, keyPair())
        val r = d.shell("echo tvlimpia")
        if (!r.output.contains("tvlimpia")) {
            d.close()
            error("La TV no respondió")
        }
        dadb = d
    }

    fun sh(cmd: String): String {
        val r = (dadb ?: error("No hay ninguna TV conectada")).shell(cmd)
        return r.output + r.errorOutput
    }

    fun close() {
        try { dadb?.close() } catch (_: Exception) { }
        dadb = null
    }

    companion object {
        /** Todas las órdenes van por un único hilo, una detrás de otra. */
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    }
}

suspend fun <T> onAdb(block: () -> T): T = withContext(TvAdb.dispatcher) { block() }

/** Busca en la Wi-Fi aparatos con ADB por red abierto (puerto 5555). */
object Scanner {
    private fun localIp(ctx: Context): String? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return lp.linkAddresses.firstOrNull { it.address is Inet4Address }?.address?.hostAddress
    }

    suspend fun scan(ctx: Context): List<String> = coroutineScope {
        val me = localIp(ctx) ?: error("El móvil no está conectado a una Wi-Fi")
        val base = me.substringBeforeLast('.')
        (1..254).map { "$base.$it" }
            .filter { it != me }
            .map { host -> async(Dispatchers.IO) { if (portOpen(host)) host else null } }
            .awaitAll()
            .filterNotNull()
    }

    private fun portOpen(host: String): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, 5555), 600); true }
    } catch (_: Exception) {
        false
    }
}
