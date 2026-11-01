package com.gabriel.avisaradar;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    static final int FONDO = 0xFF0B1220;
    static final int TARJETA = 0xFF16213A;
    static final int BORDE = 0xFF24324F;
    static final int TEXTO = 0xFFE8EEF8;
    static final int SUB = 0xFF8A9BB8;
    static final int VERDE = 0xFF2EE6A6;
    static final int AMBAR = 0xFFFFB020;
    static final int AMBAR_FONDO = 0xFF3A2A08;
    static final int ROJO = 0xFFFF4D5E;

    /** A partir de cuántos días se avisa de que hay que actualizar los radares. */
    static final int DIAS_ACTUALIZAR = 30;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView botonActivar, botonMapa, aparcTexto;
    private final TextView[] chipsTipo = new TextView[3];
    private final TextView[] chipsComb = new TextView[Gasolineras.NOMBRES.length];
    private double aparcLatMostrada = Double.NaN;
    private LinearLayout tarjetaRadares, tarjetaBateria;
    private TextView radaresTitulo, radaresSub, radaresAccion;
    private ObjectAnimator latidoRadares;
    private boolean descargando = false;

    private final Runnable refresco = new Runnable() {
        @Override
        public void run() {
            refrescar();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(FONDO);
        getWindow().setNavigationBarColor(FONDO);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(20), dp(18), dp(28));

        // Cabecera con logo
        LinearLayout cabecera = new LinearLayout(this);
        cabecera.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo);
        logo.setBackground(redondo(FONDO, BORDE, 14));
        cabecera.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout titulos = new LinearLayout(this);
        titulos.setOrientation(LinearLayout.VERTICAL);
        titulos.setPadding(dp(12), 0, 0, 0);
        TextView titulo = texto("AvisaRadar", 24, TEXTO, true);
        titulos.addView(titulo);
        titulos.addView(texto("Avisos de radares fijos", 13, SUB, false));
        cabecera.addView(titulos);
        col.addView(cabecera);

        // Radar animado
        RadarView radar = new RadarView(this);
        LinearLayout.LayoutParams lpRadar = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpRadar.topMargin = dp(12);
        col.addView(radar, lpRadar);

        // Botón principal
        botonActivar = texto("Activar avisos", 19, FONDO, true);
        botonActivar.setGravity(Gravity.CENTER);
        botonActivar.setBackground(redondo(VERDE, VERDE, 30));
        pulsable(botonActivar, v -> alternar());
        LinearLayout.LayoutParams lpBoton = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
        lpBoton.topMargin = dp(8);
        col.addView(botonActivar, lpBoton);

        // Botón del mapa (solo si la opción está activada)
        botonMapa = texto("Abrir mapa", 16, VERDE, true);
        botonMapa.setGravity(Gravity.CENTER);
        botonMapa.setBackground(redondo(TARJETA, VERDE, 26));
        pulsable(botonMapa, v -> startActivity(new Intent(this, MapaActivity.class)));
        LinearLayout.LayoutParams lpMapa = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lpMapa.topMargin = dp(10);
        col.addView(botonMapa, lpMapa);

        // Tarjeta de radares (se pone naranja cuando toca actualizar)
        tarjetaRadares = tarjeta();
        LinearLayout filaRadares = new LinearLayout(this);
        filaRadares.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout textosRadares = new LinearLayout(this);
        textosRadares.setOrientation(LinearLayout.VERTICAL);
        radaresTitulo = texto("Radares", 16, TEXTO, true);
        radaresSub = texto("", 13, SUB, false);
        textosRadares.addView(radaresTitulo);
        textosRadares.addView(radaresSub);
        filaRadares.addView(textosRadares, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        radaresAccion = texto("Actualizar", 14, VERDE, true);
        filaRadares.addView(radaresAccion);
        tarjetaRadares.addView(filaRadares);
        pulsable(tarjetaRadares, v -> actualizar());
        col.addView(tarjetaRadares, margenArriba(12));

        // Tarjeta de batería (solo aparece si Android puede cerrar la app)
        tarjetaBateria = tarjeta();
        tarjetaBateria.setBackground(redondo(AMBAR_FONDO, AMBAR, 16));
        tarjetaBateria.addView(texto("Permite que funcione siempre", 16, AMBAR, true));
        TextView expl = texto("Android puede cerrar AvisaRadar en viajes largos para ahorrar batería, "
                + "y dejarías de oír los avisos. Toca aquí y pulsa «Permitir».", 13, TEXTO, false);
        expl.setPadding(0, dp(4), 0, 0);
        tarjetaBateria.addView(expl);
        pulsable(tarjetaBateria, v -> pedirSinOptimizar());
        col.addView(tarjetaBateria, margenArriba(12));

        // Dónde he aparcado
        LinearLayout aparc = tarjeta();
        aparc.addView(texto("Dónde he aparcado", 16, TEXTO, true));
        aparcTexto = texto("", 13, SUB, false);
        aparcTexto.setPadding(0, dp(2), 0, dp(10));
        aparc.addView(aparcTexto);
        LinearLayout filaAparc = new LinearLayout(this);
        TextView guardar = botonPequeno("Guardar aquí");
        pulsable(guardar, v -> guardarAparcamientoManual());
        TextView llegar = botonPequeno("Cómo llegar");
        pulsable(llegar, v -> comoLlegar());
        TextView whatsapp = botonPequeno("WhatsApp");
        whatsapp.setTextColor(VERDE);
        iconoApp(whatsapp, "com.whatsapp");
        pulsable(whatsapp, v -> enviarWhatsApp());
        filaAparc.addView(guardar, pesoBoton());
        filaAparc.addView(llegar, pesoBoton());
        filaAparc.addView(whatsapp, pesoBoton());
        aparc.addView(filaAparc);
        col.addView(aparc, margenArriba(12));

        // Música (Spotify u otra app que esté sonando)
        LinearLayout musica = tarjeta();
        musica.addView(texto("Música", 16, TEXTO, true));
        LinearLayout filaMusica = new LinearLayout(this);
        filaMusica.setPadding(0, dp(10), 0, 0);
        TextView ant = botonPequeno("⏮");
        ant.setTextSize(20);
        pulsable(ant, v -> Musica.anterior(this));
        TextView pp = botonPequeno("⏯");
        pp.setTextSize(20);
        pulsable(pp, v -> Musica.playPausa(this));
        TextView sig = botonPequeno("⏭");
        sig.setTextSize(20);
        pulsable(sig, v -> Musica.siguiente(this));
        TextView spotify = botonPequeno("Spotify");
        spotify.setTextColor(VERDE);
        iconoApp(spotify, Musica.SPOTIFY);
        pulsable(spotify, v -> Musica.abrirSpotify(this));
        filaMusica.addView(ant, pesoBoton());
        filaMusica.addView(pp, pesoBoton());
        filaMusica.addView(sig, pesoBoton());
        filaMusica.addView(spotify, pesoBoton());
        musica.addView(filaMusica);
        col.addView(musica, margenArriba(12));

        // Opciones
        LinearLayout opciones = tarjeta();
        opciones.addView(texto("Opciones", 16, TEXTO, true));
        Switch interMapa = interruptor("Mapa", Ajustes.usarMapa(this));
        interMapa.setOnCheckedChangeListener((b, activado) -> {
            Ajustes.setUsarMapa(this, activado);
            refrescar();
        });
        opciones.addView(interMapa);
        Switch interTrafico = interruptor("Avisos de tráfico", Ajustes.avisosTrafico(this));
        interTrafico.setOnCheckedChangeListener((b, activado) -> {
            Ajustes.setAvisosTrafico(this, activado);
            reiniciarSiActivo();
        });
        opciones.addView(interTrafico);

        // Tipo de aviso: tres botones, el elegido en verde
        LinearLayout filaTipo = new LinearLayout(this);
        filaTipo.setGravity(Gravity.CENTER_VERTICAL);
        filaTipo.setPadding(0, dp(12), 0, dp(4));
        filaTipo.addView(texto("Aviso", 14, TEXTO, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String[] nombres = {"Voz", "Pitido", "Vibrar"};
        for (int i = 0; i < 3; i++) {
            final int tipo = i;
            TextView chip = botonPequeno(nombres[i]);
            chip.setTextSize(13);
            pulsable(chip, v -> {
                Ajustes.setTipoAviso(this, tipo);
                pintarTipoAviso();
            });
            chipsTipo[i] = chip;
            LinearLayout.LayoutParams lpChip = new LinearLayout.LayoutParams(dp(68), dp(36));
            lpChip.leftMargin = dp(6);
            filaTipo.addView(chip, lpChip);
        }
        pintarTipoAviso();
        opciones.addView(filaTipo);

        // Combustible que usas (para los precios de las gasolineras del mapa)
        LinearLayout filaComb = new LinearLayout(this);
        filaComb.setGravity(Gravity.CENTER_VERTICAL);
        filaComb.setPadding(0, dp(10), 0, dp(4));
        filaComb.addView(texto("Combustible", 14, TEXTO, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        for (int i = 0; i < Gasolineras.NOMBRES.length; i++) {
            final int tipo = i;
            TextView chip = botonPequeno(Gasolineras.NOMBRES[i]);
            chip.setTextSize(12);
            pulsable(chip, v -> {
                Ajustes.setCombustible(this, tipo);
                pintarCombustible();
            });
            chipsComb[i] = chip;
            LinearLayout.LayoutParams lpChip = new LinearLayout.LayoutParams(dp(56), dp(36));
            lpChip.leftMargin = dp(4);
            filaComb.addView(chip, lpChip);
        }
        pintarCombustible();
        opciones.addView(filaComb);

        Switch interGas = interruptor("Gasolineras en el mapa", Ajustes.gasolineras(this));
        interGas.setOnCheckedChangeListener((b, activado) -> Ajustes.setGasolineras(this, activado));
        opciones.addView(interGas);

        Switch interSolo = interruptor("Solo si me paso", Ajustes.soloSiMePaso(this));
        interSolo.setOnCheckedChangeListener((b, activado) -> Ajustes.setSoloSiMePaso(this, activado));
        opciones.addView(interSolo);

        Switch interBurbuja = interruptor("Burbuja flotante", Ajustes.burbuja(this));
        interBurbuja.setOnCheckedChangeListener((b, activado) -> {
            Ajustes.setBurbuja(this, activado);
            if (activado && !Burbuja.tienePermiso(this)) {
                toast("Activa «Mostrar sobre otras apps» para AvisaRadar y vuelve");
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
            reiniciarSiActivo();
        });
        opciones.addView(interBurbuja);

        Switch interAparcar = interruptor("Aparcamiento automático", Ajustes.aparcarAuto(this));
        interAparcar.setOnCheckedChangeListener((b, activado) -> {
            Ajustes.setAparcarAuto(this, activado);
            if (activado && Build.VERSION.SDK_INT >= 31
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 3);
            }
        });
        opciones.addView(interAparcar);
        col.addView(opciones, margenArriba(12));

        TextView ayuda = texto("Funciona en segundo plano, con Maps o Waze delante.\n\n"
                + "Radares: © OpenStreetMap. Tráfico: DGT, SCT y Gobierno Vasco.", 12, SUB, false);
        ayuda.setPadding(dp(4), dp(18), dp(4), 0);
        col.addView(ayuda);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(FONDO);
        scroll.setFillViewport(true);
        scroll.addView(col);
        setContentView(scroll);

        // Animación de entrada: cada bloque aparece deslizándose
        for (int i = 0; i < col.getChildCount(); i++) {
            View v = col.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(dp(24));
            v.animate().alpha(1f).translationY(0).setStartDelay(80L * i).setDuration(450)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refrescarRadares();
        handler.post(refresco);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresco);
    }

    private void refrescar() {
        refrescarAparcamiento();
        botonMapa.setVisibility(Ajustes.usarMapa(this) ? View.VISIBLE : View.GONE);
        // Botón principal
        if (RadarService.activo) {
            botonActivar.setText("Parar avisos");
            botonActivar.setTextColor(TEXTO);
            botonActivar.setBackground(redondo(ROJO, ROJO, 30));
        } else {
            botonActivar.setText("Activar avisos");
            botonActivar.setTextColor(FONDO);
            botonActivar.setBackground(redondo(VERDE, VERDE, 30));
        }

        // Tarjeta de batería
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        tarjetaBateria.setVisibility(pm.isIgnoringBatteryOptimizations(getPackageName())
                ? View.GONE : View.VISIBLE);
    }

    /** Lee los radares guardados y pone la tarjeta normal o naranja. */
    private void refrescarRadares() {
        if (!descargando) {
            int n = RadarDB.contarOficiales(this);
            int propios = RadarDB.propios(this).size();
            int dias = RadarDB.diasSinActualizar(this);
            boolean toca = n == 0 || dias < 0 || dias >= DIAS_ACTUALIZAR;
            String extra = propios > 0 ? " · " + propios + " tuyos" : "";
            if (n == 0) {
                radaresTitulo.setText("Descarga los radares");
                radaresSub.setText("Todavía no hay radares en el móvil" + extra);
            } else if (toca) {
                radaresTitulo.setText("Actualiza los radares");
                radaresSub.setText(n + " radares · última vez hace " + dias + " días" + extra);
            } else {
                radaresTitulo.setText(n + " radares");
                radaresSub.setText("Actualizados " + cuando(dias) + extra);
            }
            ponerAvisoRadares(toca);
        }
    }

    private void ponerAvisoRadares(boolean toca) {
        if (toca) {
            tarjetaRadares.setBackground(redondo(AMBAR_FONDO, AMBAR, 16));
            radaresTitulo.setTextColor(AMBAR);
            radaresAccion.setTextColor(AMBAR);
            if (latidoRadares == null) {
                latidoRadares = ObjectAnimator.ofFloat(tarjetaRadares, "alpha", 1f, 0.65f);
                latidoRadares.setDuration(900);
                latidoRadares.setRepeatMode(ValueAnimator.REVERSE);
                latidoRadares.setRepeatCount(ValueAnimator.INFINITE);
                latidoRadares.setStartDelay(900);
                latidoRadares.start();
            }
        } else {
            tarjetaRadares.setBackground(redondo(TARJETA, BORDE, 16));
            radaresTitulo.setTextColor(TEXTO);
            radaresAccion.setTextColor(VERDE);
            if (latidoRadares != null) {
                latidoRadares.cancel();
                latidoRadares = null;
                tarjetaRadares.setAlpha(1f);
            }
        }
    }

    static String cuando(int dias) {
        if (dias <= 0) return "hoy";
        if (dias == 1) return "ayer";
        return "hace " + dias + " días";
    }

    // ---------- Activar / parar ----------

    private void alternar() {
        if (RadarService.activo) {
            stopService(new Intent(this, RadarService.class));
            handler.postDelayed(this::refrescar, 300);
            return;
        }
        List<String> faltan = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            faltan.add(Manifest.permission.ACCESS_FINE_LOCATION);
            faltan.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            faltan.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (faltan.isEmpty()) {
            iniciar();
        } else {
            requestPermissions(faltan.toArray(new String[0]), 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permisos, int[] resultados) {
        super.onRequestPermissionsResult(code, permisos, resultados);
        if (code == 4) {
            guardarAparcamientoManual();
            return;
        }
        if (code != 1) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            iniciar();
        } else {
            toast("Sin permiso de ubicación precisa la app no puede avisar");
        }
    }

    private void iniciar() {
        if (RadarDB.contarOficiales(this) == 0 && RadarDB.propios(this).isEmpty()) {
            toast("Primero descarga los radares");
            return;
        }
        startForegroundService(new Intent(this, RadarService.class));
        handler.postDelayed(this::refrescar, 500);
    }

    private void reiniciarSiActivo() {
        if (RadarService.activo) {
            stopService(new Intent(this, RadarService.class));
            handler.postDelayed(() -> {
                startForegroundService(new Intent(this, RadarService.class));
                refrescar();
            }, 500);
        }
    }

    // ---------- Radares ----------

    private void actualizar() {
        if (descargando) return;
        descargando = true;
        radaresTitulo.setText("Descargando radares…");
        radaresSub.setText("Puede tardar un minuto");
        radaresAccion.setText("");
        new Thread(() -> {
            try {
                int n = RadarDB.descargar(this);
                try {
                    TramosDB.descargar(this);
                } catch (Exception ignored) {
                    // Si fallan los tramos, se siguen usando los que ya había
                }
                runOnUiThread(() -> {
                    descargando = false;
                    radaresAccion.setText("Actualizar");
                    toast(n + " radares descargados");
                    reiniciarSiActivo();
                    refrescarRadares();
                    refrescar();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    descargando = false;
                    radaresAccion.setText("Actualizar");
                    toast("No se pudo descargar. Revisa la conexión e inténtalo de nuevo.");
                    refrescarRadares();
                });
            }
        }).start();
    }

    private void anadirAqui() {
        Location l = RadarService.ultima;
        if (!RadarService.activo || l == null) {
            toast("Activa los avisos y espera a tener señal GPS");
            return;
        }
        RadarDB.anadirPropio(this, l.getLatitude(), l.getLongitude());
        toast("Aviso guardado en tu posición");
        reiniciarSiActivo();
        refrescarRadares();
    }

    private void confirmarBorrar() {
        if (RadarDB.propios(this).isEmpty()) {
            toast("No tienes avisos propios guardados");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Borrar mis avisos")
                .setMessage("Se borrarán todos los avisos que has añadido tú. Los radares no se tocan.")
                .setPositiveButton("Borrar", (d, w) -> {
                    RadarDB.borrarPropios(this);
                    toast("Avisos propios borrados");
                    reiniciarSiActivo();
                    refrescarRadares();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void pedirSinOptimizar() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    // ---------- Piezas de la interfaz ----------

    private TextView texto(String s, int sp, int color, boolean negrita) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (negrita) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    private void pintarTipoAviso() {
        int elegido = Ajustes.tipoAviso(this);
        for (int i = 0; i < 3; i++) {
            boolean si = i == elegido;
            chipsTipo[i].setBackground(redondo(si ? VERDE : TARJETA, si ? VERDE : BORDE, 18));
            chipsTipo[i].setTextColor(si ? FONDO : TEXTO);
        }
    }

    private void pintarCombustible() {
        int elegido = Ajustes.combustible(this);
        for (int i = 0; i < chipsComb.length; i++) {
            boolean si = i == elegido;
            chipsComb[i].setBackground(redondo(si ? VERDE : TARJETA, si ? VERDE : BORDE, 18));
            chipsComb[i].setTextColor(si ? FONDO : TEXTO);
        }
    }

    // ---------- Dónde he aparcado ----------

    private void refrescarAparcamiento() {
        if (!Ajustes.hayAparcamiento(this)) {
            aparcTexto.setText("Aún no hay ninguno guardado");
            return;
        }
        double lat = Ajustes.aparcLat(this), lon = Ajustes.aparcLon(this);
        long min = (System.currentTimeMillis() - Ajustes.aparcHora(this)) / 60000;
        String hace = min < 1 ? "ahora mismo" : min < 60 ? "hace " + min + " min"
                : min < 48 * 60 ? "hace " + (min / 60) + " h" : "hace " + (min / 1440) + " días";
        String actual = aparcTexto.getText().toString();
        int punto = actual.indexOf(" · ");
        String direccion = (lat == aparcLatMostrada && punto > 0) ? actual.substring(punto) : "";
        aparcTexto.setText("Guardado " + hace + direccion);
        if (lat != aparcLatMostrada) {
            aparcLatMostrada = lat;
            new Thread(() -> {
                String calle = direccion(lat, lon);
                if (calle != null) runOnUiThread(() -> aparcTexto.setText("Guardado " + hace + " · " + calle));
            }).start();
        }
    }

    private String direccion(double lat, double lon) {
        try {
            android.location.Geocoder g = new android.location.Geocoder(this, new java.util.Locale("es", "ES"));
            List<android.location.Address> lista = g.getFromLocation(lat, lon, 1);
            if (lista != null && !lista.isEmpty()) {
                android.location.Address a = lista.get(0);
                String calle = a.getThoroughfare();
                if (calle == null) return a.getLocality();
                return calle + (a.getSubThoroughfare() != null ? " " + a.getSubThoroughfare() : "");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void guardarAparcamientoManual() {
        Location l = RadarService.ultima;
        if (l != null && System.currentTimeMillis() - l.getTime() < 60000) {
            guardarEn(l);
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 4);
            return;
        }
        toast("Buscando tu posición…");
        android.location.LocationManager lm =
                (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(android.location.LocationManager.GPS_PROVIDER, null,
                        getMainExecutor(), loc -> {
                            if (loc != null) guardarEn(loc);
                            else toast("No hay señal GPS. Prueba al aire libre.");
                        });
            } else {
                lm.requestSingleUpdate(android.location.LocationManager.GPS_PROVIDER,
                        this::guardarEn, Looper.getMainLooper());
            }
        } catch (Exception e) {
            toast("No se pudo obtener la posición");
        }
    }

    private void guardarEn(Location l) {
        Ajustes.guardarAparcamiento(this, l.getLatitude(), l.getLongitude());
        aparcLatMostrada = Double.NaN;
        toast("Aparcamiento guardado");
        refrescarAparcamiento();
    }

    private String enlaceAparcamiento() {
        return String.format(java.util.Locale.US, "https://maps.google.com/?q=%.6f,%.6f",
                Ajustes.aparcLat(this), Ajustes.aparcLon(this));
    }

    private void comoLlegar() {
        if (!Ajustes.hayAparcamiento(this)) {
            toast("Primero guarda dónde has aparcado");
            return;
        }
        String url = String.format(java.util.Locale.US,
                "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f&travelmode=walking",
                Ajustes.aparcLat(this), Ajustes.aparcLon(this));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("No hay ninguna app de mapas");
        }
    }

    private void enviarWhatsApp() {
        if (!Ajustes.hayAparcamiento(this)) {
            toast("Primero guarda dónde has aparcado");
            return;
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, "Mi coche está aparcado aquí: " + enlaceAparcamiento());
        for (String app : new String[]{"com.whatsapp", "com.whatsapp.w4b"}) {
            try {
                i.setPackage(app);
                startActivity(i);
                return;
            } catch (Exception ignored) {
            }
        }
        i.setPackage(null);
        startActivity(Intent.createChooser(i, "Enviar ubicación"));
    }

    /** Cambia el texto del botón por el icono de la app instalada en el móvil (si lo está). */
    private void iconoApp(TextView boton, String paquete) {
        try {
            android.graphics.drawable.Drawable icono = getPackageManager().getApplicationIcon(paquete);
            int t = dp(26);
            icono.setBounds(0, 0, t, t);
            // Solo el icono, sin texto, centrado en el botón
            boton.setContentDescription(boton.getText());
            boton.setText("");
            boton.setCompoundDrawables(icono, null, null, null);
            boton.setCompoundDrawablePadding(0);
            boton.post(() -> boton.setPadding(Math.max(0, (boton.getWidth() - t) / 2), 0, 0, 0));
        } catch (PackageManager.NameNotFoundException ignored) {
            // App no instalada: el botón se queda solo con el texto
        }
    }

    private LinearLayout.LayoutParams pesoBoton() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    private Switch interruptor(String titulo, boolean activado) {
        Switch s = new Switch(this);
        s.setText(titulo);
        s.setTextColor(TEXTO);
        s.setTextSize(14);
        s.setChecked(activado);
        s.setPadding(0, dp(10), 0, dp(4));
        return s;
    }

    private LinearLayout tarjeta() {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        t.setBackground(redondo(TARJETA, BORDE, 16));
        return t;
    }

    private TextView botonPequeno(String s) {
        TextView b = texto(s, 14, TEXTO, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(redondo(TARJETA, BORDE, 25));
        return b;
    }

    private GradientDrawable redondo(int fondo, int borde, int radioDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fondo);
        g.setCornerRadius(dp(radioDp));
        g.setStroke(dp(1), borde);
        return g;
    }

    /** Hace que una vista reaccione al toque encogiéndose un poco. */
    private void pulsable(View v, View.OnClickListener l) {
        v.setClickable(true);
        v.setOnClickListener(l);
        v.setOnTouchListener((vista, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                vista.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
            } else if (e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL) {
                vista.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            }
            return false;
        });
    }

    private LinearLayout.LayoutParams margenArriba(int dpArriba) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(dpArriba);
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
