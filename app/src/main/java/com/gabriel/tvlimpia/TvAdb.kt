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

    private var lastIp: String? = null

    private fun openOnce(ip: String): Dadb {
        val d = Dadb.create(ip, 5555, keyPair())
        try {
            val r = d.shell("echo tvlimpia")
            if (!r.output.contains("tvlimpia")) error("La TV no respondió")
            return d
        } catch (e: Exception) {
            try { d.close() } catch (_: Exception) { }
            throw e
        }
    }

    /**
     * La primera vez la TV muestra «¿Permitir depuración?» y a veces corta la conexión
     * mientras esperas (Broken pipe). Reintentamos durante ~40 s para darte tiempo a aceptar.
     */
    fun connect(ip: String, onRetry: (Int) -> Unit = {}) {
        close()
        var last: Exception? = null
        for (i in 1..20) {
            try {
                dadb = openOnce(ip)
                lastIp = ip
                return
            } catch (e: Exception) {
                last = e
                onRetry(i)
                Thread.sleep(2000)
            }
        }
        throw last ?: IllegalStateException("No se pudo conectar")
    }

    /** Si la conexión se cae (TV en reposo, Wi-Fi), reconecta una vez y repite la orden. */
    fun sh(cmd: String): String {
        val d = dadb ?: error("No hay ninguna TV conectada")
        return try {
            val r = d.shell(cmd)
            r.output + r.errorOutput
        } catch (e: Exception) {
            val ip = lastIp ?: throw e
            try { d.close() } catch (_: Exception) { }
            val nd = openOnce(ip)
            dadb = nd
            val r = nd.shell(cmd)
            r.output + r.errorOutput
        }
    }

    /**
     * Tareas largas (compilar apps): se lanzan EN LA TV en segundo plano y aquí solo
     * consultamos cómo van cada pocos segundos. Si el móvil pierde la conexión
     * (pantalla apagada, Wi-Fi), la TV sigue trabajando y al volver se reanuda el seguimiento.
     * El script no debe contener comillas simples.
     */
    fun runLong(script: String, tag: String, timeoutMin: Int = 40, onProgress: (String) -> Unit): String {
        val log = "/data/local/tmp/tvl_$tag.log"
        sh("rm -f $log; setsid nohup sh -c '$script; echo TVL_DONE' > $log 2>&1 < /dev/null &")
        val end = System.currentTimeMillis() + timeoutMin * 60_000L
        var fails = 0
        while (System.currentTimeMillis() < end) {
            Thread.sleep(4000)
            val out = try {
                sh("cat $log").also { fails = 0 }
            } catch (e: Exception) {
                fails++
                onProgress("Reconectando con la TV… (la TV sigue trabajando)")
                if (fails > 30) throw e
                continue
            }
            onProgress(out)
            if (out.contains("TVL_DONE")) return out
        }
        error("La tarea tardó demasiado")
    }

    fun close() {
        try { dadb?.close() } catch (_: Exception) { }
        dadb = null
        lastIp = null
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
