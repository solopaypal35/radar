package com.gabriel.avisaradar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Servicio en segundo plano: sigue el GPS y avisa al acercarse a un radar. */
public class RadarService extends Service implements LocationListener {

    public static final String ACCION_PARAR = "com.gabriel.avisaradar.PARAR";
    static final String CANAL_ESTADO = "estado";
    static final String CANAL_AVISO = "avisos";
    static final int ID_ESTADO = 1;
    static final int ID_AVISO = 2;

    public static volatile boolean activo = false;
    public static volatile Location ultima = null;
    // Estado para la pantalla de la app
    public static volatile int velocidad = 0;      // km/h
    public static volatile int distancia = -1;     // metros al radar de delante, -1 = ninguno
    public static volatile int limite = 0;         // km/h, 0 = desconocido
    public static volatile boolean exceso = false; // vas por encima del límite cerca de un radar
    public static volatile boolean hayGps = false;
    public static volatile String incidenciaTexto = null; // "Accidente a 1,2 km" o null
    // Radar de tramo
    public static volatile boolean enTramo = false;
    public static volatile int mediaTramo = 0;      // km/h
    public static volatile int limiteTramo = 0;
    // Límite de la carretera por la que vas (OpenStreetMap)
    public static volatile int limiteVia = 0;

    /** Límite que se enseña en pantalla: el del radar cercano, el del tramo o el de la vía. */
    public static int limiteMostrado() {
        if (distancia >= 0 && limite > 0) return limite;
        if (enTramo && limiteTramo > 0) return limiteTramo;
        return limiteVia;
    }

    private List<RadarDB.Radar> radares;
    private LocationManager lm;
    private TextToSpeech tts;
    private boolean ttsListo = false;
    private ToneGenerator tono;
    private PowerManager.WakeLock wakeLock;
    /** Distancias de aviso. A 500, 100 y 50 m siempre; a 200 m solo si vas por encima del límite. */
    static final int[] UMBRALES = {500, 200, 100, 50};
    /** Para cada radar cercano, cuántos umbrales se han pasado ya. */
    private final Map<RadarDB.Radar, Integer> nivel = new IdentityHashMap<>();
    private long ultimaNotif = 0;

    /** Avisos de tráfico: a 2 km y a 500 m de cada incidencia importante que tengas delante. */
    static final int[] UMBRALES_TRAFICO = {2000, 500};
    private final Map<IncidenciasDB.Incidencia, Integer> nivelTrafico = new IdentityHashMap<>();
    private Thread hiloTrafico;

    // Tramos
    private List<TramosDB.Tramo> tramos;
    private TramosDB.Tramo tramo, tramoTerminado;
    private double finLat, finLon, recorrido, minFin;
    private long inicioTramo, finTramoHora;
    private Location anteriorTramo;
    private boolean avisadoMedia;

