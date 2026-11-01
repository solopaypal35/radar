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

public class MainActivity extends Pantalla {


    /** A partir de cuántos días se avisa de que hay que actualizar los radares. */
    static final int DIAS_ACTUALIZAR = 30;

    private TextView botonActivar, etiquetaActivar, estadoCabecera, aparcTexto;
    private TextView estadoMapa, estadoBurbuja;
    private Switch interMapa, interBurbuja;
    private VelocimetroView velocimetro;
    private double aparcLatMostrada = Double.NaN;
    private ImageView fotoAparc;
    private String fotoMostrada = null;
    static final int PEDIR_FOTO = 20;
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
        colorearBarras();

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(20), dp(18), dp(28));

        // Cabecera: saludo, fecha y estado, y botón de ajustes
        LinearLayout cabecera = new LinearLayout(this);
        cabecera.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titulos = new LinearLayout(this);
        titulos.setOrientation(LinearLayout.VERTICAL);
        titulos.addView(texto("¡Hola, Gabriel!", 20, TEXTO, true));
        estadoCabecera = texto("", 13, SUB, false);
        titulos.addView(estadoCabecera);
        cabecera.addView(titulos, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView ajustes = botonRedondo("⚙", 20);
        ajustes.setContentDescription("Ajustes");
        pulsable(ajustes, v -> startActivity(new Intent(this, AjustesActivity.class)));
        cabecera.addView(ajustes, new LinearLayout.LayoutParams(dp(50), dp(50)));
        col.addView(cabecera);

        // Velocímetro con el botón de encendido debajo
        LinearLayout panel = tarjeta();
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(14), dp(16), dp(14), dp(20));
        velocimetro = new VelocimetroView(this);
        panel.addView(velocimetro, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        botonActivar = texto("⏻", 28, 0xFFFFFFFF, true);
        botonActivar.setGravity(Gravity.CENTER);
        botonActivar.setContentDescription("Activar o parar avisos");
        pulsable(botonActivar, v -> alternar());
        LinearLayout.LayoutParams lpBoton = new LinearLayout.LayoutParams(dp(72), dp(72));
        lpBoton.topMargin = dp(10);
        panel.addView(botonActivar, lpBoton);
        etiquetaActivar = texto("", 13, SUB, false);
        etiquetaActivar.setPadding(0, dp(8), 0, 0);
        panel.addView(etiquetaActivar);
        col.addView(panel, margenArriba(16));

        // Pestañas: qué enseña la línea de estado del velocímetro
        android.widget.HorizontalScrollView pestanas = new android.widget.HorizontalScrollView(this);
        pestanas.setHorizontalScrollBarEnabled(false);
        TextView[] chips = new TextView[4];
        LinearLayout filaPestanas = filaChips(new String[]{"Radares", "Tramos", "Tráfico", "Peajes"},
                chips, 92, pos -> velocimetro.modo = pos);
        filaPestanas.setPadding(dp(2), dp(4), dp(2), dp(4));
        marcarChips(chips, 0);
        pestanas.addView(filaPestanas);
        col.addView(pestanas, margenArriba(16));

        // Aviso naranja: radares sin actualizar (solo aparece cuando toca)
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
        col.addView(tarjetaRadares, margenArriba(14));

        // Aviso naranja: batería (solo aparece si Android puede cerrar la app)
        tarjetaBateria = tarjeta();
        tarjetaBateria.setBackground(redondo(AMBAR_FONDO, AMBAR, 18));
        tarjetaBateria.addView(texto("Permite que funcione siempre", 16, AMBAR, true));
        TextView expl = texto("Si no, Android puede cerrar AvisaRadar en viajes largos. Toca aquí y pulsa «Permitir».",
                13, TEXTO, false);
        expl.setPadding(0, dp(4), 0, 0);
        tarjetaBateria.addView(expl);
        pulsable(tarjetaBateria, v -> pedirSinOptimizar());
        col.addView(tarjetaBateria, margenArriba(14));

        // Cuadrícula: Mapa | Burbuja
        LinearLayout cuadricula = new LinearLayout(this);
        LinearLayout tMapa = baldosa(R.drawable.ic_mapa, "Mapa");
        interMapa = interruptor("", Ajustes.usarMapa(this));
        estadoMapa = (TextView) ((LinearLayout) tMapa.getChildAt(2)).getChildAt(0);
        ((LinearLayout) tMapa.getChildAt(2)).addView(interMapa);
        interMapa.setOnCheckedChangeListener((b2, si) -> {
            Ajustes.setUsarMapa(this, si);
            refrescar();
        });
        pulsable(tMapa, v -> {
            if (Ajustes.usarMapa(this)) startActivity(new Intent(this, MapaActivity.class));
            else toast("Activa el mapa con el interruptor");
        });
        LinearLayout tBurbuja = baldosa(R.drawable.ic_burbuja, "Burbuja");
        interBurbuja = interruptor("", Ajustes.burbuja(this));
        estadoBurbuja = (TextView) ((LinearLayout) tBurbuja.getChildAt(2)).getChildAt(0);
        ((LinearLayout) tBurbuja.getChildAt(2)).addView(interBurbuja);
        interBurbuja.setOnCheckedChangeListener((b2, si) -> {
            Ajustes.setBurbuja(this, si);
            if (si && !Burbuja.tienePermiso(this)) {
                toast("Activa «Mostrar sobre otras apps» para AvisaRadar y vuelve");
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
            reiniciarSiActivo();
            refrescar();
        });
        LinearLayout.LayoutParams lpIzq = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpIzq.rightMargin = dp(7);
        LinearLayout.LayoutParams lpDer = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpDer.leftMargin = dp(7);
        cuadricula.addView(tMapa, lpIzq);
        cuadricula.addView(tBurbuja, lpDer);
        col.addView(cuadricula, margenArriba(16));

        // Aparcamiento
        LinearLayout aparc = tarjeta();
        LinearLayout cabAparc = new LinearLayout(this);
        cabAparc.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icAparc = new ImageView(this);
        icAparc.setImageResource(R.drawable.ic_parking);
        cabAparc.addView(icAparc, new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout textosAparc = new LinearLayout(this);
        textosAparc.setOrientation(LinearLayout.VERTICAL);
        textosAparc.setPadding(dp(12), 0, 0, 0);
        textosAparc.addView(texto("Aparcamiento", 16, TEXTO, true));
        aparcTexto = texto("", 12, SUB, false);
        textosAparc.addView(aparcTexto);
        cabAparc.addView(textosAparc, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        aparc.addView(cabAparc);
        fotoAparc = new ImageView(this);
        fotoAparc.setScaleType(ImageView.ScaleType.CENTER_CROP);
        fotoAparc.setBackground(redondo(HUNDIDO, HUNDIDO, 14));
        fotoAparc.setClipToOutline(true);
        fotoAparc.setVisibility(View.GONE);
        fotoAparc.setOnClickListener(v -> verFoto());
        LinearLayout.LayoutParams lpFoto = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(130));
        lpFoto.topMargin = dp(12);
        aparc.addView(fotoAparc, lpFoto);
        LinearLayout filaAparc = new LinearLayout(this);
        filaAparc.setPadding(0, dp(14), 0, 0);
        filaAparc.addView(botonAccion("📍", "Guardar", v -> guardarAparcamientoManual()), pesoAccion());
        filaAparc.addView(botonAccion("📷", "Foto", v -> hacerFoto()), pesoAccion());
        filaAparc.addView(botonAccion("➜", "Ruta", v -> comoLlegar()), pesoAccion());
        LinearLayout wa = botonAccion("✉", "Enviar", v -> enviarWhatsApp());
        iconoApp((TextView) wa.getChildAt(0), "com.whatsapp");
        filaAparc.addView(wa, pesoAccion());
        aparc.addView(filaAparc);
        col.addView(aparc, margenArriba(16));

        // Música
        LinearLayout musica = tarjeta();
        LinearLayout cabMusica = new LinearLayout(this);
        cabMusica.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icMusica = new ImageView(this);
        icMusica.setImageResource(R.drawable.ic_musica);
        cabMusica.addView(icMusica, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView tMusica = texto("Música", 16, TEXTO, true);
        tMusica.setPadding(dp(12), 0, 0, 0);
        cabMusica.addView(tMusica);
        musica.addView(cabMusica);
        LinearLayout filaMusica = new LinearLayout(this);
        filaMusica.setPadding(0, dp(14), 0, 0);
        filaMusica.addView(botonAccion("⏮", null, v -> Musica.anterior(this)), pesoAccion());
        LinearLayout play = botonAccion("⏯", null, v -> Musica.playPausa(this));
        ((TextView) play.getChildAt(0)).setBackground(redondo(VERDE, VERDE, 30));
        ((TextView) play.getChildAt(0)).setTextColor(0xFFFFFFFF);
        filaMusica.addView(play, pesoAccion());
        filaMusica.addView(botonAccion("⏭", null, v -> Musica.siguiente(this)), pesoAccion());
        LinearLayout sp = botonAccion("♫", null, v -> Musica.abrirSpotify(this));
        iconoApp((TextView) sp.getChildAt(0), Musica.SPOTIFY);
        filaMusica.addView(sp, pesoAccion());
        musica.addView(filaMusica);
        col.addView(musica, margenArriba(16));

        TextView ayuda = texto("Activa los avisos y abre Maps o Waze: AvisaRadar sigue avisando en segundo plano.",
                12, SUB, false);
        ayuda.setGravity(Gravity.CENTER);
        ayuda.setPadding(dp(8), dp(20), dp(8), 0);
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
        // Botón de encendido: cian relleno si está activo, con relieve si está parado
        boolean activo = RadarService.activo;
        if (activo) {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(VERDE);
            g.setStroke(dp(6), SOMBRA);
            botonActivar.setBackground(g);
            botonActivar.setTextColor(0xFFFFFFFF);
            etiquetaActivar.setText("Avisos activos · toca para parar");
        } else {
            botonActivar.setBackground(relieveOval());
            botonActivar.setTextColor(SUB);
            etiquetaActivar.setText("Toca para activar los avisos");
        }
        String fecha = new java.text.SimpleDateFormat("EEEE d MMM", new java.util.Locale("es", "ES"))
                .format(new java.util.Date());
        estadoCabecera.setText(Character.toUpperCase(fecha.charAt(0)) + fecha.substring(1)
                + (activo ? " · Avisos activos" : ""));
        estadoMapa.setText(Ajustes.usarMapa(this) ? "Toca para abrir" : "Apagado");
        estadoBurbuja.setText(Ajustes.burbuja(this) ? "Encendida" : "Apagada");

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
            // La tarjeta solo aparece cuando hay que actualizar (si no, está en Ajustes)
            tarjetaRadares.setVisibility(toca ? View.VISIBLE : View.GONE);
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

    /** Baldosa cuadrada con icono, título y una fila inferior (estado + interruptor). */
    private LinearLayout baldosa(int icono, String titulo) {
        LinearLayout t = tarjeta();
        t.setPadding(dp(18), dp(16), dp(14), dp(10));
        ImageView ic = new ImageView(this);
        ic.setImageResource(icono);
        LinearLayout.LayoutParams lpIc = new LinearLayout.LayoutParams(dp(28), dp(28));
        t.addView(ic, lpIc);
        TextView tt = texto(titulo, 16, TEXTO, true);
        tt.setPadding(0, dp(8), 0, dp(2));
        t.addView(tt);
        LinearLayout abajo = new LinearLayout(this);
        abajo.setGravity(Gravity.CENTER_VERTICAL);
        TextView estado = texto("", 12, SUB, false);
        abajo.addView(estado, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        t.addView(abajo);
        return t;
    }

    /** Botón redondo con relieve y, opcionalmente, una etiqueta pequeña debajo. */
    private LinearLayout botonAccion(String simbolo, String etiqueta, View.OnClickListener l) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView b = botonRedondo(simbolo, 18);
        pulsable(b, l);
        c.addView(b, new LinearLayout.LayoutParams(dp(52), dp(52)));
        if (etiqueta != null) {
            TextView e = texto(etiqueta, 11, SUB, false);
            e.setPadding(0, dp(4), 0, 0);
            c.addView(e);
        }
        return c;
    }

    private LinearLayout.LayoutParams pesoAccion() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
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
        if (code == 5) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) hacerFoto();
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

    private void pedirSinOptimizar() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    // ---------- Piezas de la interfaz ----------

    // ---------- Dónde he aparcado ----------

    private void refrescarAparcamiento() {
        refrescarFoto();
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
        Uri foto = fotoUri();
        Intent i = new Intent(Intent.ACTION_SEND);
        i.putExtra(Intent.EXTRA_TEXT, "Mi coche está aparcado aquí: " + enlaceAparcamiento());
        if (foto != null) {
            i.setType("image/jpeg");
            i.putExtra(Intent.EXTRA_STREAM, foto);
            i.setClipData(android.content.ClipData.newRawUri("Aparcamiento", foto));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            i.setType("text/plain");
        }
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

    // ---------- Foto del aparcamiento ----------
    // La foto se guarda en la galería (álbum «AvisaRadar»), para que la cámara pueda
    // escribirla y WhatsApp leerla sin problemas en cualquier móvil.

    private Uri fotoPendiente;

    /** La foto del aparcamiento actual, o null si no hay (o se ha borrado de la galería). */
    private Uri fotoUri() {
        String s = Ajustes.aparcFoto(this);
        if (s == null) return null;
        Uri u = Uri.parse(s);
        try (java.io.InputStream in = getContentResolver().openInputStream(u)) {
            return in != null && in.read() != -1 ? u : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void hacerFoto() {
        if (!Ajustes.hayAparcamiento(this)) {
            toast("Primero guarda dónde has aparcado");
            return;
        }
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 5);
            return;
        }
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                "aparcamiento_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                        .format(new java.util.Date()) + ".jpg");
        v.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= 29) {
            v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AvisaRadar");
        }
        try {
            fotoPendiente = getContentResolver().insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        } catch (Exception e) {
            fotoPendiente = null;
        }
        if (fotoPendiente == null) {
            toast("No se pudo preparar la foto");
            return;
        }
        Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, fotoPendiente);
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(i, PEDIR_FOTO);
        } catch (Exception e) {
            borrarPendiente();
            toast("No se encontró la cámara");
        }
    }

    private void borrarPendiente() {
        if (fotoPendiente != null) {
            try {
                getContentResolver().delete(fotoPendiente, null, null);
            } catch (Exception ignored) {
            }
            fotoPendiente = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle estado) {
        super.onSaveInstanceState(estado);
        if (fotoPendiente != null) estado.putString("foto_pendiente", fotoPendiente.toString());
    }

    @Override
    protected void onRestoreInstanceState(Bundle estado) {
        super.onRestoreInstanceState(estado);
        String s = estado.getString("foto_pendiente");
        if (s != null) fotoPendiente = Uri.parse(s);
    }

    @Override
    protected void onActivityResult(int code, int resultado, Intent datos) {
        super.onActivityResult(code, resultado, datos);
        if (code != PEDIR_FOTO) return;
        if (resultado == RESULT_OK && fotoPendiente != null) {
            Ajustes.setAparcFoto(this, fotoPendiente.toString());
            fotoPendiente = null;
            toast("Foto guardada con el aparcamiento");
        } else {
            borrarPendiente(); // se canceló: no dejar una foto vacía en la galería
        }
        fotoMostrada = null;
        refrescarFoto();
    }

    /** Muestra la miniatura si hay foto (solo vuelve a leerla si ha cambiado). */
    private void refrescarFoto() {
        String actual = Ajustes.aparcFoto(this);
        if (actual == null) actual = "";
        if (actual.equals(fotoMostrada)) return;
        fotoMostrada = actual;
        Uri u = fotoUri();
        android.graphics.Bitmap b = null;
        if (u != null) {
            try (java.io.InputStream in = getContentResolver().openInputStream(u)) {
                android.graphics.BitmapFactory.Options op = new android.graphics.BitmapFactory.Options();
                op.inSampleSize = 4; // miniatura ligera
                b = android.graphics.BitmapFactory.decodeStream(in, null, op);
            } catch (Exception ignored) {
            }
        }
        if (b == null) {
            fotoAparc.setImageDrawable(null);
            fotoAparc.setVisibility(View.GONE);
            return;
        }
        fotoAparc.setImageBitmap(rotarSegunExif(u, b));
        fotoAparc.setVisibility(View.VISIBLE);
    }

    private android.graphics.Bitmap rotarSegunExif(Uri u, android.graphics.Bitmap b) {
        try (java.io.InputStream in = getContentResolver().openInputStream(u)) {
            int o = new android.media.ExifInterface(in).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL);
            int grados = o == android.media.ExifInterface.ORIENTATION_ROTATE_90 ? 90
                    : o == android.media.ExifInterface.ORIENTATION_ROTATE_180 ? 180
                    : o == android.media.ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            if (grados == 0) return b;
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.postRotate(grados);
            return android.graphics.Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        } catch (Exception e) {
            return b;
        }
    }

    private void verFoto() {
        Uri u = fotoUri();
        if (u == null) return;
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(u, "image/jpeg");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(i);
        } catch (Exception e) {
            toast("No hay ninguna app para ver fotos");
        }
    }

    // ---------- Distancias de aviso ----------

}
