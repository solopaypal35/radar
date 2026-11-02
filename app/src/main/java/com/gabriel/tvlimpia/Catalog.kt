package com.gabriel.tvlimpia

enum class Group(val title: String) {
    RECOMMENDED("Recomendadas"),
    MINE("Tus apps (no se tocan)"),
    REVIEW("Para revisar"),
    PROTECTED("Protegidas"),
}

data class AppEntry(
    val pkg: String,
    val name: String,
    val note: String,
    val group: Group,
    val disabled: Boolean,
    val installedByUser: Boolean,
)

/**
 * Decide qué se puede tocar.
 * Regla de oro: ante la duda, PROTEGIDA. Es mejor dejar una app inútil que dejar la TV sin imagen.
 */
object Catalog {
    private val validPkg = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")

    /** Lista segura: se marcan solas. */
    private val recommended: Map<String, Pair<String, String>> = mapOf(
        "com.google.android.youtube.tvmusic" to ("YouTube Music" to "Música de YouTube. Prescindible si no la usas."),
        "com.google.android.play.games" to ("Google Play Juegos" to "Juegos de Google; casi nadie la usa en la TV."),
        "cm.aptoidetv.pt" to ("Aptoide TV" to "Tienda de apps alternativa preinstalada."),
        "com.google.android.feedback" to ("Comentarios de Google" to "Envía informes a Google."),
        "com.android.printspooler" to ("Cola de impresión" to "Servicio de impresión; inútil en una TV."),
        "com.android.bips" to ("Impresión predeterminada" to "Servicio de impresión; inútil en una TV."),
        "com.android.dreams.basic" to ("Salvapantallas básico" to "Salvapantallas antiguo de Android."),
        "com.android.dreams.phototable" to ("Salvapantallas de fotos" to "Salvapantallas antiguo de Android."),
        // --- TCL (comprobadas en una TCL Google TV, Android 11) ---
        "com.tcl.channelplus" to ("TCL Channel" to "Canales con anuncios de TCL; se carga en segundo plano."),
        "com.tcl.esticker" to ("TCL Stickers" to "Pegatinas y avisos promocionales sobre la imagen."),
        "com.tcl.messagebox" to ("Mensajes de TCL" to "Notificaciones y promociones de TCL."),
        "com.tcl.guard" to ("TV Guard" to "«Limpiador» de TCL que corre en segundo plano."),
        "com.tcl.gallery" to ("Galería de TCL" to "Visor de fotos; prescindible."),
        "com.tcl.notereminder" to ("Recordatorios de TCL" to "Avisos de programas; casi nadie lo usa."),
        "com.tcl.hotelmenu" to ("Menú de hotel" to "Modo para hoteles; inútil en casa."),
        "com.tcl.usercenter" to ("Cuenta de TCL" to "Registro y cuenta de TCL."),
        "com.tcl.smartalexa" to ("Alexa de TCL" to "Integración con Alexa; prescindible si no la usas."),
        "com.tcl.MultiScreenInteraction_TV" to ("TCL T-Cast" to "Enviar contenido desde la app de TCL. No afecta a Chromecast."),
        "com.google.android.marvin.talkback" to ("TalkBack" to "Lector de pantalla para personas ciegas."),
        "com.google.android.syncadapters.calendar" to ("Sincronizar calendario" to "Sincroniza Google Calendar; inútil en la TV."),
        "au.com.stan.and" to ("Stan" to "Plataforma australiana; no funciona en España."),
        "tv.wuaki.apptv" to ("Rakuten TV" to "Preinstalada; prescindible si no la usas."),
    )

