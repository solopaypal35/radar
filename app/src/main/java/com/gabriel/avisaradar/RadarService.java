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
    public static volatile float progresoTramo = 0;   // 0 = inicio, 1 = final
    public static volatile int recorridoTramo = 0, restanteTramo = 0; // metros
    // Límite de la carretera por la que vas (OpenStreetMap)
    public static volatile int limiteVia = 0;
    // Próximo peaje delante (para la pestaña «Peajes» de la pantalla principal)
    public static volatile String peajeTexto = null;

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
    /** Distancias de aviso (las elige el usuario). 1ª, 3ª y 4ª siempre; 2ª solo si vas por encima del límite. */
    private int[] umbrales = {500, 200, 100, 50};
    private final int[] modosDistancia = new int[4];
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

    // Semáforos con cámara y peajes: se avisa una vez al acercarte
    private List<PuntosDB.Punto> semaforos, peajes;
    private final Map<PuntosDB.Punto, Boolean> puntosAvisados = new IdentityHashMap<>();
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
        semaforos = PuntosDB.semaforos(this);
        peajes = PuntosDB.peajes(this);
        umbrales = Ajustes.distancias(this);
        for (int i = 0; i < 4; i++) modosDistancia[i] = Ajustes.modoDistancia(this, i);
        tts = new TextToSpeech(this, estado -> {
            if (estado == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("es", "ES"));
                tts.setAudioAttributes(atributosAviso());
                tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                    @Override
                    public void onStart(String id) {
                    }

                    @Override
                    public void onDone(String id) {
                        frasePendientes--;
                        if (frasePendientes <= 0) soltarFoco();
                    }

                    @Override
                    public void onError(String id) {
                        frasePendientes--;
                        if (frasePendientes <= 0) soltarFoco();
                    }
                });
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
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlLoro:gps");
            wakeLock.acquire(12 * 60 * 60 * 1000L); // máximo 12 horas
            activo = true;
            if (Ajustes.avisosTrafico(this)) iniciarTrafico();
            iniciarLimiteVia();
            // La burbuja solo aparece cuando la app está minimizada (Maps, Waze, escritorio…)
            if (Ajustes.burbuja(this) && Burbuja.tienePermiso(this)) {
                burbuja = new Burbuja(this);
                manejador.post(vigilarBurbuja);
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
        manejador.removeCallbacks(vigilarBurbuja);
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
        peajeTexto = null;
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
        soltarFoco();
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
            emitir(frase, "trafico");
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
        recorridoTramo = (int) recorrido;
        restanteTramo = (int) dFin;
        progresoTramo = (float) Math.max(0, Math.min(1, recorrido / Math.max(1, recorrido + dFin)));

        if (!avisadoMedia && dFin <= 200) {
            avisadoMedia = true;
            boolean pasado = tramo.limite > 0 && mediaTramo > tramo.limite;
            if (!Ajustes.soloSiMePaso(this) || pasado) {
                String frase = "Tu media es de " + mediaTramo + " kilómetros por hora";
                if (pasado) {
                    frase += ". Vas " + (mediaTramo - tramo.limite)
                            + " por encima del límite de " + tramo.limite;
                }
                emitir(frase, "tramo");
                notificar("Radar de tramo", frase, pasado ? 0xFFFF4D5E : 0xFFFFB020, ID_AVISO + 2);
            }
        }
        boolean llegado = dFin < 40 || (dFin > minFin + 80 && minFin < 250);
        boolean perdido = dFin > minFin + 1500 || segundos > 2 * 60 * 60;
        if (llegado || perdido) {
            if (llegado && !Ajustes.soloSiMePaso(this)) emitir("Fin del tramo", "tramo");
            tramoTerminado = tramo;
            finTramoHora = System.currentTimeMillis();
            tramo = null;
            enTramo = false;
            limiteTramo = 0;
            progresoTramo = 0;
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
            emitir("Inicio de radar de tramo" + (t.limite > 0 ? ", límite " + t.limite : ""), "tramo");
        }
        return true;
    }

    // ---------- Semáforos con cámara y peajes ----------

    private void revisarPuntos(List<PuntosDB.Punto> lista, int distanciaAviso, Location loc,
                               boolean hayRumbo, float rumbo, boolean esPeaje) {
        float[] res = new float[2];
        for (PuntosDB.Punto p : lista) {
            Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), p.lat, p.lon, res);
            if (res[0] > distanciaAviso + 1000) {
                puntosAvisados.remove(p);
                continue;
            }
            if (res[0] > distanciaAviso || puntosAvisados.containsKey(p)) continue;
            if (hayRumbo && angulo(rumbo, res[1]) > 40) continue; // solo si lo tienes delante
            puntosAvisados.put(p, true);
            String frase;
            if (esPeaje) {
                frase = "Peaje a " + distanciaHablada(res[0]);
                if (!p.precio.isEmpty()) frase += ", precio " + p.precio;
            } else {
                frase = "Semáforo con cámara a " + redondear(res[0]) + " metros";
            }
            emitir(frase, esPeaje ? "peaje" : "semaforo");
            notificar(esPeaje ? "Peaje" : "Semáforo con cámara", frase, 0xFFB57CFF, ID_AVISO + 4);
            return; // un aviso cada vez
        }
    }

    private String proximoPeaje(Location loc, boolean hayRumbo, float rumbo) {
        float[] res = new float[2];
        PuntosDB.Punto mejor = null;
        float dMejor = 5000;
        for (PuntosDB.Punto p : peajes) {
            Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), p.lat, p.lon, res);
            if (res[0] >= dMejor) continue;
            if (hayRumbo && angulo(rumbo, res[1]) > 40) continue;
            dMejor = res[0];
            mejor = p;
        }
        if (mejor == null) return null;
        return "Peaje a " + distanciaHablada(dMejor).replace(" kilómetros", " km").replace(" kilómetro", " km")
                .replace(" metros", " m") + (mejor.precio.isEmpty() ? "" : " · " + mejor.precio.replace(" euros", " €"));
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
            if (d > Math.max(1500, umbrales[0] + 500)) {
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
        if (kmh >= 15) {
            revisarPuntos(semaforos, 200, loc, hayRumbo, rumbo, false);
            revisarPuntos(peajes, 1000, loc, hayRumbo, rumbo, true);
        }
        peajeTexto = proximoPeaje(loc, hayRumbo, rumbo);
        maxVelocidad = Math.max(maxVelocidad, kmh);

        // Estado para la pantalla
        hayGps = true;
        velocidad = (int) Math.round(kmh);
        boolean cerca = cercano != null && dCercano < 1500;
        distancia = cerca ? (int) dCercano : -1;
        // Si el radar no tiene límite anotado, se usa el de la carretera por la que vas
        int limRadar = cerca ? (cercano.limite > 0 ? cercano.limite : limiteVia) : 0;
        limite = limRadar;
        exceso = cerca && dCercano <= 500 && limRadar > 0 && kmh > limRadar + 3;

        long ahora = System.currentTimeMillis();
        if (cercano != null && kmh >= 15) {
            int antes = nivel.containsKey(cercano) ? nivel.get(cercano) : 0;
            int ahoraNivel = antes;
            while (ahoraNivel < umbrales.length && dCercano <= umbrales[ahoraNivel]) ahoraNivel++;
            if (ahoraNivel > antes) {
                nivel.put(cercano, ahoraNivel);
                int indice = ahoraNivel - 1;
                int umbral = umbrales[indice];
                boolean rapido = limRadar > 0 && kmh > limRadar + 3;
                int modo = modosDistancia[indice];
                int metros = antes == 0 && indice > 0 ? redondear(dCercano) : umbral;
                if (Ajustes.soloSiMePaso(this) && !rapido) {
                    // Opción «solo si me paso»: no se avisa
                } else if (modo == Ajustes.DIST_SILENCIO) {
                    avisar(cercano, metros, limRadar, rapido, false); // solo notificación
                } else if (modo == Ajustes.DIST_VOZ || rapido) {
                    avisar(cercano, metros, limRadar, rapido, true);
                }
            }
        }

        if (ahora - ultimaNotif > 3000) {
            ultimaNotif = ahora;
            String texto;
            if (cercano != null && dCercano < 1500) {
                texto = (cercano.propio ? "Aviso" : "Radar") + " a " + redondear(dCercano) + " m"
                        + (limite > 0 ? " · límite " + limite : "");
            } else {
                texto = "Sin radares cerca";
            }
            texto += " · " + Math.round(kmh) + " km/h";
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.notify(ID_ESTADO, notificacionEstado(texto));
        }
    }

    private void avisar(RadarDB.Radar r, int metros, int lim, boolean exceso, boolean conSonido) {
        String frase = (r.propio ? "Aviso a " : "Radar a ") + metros + " metros";
        if (lim > 0) frase += ", límite " + lim;
        if (exceso) frase += ". Reduce la velocidad";
        if (conSonido) emitir(frase, "radar");
        notificar(r.propio ? "Aviso" : "Radar", frase, 0xFFFF4D5E, ID_AVISO);
    }

    /** Avisa según el tipo elegido: voz, solo pitido o solo vibración. */
    private void emitir(String frase, String tipo) {
        if (!Ajustes.sonido(this, tipo)) return; // ese tipo está en silencio: solo notificación
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

    /** Cada medio segundo: muestra la burbuja si la app está minimizada y la oculta si está delante. */
    private final Runnable vigilarBurbuja = new Runnable() {
        @Override
        public void run() {
            if (burbuja == null) return;
            boolean appDelante = Pantalla.visibles > 0;
            if (appDelante || burbuja.cerrada) burbuja.ocultar();
            else burbuja.mostrar();
            manejador.postDelayed(this, 500);
        }
    };

    // ---------- Foco de audio: la música baja de volumen mientras se avisa ----------

    private android.media.AudioFocusRequest foco;
    private volatile int frasePendientes = 0;
    private final android.os.Handler manejador = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable soltar = () -> {
        if (frasePendientes <= 0) soltarFoco();
    };

    static android.media.AudioAttributes atributosAviso() {
        return new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
    }

    /** Pide a Android que baje la música (sin pararla) mientras suena el aviso. */
    private void pedirFoco() {
        manejador.removeCallbacks(soltar);
        if (foco == null) {
            foco = new android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(atributosAviso())
                    .setOnAudioFocusChangeListener(c -> {
                    })
                    .build();
        }
        try {
            getSystemService(AudioManager.class).requestAudioFocus(foco);
        } catch (Exception ignored) {
        }
    }

    /** La música vuelve a su volumen normal. */
    private void soltarFoco() {
        if (foco == null) return;
        try {
            getSystemService(AudioManager.class).abandonAudioFocusRequest(foco);
        } catch (Exception ignored) {
        }
    }

    private void pitar() {
        pedirFoco();
        // Si solo hay pitido, se suelta el foco poco después; si luego hay voz, se suelta al acabar de hablar
        manejador.postDelayed(soltar, 900);
        if (tono != null) {
            try {
                tono.startTone(ToneGenerator.TONE_PROP_BEEP2, 400);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void hablar(String frase) {
        if (ttsListo) {
            pedirFoco();
            frasePendientes++;
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
                CANAL_ESTADO, "Estado de Al Loro", NotificationManager.IMPORTANCE_LOW);
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
                .setContentTitle("Al Loro activo")
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
