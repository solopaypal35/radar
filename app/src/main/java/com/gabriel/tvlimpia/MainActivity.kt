package com.gabriel.tvlimpia

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val act = this
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF4FD1C5),
                    secondary = Color(0xFFF6AD55),
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(act)
                }
            }
        }
    }
}

@Composable
fun App(act: ComponentActivity) {
    val ctx = act.applicationContext
    val adb = remember { TvAdb(ctx) }
    val prefs = remember { ctx.getSharedPreferences("tvlimpia", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf<String?>(null) }
    var tvInfo by remember { mutableStateOf("") }
    var found by remember { mutableStateOf(listOf<String>()) }
    var manualIp by remember { mutableStateOf(prefs.getString("last_ip", "") ?: "") }
    var apps by remember { mutableStateOf(listOf<AppEntry>()) }
    var showReview by remember { mutableStateOf(false) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var health by remember { mutableStateOf<Health?>(null) }
    // Apps que tú marcas como «Mantener»: nunca se desactivan ni se frenan
    val keep = remember {
        mutableStateMapOf<String, Boolean>().apply {
            (prefs.getStringSet("keep", emptySet()) ?: emptySet()).forEach { put(it, true) }
        }
    }

    fun toggleKeep(p: String) {
        val now = keep[p] != true
        keep[p] = now
        if (now) selected[p] = false
        prefs.edit().putStringSet("keep", keep.filterValues { it }.keys.toSet()).apply()
    }

    fun job(msg: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        status = msg
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                status = friendlyError(e)
            } finally {
                busy = false
            }
        }
    }

    fun ourSet(ip: String): Set<String> = prefs.getStringSet("byus_$ip", emptySet())?.toSet() ?: emptySet()

    suspend fun reload() {
        val list = onAdb {
            Catalog.build(
                adb.sh("pm list packages -s"),
                adb.sh("pm list packages -3"),
                adb.sh("pm list packages -d"),
                ctx.packageName,
            )
        }
        apps = list
        selected.clear()
        list.filter { it.group == Group.RECOMMENDED && !it.disabled && keep[it.pkg] != true }
            .forEach { selected[it.pkg] = true }
    }

    fun connect(ip: String) = job(
        "Conectando con $ip…\nSi en la TV aparece «¿Permitir depuración?», marca «Permitir siempre» y pulsa Aceptar."
    ) {
        val info = onAdb {
            adb.connect(ip) { n ->
                status = "Esperando a la TV (intento $n de 20)…\n" +
                    "Mira la TV: si aparece «¿Permitir depuración?», marca «Permitir siempre» y pulsa Aceptar."
            }
            adb.sh("getprop ro.product.model").trim() + " · Android " +
                adb.sh("getprop ro.build.version.release").trim()
        }
        prefs.edit().putString("last_ip", ip).apply()
        connected = ip
        tvInfo = info
        reload()
        health = onAdb { Optimizer.health(adb) }
        status = "Conectado. Revisa la lista y pulsa «Limpiar y optimizar»."
    }

    fun enable(pkgs: List<String>, msg: String) {
        val ip = connected ?: return
        job(msg) {
            val ok = mutableListOf<String>()
            onAdb {
                pkgs.forEach { p -> if (adb.sh("pm enable $p").contains("enabled", true)) ok += p }
            }
            prefs.edit().putStringSet("byus_$ip", ourSet(ip) - ok.toSet()).apply()
            reload()
            status = "Reactivadas: ${ok.size}."
        }
    }

    fun optimizeTargets(): List<String> = apps
        .filter { (it.group == Group.REVIEW || it.group == Group.RECOMMENDED) && !it.disabled && keep[it.pkg] != true }
        .map { it.pkg }

    suspend fun optimizeNow(ip: String) {
        if (prefs.getString("orig_$ip", null) == null) {
            val o = onAdb { Optimizer.readOriginals(adb) }
            prefs.edit().putString("orig_$ip", Optimizer.encode(o)).apply()
        }
        val targets = optimizeTargets()
        val restricted = onAdb { Optimizer.run(adb, targets) { s -> status = s } }
        val prev = prefs.getStringSet("restr_$ip", emptySet()) ?: emptySet()
        prefs.edit().putStringSet("restr_$ip", prev + restricted).apply()
    }

    fun cleanAndOptimize() {
        val ip = connected ?: return
        val pk = selected.filterValues { it }.keys.filter { keep[it] != true }
        job("Midiendo la TV…") {
            val before = onAdb { Optimizer.health(adb) }
            var msg = ""
            if (pk.isNotEmpty()) {
                status = "Desactivando ${pk.size} apps…"
                val done = mutableListOf<String>()
                onAdb {
                    pk.forEach { p ->
                        if (adb.sh("pm disable-user --user 0 $p").contains("disabled", true)) done += p
                    }
                }
                prefs.edit().putStringSet("byus_$ip", ourSet(ip) + done).apply()
                msg = "${done.size} apps desactivadas.\n"
                reload()
            }
            optimizeNow(ip)
            val after = onAdb { Optimizer.health(adb) }
            health = after
            status = "Hecho. " + msg +
                "Antes: ${before.text()}\nAhora: ${after.text()}\n\n" +
                "Reinicia la TV (botón «Reiniciar TV») para que todo haga efecto."
        }
    }

    fun optimizeAgain() {
        val ip = connected ?: return
        job("Optimizando…") {
            val before = onAdb { Optimizer.health(adb) }
            optimizeNow(ip)
            val after = onAdb { Optimizer.health(adb) }
            health = after
            status = "Hecho.\nAntes: ${before.text()}\nAhora: ${after.text()}"
        }
    }

    fun boostMine() {
        val ip = connected ?: return
        val mine = apps.filter { it.group == Group.MINE && !it.disabled }.map { it.pkg }
        if (mine.isEmpty()) { status = "No hay apps tuyas instaladas."; return }
        job("Preparando…") {
            val ok = onAdb { Optimizer.boostApps(adb, mine) { s -> status = s } }
            val prev = prefs.getStringSet("boost_$ip", emptySet()) ?: emptySet()
            prefs.edit().putStringSet("boost_$ip", prev + ok).apply()
            health = onAdb { Optimizer.health(adb) }
            status = "Hecho: ${ok.size} de ${mine.size} apps aceleradas. Tu usuario y contraseña siguen intactos.\n" +
                "Reinicia la TV y abre tu IPTV: los menús y el cambio de canal deberían ir más fluidos." +
                (if (ok.size < mine.size) "\nAlgunas no se pudieron compilar (pasa con apps muy protegidas); no les afecta." else "")
        }
    }

    fun undoOptimize() {
        val ip = connected ?: return
        job("Deshaciendo la optimización…") {
            val r = prefs.getStringSet("restr_$ip", emptySet()) ?: emptySet()
            val o = Optimizer.decode(prefs.getString("orig_$ip", null))
            val b = prefs.getStringSet("boost_$ip", emptySet()) ?: emptySet()
            onAdb {
                Optimizer.undo(adb, r, o)
                if (b.isNotEmpty()) Optimizer.unboostApps(adb, b)
            }
            prefs.edit().remove("restr_$ip").remove("orig_$ip").remove("boost_$ip").apply()
            status = "Optimización deshecha: ajustes como estaban. Las apps desactivadas se recuperan con «Restaurar todo»."
        }
    }

    fun rebootTv() = job("Reiniciando la TV…") {
        onAdb {
            try { adb.sh("reboot") } catch (_: Exception) { }
            adb.close()
        }
        connected = null
        apps = emptyList()
        selected.clear()
        health = null
        status = "La TV se está reiniciando. Cuando vuelva, pulsa «Buscar mi TV» otra vez (ya no pedirá permiso)."
    }

    fun export() {
        val text = buildString {
            appendLine("TV Limpia · $tvInfo")
            Group.values().forEach { g ->
                appendLine()
                appendLine("== ${g.title} ==")
                apps.filter { it.group == g }.forEach {
                    appendLine(it.pkg + (if (it.disabled) "  [desactivada]" else ""))
                }
            }
        }
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        act.startActivity(Intent.createChooser(send, "Exportar lista de apps"))
    }

    fun disconnect() = job("Desconectando…") {
        onAdb { adb.close() }
        connected = null
        apps = emptyList()
        selected.clear()
        status = ""
    }

    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        bottomBar = {
            if (connected != null) {
                val n = selected.count { it.value && keep[it.key] != true }
                Button(
                    onClick = { cleanAndOptimize() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(56.dp),
                ) {
                    Text(if (n > 0) "3 · Limpiar ($n) y optimizar" else "3 · Optimizar", fontSize = 18.sp)
                }
            }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).statusBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    Text("TV Limpia", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (connected == null) "Quita lo que sobra de tu TV desde el móvil" else "$tvInfo · $connected",
                        color = muted,
                    )
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (status.isNotBlank()) item {
                Card(Modifier.fillMaxWidth()) { Text(status, Modifier.padding(12.dp)) }
            }

            val h = health
            if (connected != null && h != null) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionTitle("Estado de la TV")
                        Text(h.text())
                        if (h.diskUsedPct >= 85) Text(
                            "Almacenamiento casi lleno: esto ralentiza mucho la TV. Desinstala apps o juegos que no uses.",
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        if (h.ramTotalMb in 1..2200) Text(
                            "Tu TV tiene poca RAM (≈2 GB): frenar las apps de segundo plano es lo que más se nota.",
                            color = muted, fontSize = 13.sp,
                        )
                    }
                }
            }

            if (connected == null) {
                // ---------- PANTALLA DE CONEXIÓN ----------
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Antes de empezar (solo una vez, en la TV)", fontWeight = FontWeight.Bold)
                            Text("1. Ajustes → Sistema → Acerca de → pulsa 7 veces en «Compilación del SO de Android TV» hasta que diga que ya eres desarrollador.")
                            Text("2. Ajustes → Sistema → Opciones de desarrollador → activa «Depuración USB» y «Depuración por red» (en algunos modelos se llama «ADB por red»).")
                            Text("3. Móvil y TV conectados a la misma Wi‑Fi.")
                        }
                    }
                }
                item {
                    Button(
                        onClick = {
                            job("Buscando televisores en tu Wi‑Fi…") {
                                val r = Scanner.scan(ctx)
                                found = r
                                status = if (r.isEmpty())
                                    "No encontré ninguna TV con la depuración por red activada. Revisa los pasos o escribe la IP de la TV abajo (en la TV: Ajustes → Red e Internet → tu Wi‑Fi)."
                                else "He encontrado ${r.size}. Toca la tuya."
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("1 · Buscar mi TV", fontSize = 18.sp) }
                }
                items(found) { ip ->
                    OutlinedButton(onClick = { connect(ip) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("2 · Conectar con $ip")
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it.trim() },
                            label = { Text("IP de la TV (opcional)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { connect(manualIp) }, enabled = !busy && manualIp.isNotBlank()) {
                            Text("Conectar")
                        }
                    }
                }
            } else {
                // ---------- PANTALLA DE LIMPIEZA ----------
                val rec = apps.filter { it.group == Group.RECOMMENDED && !it.disabled }
                val rev = apps.filter { it.group == Group.REVIEW && !it.disabled }
                val dis = apps.filter { it.disabled }
                val prot = apps.count { it.group == Group.PROTECTED && !it.disabled }
                val mine = apps.filter { it.group == Group.MINE && !it.disabled }

                if (mine.isNotEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionTitle("Acelerar mis apps (IPTV)")
                                Text(
                                    "Compila tus apps a código nativo para que abran, se muevan por los menús y cambien de canal con más fluidez. " +
                                        "No borra datos: tu usuario y contraseña se mantienen. Tarda unos minutos.",
                                    color = muted, fontSize = 13.sp,
                                )
                                Button(onClick = { boostMine() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                    Text("Acelerar mis ${mine.size} apps")
                                }
                            }
                        }
                    }
                    item { SectionTitle("Tus apps instaladas (${mine.size}) · no se tocan") }
                    items(mine, key = { "mine_" + it.pkg }) { a -> InfoRow(a) }
                }

                item { SectionTitle("Recomendadas para desactivar (${rec.size})") }
                if (rec.isEmpty()) item {
                    Text("Nada de la lista segura en esta TV. Mira «más apps para revisar».", color = muted)
                }
                items(rec, key = { it.pkg }) { a ->
                    AppRow(a, selected[a.pkg] == true, keep[a.pkg] == true, !busy, { toggleKeep(a.pkg) }) { v -> selected[a.pkg] = v }
                }

                item {
                    TextButton(onClick = { showReview = !showReview }) {
                        Text((if (showReview) "Ocultar" else "Ver") + " más apps para revisar (${rev.size})")
                    }
                }
                if (showReview) {
                    item {
                        Text(
                            "Desactiva solo lo que reconozcas. Si algo deja de funcionar, se reactiva abajo o con «Restaurar todo».",
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    items(rev, key = { it.pkg }) { a ->
                        AppRow(a, selected[a.pkg] == true, keep[a.pkg] == true, !busy, { toggleKeep(a.pkg) }) { v -> selected[a.pkg] = v }
                    }
                }

                if (dis.isNotEmpty()) {
                    item { SectionTitle("Desactivadas (${dis.size})") }
                    items(dis, key = { "off_" + it.pkg }) { a ->
                        DisabledRow(a, !busy) { enable(listOf(a.pkg), "Reactivando ${a.name}…") }
                    }
                }

                item {
                    Text(
                        "Protegidas: $prot apps del sistema que TV Limpia nunca toca (inicio, ajustes, HDMI, mando, actualizaciones, Google…).",
                        fontSize = 13.sp,
                        color = muted,
                    )
                }

                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionTitle("Optimización")
                            Text(
                                "Acelera animaciones, frena en segundo plano las apps no esenciales (nunca las tuyas ni las marcadas ★), " +
                                    "congela apps en caché, vacía cachés y compila las apps para que abran antes.",
                                color = muted, fontSize = 13.sp,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { optimizeAgain() }, enabled = !busy) { Text("Optimizar") }
                                OutlinedButton(onClick = { undoOptimize() }, enabled = !busy) { Text("Deshacer") }
                            }
                            Button(onClick = { rebootTv() }, enabled = !busy) { Text("Reiniciar TV") }
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val s = ourSet(connected ?: "").toList()
                                if (s.isEmpty()) status = "No hay nada que restaurar."
                                else enable(s, "Restaurando ${s.size} apps…")
                            },
                            enabled = !busy,
                        ) { Text("Restaurar todo") }
                        OutlinedButton(onClick = { export() }, enabled = !busy) { Text("Exportar lista") }
                    }
                }
                item { TextButton(onClick = { disconnect() }, enabled = !busy) { Text("Desconectar") } }
            }
        }
    }
}

