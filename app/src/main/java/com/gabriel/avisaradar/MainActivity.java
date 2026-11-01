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
    private TextView botonActivar, botonMapa;
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

        // Opciones
        LinearLayout opciones = tarjeta();
        opciones.addView(texto("Opciones", 16, TEXTO, true));
        Switch interMapa = interruptor("Usar mapa", "Mapa ligero con radares e incidencias",
                Ajustes.usarMapa(this));
        interMapa.setOnCheckedChangeListener((b, activado) -> {
            Ajustes.setUsarMapa(this, activado);
            refrescar();
        });
        opciones.addView(interMapa);
        Switch interTrafico = interruptor("Avisos de tráfico (DGT)",
                "Accidentes, retenciones, obstáculos y cortes", Ajustes.avisosTrafico(this));
        interTrafico.setOnCheckedChangeListener((b, activado) -> {
            Ajustes.setAvisosTrafico(this, activado);
            reiniciarSiActivo();
        });
        opciones.addView(interTrafico);
        col.addView(opciones, margenArriba(12));

        // Mis avisos
        LinearLayout fila = new LinearLayout(this);
        TextView anadir = botonPequeno("+  Aviso aquí");
        pulsable(anadir, v -> anadirAqui());
        TextView borrar = botonPequeno("Borrar mis avisos");
        pulsable(borrar, v -> confirmarBorrar());
        LinearLayout.LayoutParams lpA = new LinearLayout.LayoutParams(0, dp(50), 1f);
        lpA.rightMargin = dp(6);
        LinearLayout.LayoutParams lpB = new LinearLayout.LayoutParams(0, dp(50), 1f);
        lpB.leftMargin = dp(6);
        fila.addView(anadir, lpA);
        fila.addView(borrar, lpB);
        col.addView(fila, margenArriba(12));

        TextView ayuda = texto("Activa los avisos y abre Google Maps o Waze: AvisaRadar seguirá "
                + "avisando en segundo plano, también con la pantalla apagada. Para pararla, usa "
                + "«Parar» en la notificación.\n\nDatos de radares: © colaboradores de "
                + "OpenStreetMap (ODbL). Incidencias: DGT, Servei Català de Trànsit y "
                + "Dirección de Tráfico del Gobierno Vasco.", 12, SUB, false);
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

    private Switch interruptor(String titulo, String sub, boolean activado) {
        Switch s = new Switch(this);
        s.setText(titulo + "\n" + sub);
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
