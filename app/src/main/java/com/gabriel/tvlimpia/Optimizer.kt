package com.gabriel.tvlimpia

/** Estado medible de la TV: para ver el antes y el después. */
data class Health(val ramTotalMb: Int, val ramFreeMb: Int, val diskUsedPct: Int) {
    fun text(): String =
        "RAM libre: $ramFreeMb de $ramTotalMb MB" +
            (if (diskUsedPct >= 0) " · Almacenamiento usado: $diskUsedPct%" else "")
}

/**
 * Optimización real sin root. Todo es reversible con undo().
 *  1. Animaciones a 0,5x: la TV responde antes al mando.
 *  2. Apps no esenciales sin permiso para correr en segundo plano: más RAM libre.
 *  3. Congelar apps en caché, sin escaneos Wi-Fi/Bluetooth continuos: menos CPU.
 *  4. Cerrar procesos de fondo y vaciar cachés.
 *  5. Compilar las apps (dexopt) para que abran más rápido.
 */
object Optimizer {
    private val settingsTouched = listOf(
        "window_animation_scale", "transition_animation_scale", "animator_duration_scale",
        "cached_apps_freezer", "wifi_scan_always_enabled", "ble_scan_always_enabled",
    )

    fun health(adb: TvAdb): Health {
        val mem = adb.sh("cat /proc/meminfo")
        fun kb(key: String): Long =
            Regex("^$key:\\s+(\\d+)", RegexOption.MULTILINE).find(mem)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val df = adb.sh("df /data")
        val pct = Regex("(\\d+)%").findAll(df).lastOrNull()?.groupValues?.get(1)?.toIntOrNull() ?: -1
        return Health((kb("MemTotal") / 1024).toInt(), (kb("MemAvailable") / 1024).toInt(), pct)
    }

    /** Valores originales de la TV, para poder dejarlo todo como estaba. */
    fun readOriginals(adb: TvAdb): Map<String, String> =
        settingsTouched.associateWith { adb.sh("settings get global $it").trim().ifBlank { "null" } }

    private fun ok(out: String) = !out.contains("rror") && !out.contains("xception")

    /** Devuelve los paquetes a los que se les ha quitado el segundo plano. */
    fun run(adb: TvAdb, restrict: List<String>, step: (String) -> Unit): List<String> {
        step("Acelerando animaciones…")
        listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
            .forEach { adb.sh("settings put global $it 0.5") }

        step("Quitando el segundo plano a ${restrict.size} apps no esenciales…")
        val restricted = restrict.filter { ok(adb.sh("cmd appops set $it RUN_ANY_IN_BACKGROUND ignore")) }

        step("Ajustes de rendimiento…")
        adb.sh("settings put global cached_apps_freezer enabled")
        adb.sh("settings put global wifi_scan_always_enabled 0")
        adb.sh("settings put global ble_scan_always_enabled 0")

        step("Cerrando procesos en segundo plano…")
        adb.sh("am kill-all")

        step("Vaciando cachés…")
        adb.sh("pm trim-caches 999G")

        step("Compilando apps para que abran más rápido.\nPuede tardar varios minutos: no apagues la TV ni cierres la app.")
        adb.runLong("cmd package bg-dexopt-job", "dexopt") { log ->
            if (log.startsWith("Reconectando")) step(log)
        }

        return restricted
    }

    fun undo(adb: TvAdb, restricted: Collection<String>, originals: Map<String, String>) {
        restricted.forEach { adb.sh("cmd appops set $it RUN_ANY_IN_BACKGROUND allow") }
        settingsTouched.forEach { key ->
            val v = originals[key] ?: "null"
            if (v == "null") adb.sh("settings delete global $key") else adb.sh("settings put global $key $v")
        }
    }

    /**
     * Compila del todo (modo «speed») las apps elegidas: abren y se mueven más fluidas.
     * No borra datos ni cierra sesión. Devuelve las que se han compilado bien.
     */
    fun boostApps(adb: TvAdb, pkgs: List<String>, step: (String) -> Unit): List<String> {
        val launcher = "com.google.android.apps.tv.launcherx"
        val script = "for p in ${(pkgs + launcher).joinToString(" ")}; do echo APP \$p; " +
            "cmd package compile -m speed -f \$p; done; am kill-all"
        val out = adb.runLong(script, "boost") { log ->
            val current = log.lines().lastOrNull { it.startsWith("APP ") }?.removePrefix("APP ")?.trim()
            val idx = (pkgs + launcher).indexOf(current)
            step(
                when {
                    log.startsWith("Reconectando") -> log
                    current == launcher -> "Acelerando el inicio de la TV…"
                    idx >= 0 -> "Acelerando app ${idx + 1} de ${pkgs.size}…\n$current\n" +
                        "La TV hace el trabajo: aunque el móvil se desconecte, sigue sola."
                    else -> "Empezando…"
                }
            )
        }
        // Qué apps terminaron con «Success»
        val ok = mutableListOf<String>()
        var current: String? = null
        out.lines().forEach { l ->
            if (l.startsWith("APP ")) current = l.removePrefix("APP ").trim()
            else if (l.contains("Success", true)) current?.let { if (it in pkgs) ok += it }
        }
        return ok.distinct()
    }

    fun unboostApps(adb: TvAdb, pkgs: Collection<String>) {
        (pkgs + "com.google.android.apps.tv.launcherx").forEach { adb.sh("cmd package compile --reset $it") }
    }

    fun encode(m: Map<String, String>) = m.entries.joinToString(";") { "${it.key}=${it.value}" }
    fun decode(s: String?): Map<String, String> = s.orEmpty().split(';').mapNotNull {
        val i = it.indexOf('=')
        if (i > 0) it.substring(0, i) to it.substring(i + 1) else null
    }.toMap()
}