@Composable
fun SectionTitle(t: String) {
    Text(t, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun AppRow(
    a: AppEntry,
    checked: Boolean,
    kept: Boolean,
    enabled: Boolean,
    onKeep: () -> Unit,
    onChange: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked && !kept, onCheckedChange = onChange, enabled = enabled && !kept)
            Column(Modifier.weight(1f).padding(top = 8.dp, bottom = 8.dp, end = 8.dp)) {
                Text(a.name, fontWeight = FontWeight.SemiBold)
                if (a.note.isNotBlank()) Text(a.note, fontSize = 13.sp)
                Text(a.pkg, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onKeep, enabled = enabled) { Text(if (kept) "★ Mantener" else "☆ Mantener") }
        }
    }
}

@Composable
fun InfoRow(a: AppEntry) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(a.name, fontWeight = FontWeight.SemiBold)
            Text(a.pkg, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DisabledRow(a: AppEntry, enabled: Boolean, onEnable: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(a.name, fontWeight = FontWeight.SemiBold)
                Text(a.pkg, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onEnable, enabled = enabled) { Text("Reactivar") }
        }
    }
}

fun friendlyError(e: Exception): String {
    val m = (e.message ?: e.javaClass.simpleName)
    val l = m.lowercase()
    return when {
        "broken pipe" in l || "reset" in l || "eof" in l || "closed" in l ->
            "La TV cortó la conexión.\n" +
                "• Mira si en la TV hay un aviso «¿Permitir depuración?» y acéptalo con «Permitir siempre».\n" +
                "• Si no sale ningún aviso: en Opciones para desarrolladores, apaga y vuelve a encender «Depuración por red» (y «Revocar autorizaciones de depuración USB» si existe), y pulsa Conectar otra vez."
        "refused" in l || "timed out" in l || "timeout" in l || "unreachable" in l ->
            "No llego a la TV. Comprueba que está encendida, en la misma Wi‑Fi y con «Depuración por red» activada."
        else -> "Error: $m"
    }
}