    private Thread hiloLimite;
    private Burbuja burbuja;
    private double maxVelocidad = 0;
    private final BroadcastReceiver bluetooth = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            // Al desconectarse el Bluetooth del coche, se guarda dónde has aparcado
            guardarAparcamientoAuto();
        }
    };
    private boolean bluetoothRegistrado = false;

    @Override
    public void onCreate() {
        super.onCreate();
        crearCanales();
        radares = RadarDB.cargar(this);
        tramos = TramosDB.cargar(this);
        tts = new TextToSpeech(this, estado -> {
            if (estado == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("es", "ES"));
                ttsListo = true;
            }
        });
        try {
            tono = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        } catch (RuntimeException e) {
            tono = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACCION_PARAR.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Notification n = notificacionEstado("Esperando señal GPS…");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID_ESTADO, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(ID_ESTADO, n);
        }
        if (!activo) {
            lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
            } catch (SecurityException | IllegalArgumentException e) {
                stopSelf();
                return START_NOT_STICKY;
            }
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AvisaRadar:gps");
            wakeLock.acquire(12 * 60 * 60 * 1000L); // máximo 12 horas
            activo = true;
            if (Ajustes.avisosTrafico(this)) iniciarTrafico();
            iniciarLimiteVia();
            if (Ajustes.burbuja(this) && Burbuja.tienePermiso(this)) {
                burbuja = new Burbuja(this);
                burbuja.mostrar();
            }
            try {
                IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
                if (Build.VERSION.SDK_INT >= 33) {
                    registerReceiver(bluetooth, f, Context.RECEIVER_EXPORTED);
                } else {
                    registerReceiver(bluetooth, f);
                }
                bluetoothRegistrado = true;
            } catch (Exception ignored) {
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        guardarAparcamientoAuto();
        if (burbuja != null) burbuja.ocultar();
        if (bluetoothRegistrado) {
            try {
                unregisterReceiver(bluetooth);
            } catch (Exception ignored) {
            }
        }
        if (hiloLimite != null) hiloLimite.interrupt();
        enTramo = false;
        mediaTramo = 0;
        limiteTramo = 0;
        limiteVia = 0;
        activo = false;
        hayGps = false;
        velocidad = 0;
        distancia = -1;
        limite = 0;
        exceso = false;
        incidenciaTexto = null;
        if (hiloTrafico != null) hiloTrafico.interrupt();
        if (lm != null) lm.removeUpdates(this);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (tts != null) tts.shutdown();
        if (tono != null) tono.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Descarga las incidencias de la DGT al empezar y cada 5 minutos. */
    private void iniciarTrafico() {
        hiloTrafico = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                IncidenciasDB.actualizar();
                try {
                    Thread.sleep(5 * 60 * 1000L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        hiloTrafico.setDaemon(true);
        hiloTrafico.start();
    }

    private void revisarTrafico(Location loc, double kmh, boolean hayRumbo, float rumbo) {
        List<IncidenciasDB.Incidencia> lista = IncidenciasDB.actuales;
        IncidenciasDB.Incidencia cercana = null;
        double dCercana = Double.MAX_VALUE;
        float[] res = new float[2];
        for (IncidenciasDB.Incidencia i : lista) {
            if (!i.importante) continue;
            Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), i.lat, i.lon, res);
            double d = res[0];
            if (d > 3000) {
                nivelTrafico.remove(i);
                continue;
            }
            if (hayRumbo && angulo(rumbo, res[1]) > 35) continue;
            if (d < dCercana) {
                dCercana = d;
                cercana = i;
            }
        }
        incidenciaTexto = cercana == null || dCercana > 2500 ? null
                : cercana.tipo + " a " + distanciaHablada(dCercana);
        if (cercana == null || kmh < 15) return;
        int antes = nivelTrafico.containsKey(cercana) ? nivelTrafico.get(cercana) : 0;
        int ahoraNivel = antes;
        while (ahoraNivel < UMBRALES_TRAFICO.length && dCercana <= UMBRALES_TRAFICO[ahoraNivel]) ahoraNivel++;
        if (ahoraNivel > antes) {
            nivelTrafico.put(cercana, ahoraNivel);
            String frase = cercana.tipo + " a " + distanciaHablada(dCercana);
            emitir(frase);
            Notification n = new Notification.Builder(this, CANAL_AVISO)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setColor(0xFFFFB020)
                    .setContentTitle("Tráfico" + (cercana.carretera.isEmpty() ? "" : " · " + cercana.carretera))
                    .setContentText(frase)
                    .setAutoCancel(true)
                    .setTimeoutAfter(30000)
                    .setCategory(Notification.CATEGORY_NAVIGATION)
                    .build();
            getSystemService(NotificationManager.class).notify(ID_AVISO + 1, n);
        }
    }

    static String distanciaHablada(double m) {
        if (m >= 1000) {
            double km = Math.round(m / 100.0) / 10.0;
            String s = (km == Math.floor(km)) ? String.valueOf((int) km) : String.valueOf(km).replace('.', ',');
            return s + (km == 1.0 ? " kilómetro" : " kilómetros");
        }
        return (Math.round(m / 50.0) * 50) + " metros";
    }

    // ---------- Límite de la vía ----------

    /** Consulta el límite de la carretera cada vez que avanzas unos 300 m (como mucho cada 15 s). */
    private void iniciarLimiteVia() {
        hiloLimite = new Thread(() -> {
            Location consultada = null;
            long cuando = 0;
            while (!Thread.currentThread().isInterrupted()) {
                Location l = ultima;
                long ahora = System.currentTimeMillis();
                if (l != null && ahora - l.getTime() < 20000 && ahora - cuando > 15000
                        && (consultada == null || consultada.distanceTo(l) > 300)) {
                    int lim = LimiteVia.consultar(l.getLatitude(), l.getLongitude());
                    if (lim >= 0) limiteVia = lim;
                    consultada = l;
                    cuando = ahora;
                }
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        hiloLimite.setDaemon(true);
        hiloLimite.start();
    }

    // ---------- Radares de tramo ----------

    private void revisarTramo(Location loc, double kmh, boolean hayRumbo, float rumbo) {
        float[] res = new float[2];
        if (tramo == null) {
            for (TramosDB.Tramo t : tramos) {
                if (t == tramoTerminado && System.currentTimeMillis() - finTramoHora < 120000) continue;
                Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), t.latA, t.lonA, res);
                if (res[0] < 45 && empezar(t, loc, t.latB, t.lonB, hayRumbo, rumbo)) return;
                Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), t.latB, t.lonB, res);
                if (res[0] < 45 && empezar(t, loc, t.latA, t.lonA, hayRumbo, rumbo)) return;
            }
            return;
        }

        // Dentro del tramo: se suma lo recorrido y se calcula la media
        if (anteriorTramo != null) recorrido += anteriorTramo.distanceTo(loc);
        anteriorTramo = loc;
        double segundos = (loc.getTime() - inicioTramo) / 1000.0;
        if (segundos >= 8) {
            mediaTramo = (int) Math.round(recorrido / segundos * 3.6);
        }
        Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), finLat, finLon, res);
        double dFin = res[0];
        minFin = Math.min(minFin, dFin);

        if (!avisadoMedia && dFin <= 200) {
            avisadoMedia = true;
            boolean pasado = tramo.limite > 0 && mediaTramo > tramo.limite;
            if (!Ajustes.soloSiMePaso(this) || pasado) {
                String frase = "Tu media es de " + mediaTramo + " kilómetros por hora";
                if (pasado) frase += ", por encima del límite de " + tramo.limite;
                emitir(frase);
                notificar("Radar de tramo", frase, pasado ? 0xFFFF4D5E : 0xFFFFB020, ID_AVISO + 2);
            }
        }
        boolean llegado = dFin < 40 || (dFin > minFin + 80 && minFin < 250);
        boolean perdido = dFin > minFin + 1500 || segundos > 2 * 60 * 60;
        if (llegado || perdido) {
            if (llegado && !Ajustes.soloSiMePaso(this)) emitir("Fin del tramo");
            tramoTerminado = tramo;
            finTramoHora = System.currentTimeMillis();
            tramo = null;
            enTramo = false;
            limiteTramo = 0;
        }
    }

    private boolean empezar(TramosDB.Tramo t, Location loc, double lat, double lon,
                            boolean hayRumbo, float rumbo) {
        float[] res = new float[2];
        Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), lat, lon, res);
        if (res[0] < 300) return false;                        // demasiado corto
        if (!hayRumbo || angulo(rumbo, res[1]) > 70) return false; // no vas hacia el final
        tramo = t;
        finLat = lat;
        finLon = lon;
        recorrido = 0;
        minFin = res[0];
        inicioTramo = loc.getTime();
        anteriorTramo = loc;
        avisadoMedia = false;
        mediaTramo = 0;
        limiteTramo = t.limite;
        enTramo = true;
        if (!Ajustes.soloSiMePaso(this)) {
            emitir("Inicio de radar de tramo" + (t.limite > 0 ? ", límite " + t.limite : ""));
        }
        return true;
    }

    // ---------- Dónde he aparcado ----------

    private void guardarAparcamientoAuto() {
        Location l = ultima;
        if (!Ajustes.aparcarAuto(this) || l == null || maxVelocidad < 20) return;
        if (System.currentTimeMillis() - l.getTime() > 10 * 60 * 1000L) return;
        Ajustes.guardarAparcamiento(this, l.getLatitude(), l.getLongitude());
        maxVelocidad = 0; // para no guardarlo dos veces
        notificar("Aparcamiento guardado", "Toca para ver dónde has dejado el coche", 0xFF2EE6A6, ID_AVISO + 3);
    }

    // ---------- Lógica de avisos ----------

    @Override
    public void onLocationChanged(Location loc) {
        ultima = loc;
        double kmh = loc.hasSpeed() ? loc.getSpeed() * 3.6 : 0;
        boolean hayRumbo = loc.hasBearing() && kmh > 8;
        float rumbo = loc.getBearing();

        RadarDB.Radar cercano = null;
        double dCercano = Double.MAX_VALUE;
        float[] res = new float[2];

        for (RadarDB.Radar r : radares) {
            Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), r.lat, r.lon, res);
            double d = res[0];
            if (d > 1500) {
                nivel.remove(r); // al alejarse, se podrá volver a avisar
                continue;
            }
            if (hayRumbo) {
                // El radar tiene que estar delante de nosotros.
                if (angulo(rumbo, res[1]) > 50) continue;
                // Si el radar tiene sentido conocido, tiene que coincidir con el nuestro.
                if (r.direccion >= 0 && angulo(rumbo, r.direccion) > 60) continue;
            }
            if (d < dCercano) {
                dCercano = d;
                cercano = r;
            }
        }

        if (hiloTrafico != null) revisarTrafico(loc, kmh, hayRumbo, rumbo);
        if (!tramos.isEmpty()) revisarTramo(loc, kmh, hayRumbo, rumbo);
        maxVelocidad = Math.max(maxVelocidad, kmh);

        // Estado para la pantalla
        hayGps = true;
        velocidad = (int) Math.round(kmh);
        boolean cerca = cercano != null && dCercano < 1500;
        distancia = cerca ? (int) dCercano : -1;
        limite = cerca ? cercano.limite : 0;
        exceso = cerca && dCercano <= 500 && cercano.limite > 0 && kmh > cercano.limite + 3;

        long ahora = System.currentTimeMillis();
        if (cercano != null && kmh >= 15) {
            int antes = nivel.containsKey(cercano) ? nivel.get(cercano) : 0;
            int ahoraNivel = antes;
            while (ahoraNivel < UMBRALES.length && dCercano <= UMBRALES[ahoraNivel]) ahoraNivel++;
            if (ahoraNivel > antes) {
                nivel.put(cercano, ahoraNivel);
                int umbral = UMBRALES[ahoraNivel - 1];
                boolean rapido = cercano.limite > 0 && kmh > cercano.limite + 3;
                if (Ajustes.soloSiMePaso(this) && !rapido) {
                    // Opción «solo si me paso»: no se avisa
                } else if (antes == 0) {
                    // Primer aviso de este radar (normalmente a 500 m)
                    avisar(cercano, umbral == 500 ? 500 : redondear(dCercano), rapido);
                } else if (rapido || umbral == 100 || umbral == 50) {
                    // 100 y 50 m: siempre. 200 m: solo si todavía vas por encima del límite
                    avisar(cercano, umbral, rapido);
                }
            }
        }

        if (ahora - ultimaNotif > 3000) {
            ultimaNotif = ahora;
            String texto;
            if (cercano != null && dCercano < 1500) {
                texto = (cercano.propio ? "Aviso" : "Radar") + " a " + redondear(dCercano) + " m"
                        + (cercano.limite > 0 ? " · límite " + cercano.limite : "");
            } else {
                texto = "Sin radares cerca";
            }
            texto += " · " + Math.round(kmh) + " km/h";
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.notify(ID_ESTADO, notificacionEstado(texto));
        }
    }

    private void avisar(RadarDB.Radar r, int metros, boolean exceso) {
        String frase = (r.propio ? "Aviso a " : "Radar a ") + metros + " metros";
        if (r.limite > 0) frase += ", límite " + r.limite;
        if (exceso) frase += ". Reduce la velocidad";
        emitir(frase);
        notificar(r.propio ? "Aviso" : "Radar", frase, 0xFFFF4D5E, ID_AVISO);
    }

    /** Avisa según el tipo elegido: voz, solo pitido o solo vibración. */
    private void emitir(String frase) {
        switch (Ajustes.tipoAviso(this)) {
            case Ajustes.AVISO_PITIDO:
                pitar();
                break;
            case Ajustes.AVISO_VIBRACION:
                vibrar();
                break;
            default:
                pitar();
                hablar(frase);
                break;
        }
    }

    private void vibrar() {
        try {
            Vibrator v = getSystemService(Vibrator.class);
            if (v != null) v.vibrate(VibrationEffect.createWaveform(new long[]{0, 400, 200, 400}, -1));
        } catch (Exception ignored) {
        }
    }

    private void notificar(String titulo, String texto, int color, int id) {
        PendingIntent abrir = PendingIntent.getActivity(this, id,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CANAL_AVISO)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(color)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setContentIntent(abrir)
                .setAutoCancel(true)
                .setTimeoutAfter(30000)
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .build();
        getSystemService(NotificationManager.class).notify(id, n);
    }

    private void pitar() {
        if (tono != null) {
            try {
                tono.startTone(ToneGenerator.TONE_PROP_BEEP2, 400);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void hablar(String frase) {
        if (ttsListo) {
            tts.speak(frase, TextToSpeech.QUEUE_ADD, null, "aviso" + System.nanoTime());
        }
    }

    static int redondear(double metros) {
        return (int) (Math.round(metros / 50.0) * 50);
    }

    /** Diferencia entre dos rumbos, de 0 a 180 grados. */
    static double angulo(double a, double b) {
        double dif = Math.abs(a - b) % 360;
        return dif > 180 ? 360 - dif : dif;
    }

    // ---------- Notificaciones ----------

    private void crearCanales() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel estado = new NotificationChannel(
                CANAL_ESTADO, "Estado de AvisaRadar", NotificationManager.IMPORTANCE_LOW);
        estado.setShowBadge(false);
        nm.createNotificationChannel(estado);

        NotificationChannel aviso = new NotificationChannel(
                CANAL_AVISO, "Avisos de radar", NotificationManager.IMPORTANCE_HIGH);
        aviso.setSound(null, null); // el sonido lo pone la propia app
        aviso.enableVibration(true);
        aviso.setVibrationPattern(new long[]{0, 300, 150, 300});
        nm.createNotificationChannel(aviso);
    }

    private Notification notificacionEstado(String texto) {
        PendingIntent abrir = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent parar = PendingIntent.getService(this, 1,
                new Intent(this, RadarService.class).setAction(ACCION_PARAR),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action accionParar = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "Parar", parar).build();
        return new Notification.Builder(this, CANAL_ESTADO)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFF2EE6A6)
                .setContentTitle("AvisaRadar activo")
                .setContentText(texto)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(abrir)
                .addAction(accionParar)
                .build();
    }

    // Métodos obligatorios en versiones antiguas de Android
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }
}