    private val friendly = mapOf(
        "rtve.tablet.android" to "RTVE Play",
        "tv.accedo.mitele_xdk" to "Mitele",
        "cat.ccma.androidtv.tv3" to "3Cat (TV3)",
        "com.esaba.downloader" to "Downloader",
        "com.tcl.waterfall.overseas" to "Contenidos de TCL (inicio)",
        "com.tcl.ttvs" to "Servicio de TCL",
        "com.tcl.suspension" to "Ventana flotante de TCL",
        "com.tcl.t_solo" to "TCL T-Solo",
        "com.tcl.micmanager" to "Micrófono del mando (TCL)",
        "com.google.android.tts" to "Síntesis de voz de Google",
        "com.google.android.apps.nbu.smartconnect.tv" to "Conexión con el móvil (Google)",
        "com.netflix.ninja" to "Netflix",
        "com.amazon.amazonvideo.livingroom" to "Prime Video",
        "com.disney.disneyplus" to "Disney+",
        "com.google.android.youtube.tv" to "YouTube",
        "com.spotify.tv.android" to "Spotify",
    )

    private val protectedExact = setOf(
        "android",
        "com.tcl.tv",
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.videos",            // base del inicio de Google TV
        "com.google.android.katniss",           // búsqueda y voz
        "com.google.android.apps.mediashell",   // Chromecast integrado
        "com.google.android.backdrop",
        "com.google.android.tvlauncher",
        "com.google.android.leanbacklauncher",
        "com.google.android.tvrecommendations",
        "com.google.android.tungsten.setupwraith",
        "com.google.android.inputmethod.latin",
        // TCL / Google que conviene no tocar en este modelo
        "com.tcl.autopair",                    // emparejar el mando
        "com.tcl.keyhelp",                     // teclas del mando
        "com.tcl.partnercustomizer",
        "com.tcl.useragreement",
        "com.tvos",
        "com.google.android.modulemetadata",
        "com.google.android.onetimeinitializer",
        "com.google.android.sss.authbridge",
    )

    private val protectedPrefixes = listOf(
        "com.android.", "com.google.android.tv", "com.google.android.ext.",
        "com.google.android.gms", "com.google.android.apps.tv",
        "com.mediatek", "com.realtek", "com.amlogic", "com.droidlogic",
    )

    private val protectedWords = listOf(
        "launcher", "setting", "input", "hdmi", "systemui", "install", "permission",
        "provider", "keyboard", "update", "upgrade", "ota", "bluetooth", "remote",
        "wifi", "network", "setup", "webview", "media", "audio", "display", "picture",
        "sound", "cec", "service", "framework", "core", "system", "tuner", "livetv",
        "dashboard", "home", "overlay", "resolver", "backup", "storage", "location",
        "platform", "factory", "cast", "voice", "assist", "power", "boot",
    )

    fun groupOf(p: String): Group {
        if (p in recommended) return Group.RECOMMENDED
        if (p in protectedExact || protectedPrefixes.any { p.startsWith(it) }) return Group.PROTECTED
        val l = p.lowercase()
        if (protectedWords.any { l.contains(it) }) return Group.PROTECTED
        return Group.REVIEW
    }

    fun build(systemOut: String, userOut: String, disabledOut: String, self: String): List<AppEntry> {
        val user = parse(userOut)
        val disabled = parse(disabledOut)
        return (parse(systemOut) + user)
            .filter { it != self }
            .map { p ->
                // Lo que instalaste tú (tus APK) nunca se toca, aunque esté en la lista segura
                val g = if (p in user) Group.MINE else groupOf(p)
                val rec = recommended[p]
                val name = rec?.first ?: friendly[p] ?: guessName(p)
                val note = when {
                    p in user -> "Instalada por ti: se mantiene siempre."
                    rec != null -> rec.second
                    g == Group.REVIEW && friendly[p] == null -> "Desconocida: mejor no tocarla."
                    else -> ""
                }
                AppEntry(p, name, note, g, p in disabled, p in user)
            }
            .sortedWith(compareBy({ it.group.ordinal }, { it.name.lowercase() }))
    }

    private val generic = setOf("com", "org", "net", "tv", "app", "apps", "android", "androidtv", "apptv", "and", "tablet", "mobile", "overseas", "global")

    private fun guessName(p: String): String {
        val parts = p.split('.').filter { it.lowercase() !in generic }
        val n = parts.lastOrNull() ?: p.substringAfterLast('.')
        return n.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    private fun parse(out: String): Set<String> = out.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("package:") }
        .map { it.removePrefix("package:").trim() }
        .filter { validPkg.matches(it) }
        .toSet()
}
