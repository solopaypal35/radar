package com.gabriel.avisaradar;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Colores y piezas de interfaz comunes a todas las pantallas de la app. */
public class Pantalla extends Activity {

    static final int FONDO = 0xFF0B1220;
    static final int TARJETA = 0xFF16213A;
    static final int BORDE = 0xFF24324F;
    static final int TEXTO = 0xFFE8EEF8;
    static final int SUB = 0xFF8A9BB8;
    static final int VERDE = 0xFF2EE6A6;
    static final int AMBAR = 0xFFFFB020;
    static final int AMBAR_FONDO = 0xFF3A2A08;
    static final int ROJO = 0xFFFF4D5E;

    protected final Handler handler = new Handler(Looper.getMainLooper());

    protected void colorearBarras() {
        getWindow().setStatusBarColor(FONDO);
        getWindow().setNavigationBarColor(FONDO);
    }

    /** Si los avisos están activos, los reinicia para que lean los ajustes nuevos. */
    protected void reiniciarSiActivo() {
        if (RadarService.activo) {
            stopService(new Intent(this, RadarService.class));
            handler.postDelayed(() -> startForegroundService(new Intent(this, RadarService.class)), 500);
        }
    }

    protected TextView texto(String s, int sp, int color, boolean negrita) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (negrita) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    /** Título pequeño de sección, en gris y mayúsculas. */
    protected TextView seccion(String s) {
        TextView t = texto(s.toUpperCase(), 12, SUB, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(6), dp(22), 0, dp(8));
        return t;
    }

    protected LinearLayout tarjeta() {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        t.setBackground(redondo(TARJETA, BORDE, 16));
        return t;
    }

    /** Línea fina para separar filas dentro de una tarjeta. */
    protected View separador() {
        View v = new View(this);
        v.setBackgroundColor(BORDE);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return v;
    }

    /** Fila con un texto a la izquierda (y uno pequeño debajo opcional) y una vista a la derecha. */
    protected LinearLayout fila(String titulo, String sub, View derecha) {
        LinearLayout f = new LinearLayout(this);
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setPadding(0, dp(12), 0, dp(12));
        LinearLayout textos = new LinearLayout(this);
        textos.setOrientation(LinearLayout.VERTICAL);
        textos.addView(texto(titulo, 15, TEXTO, false));
        if (sub != null) textos.addView(texto(sub, 12, SUB, false));
        f.addView(textos, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (derecha != null) f.addView(derecha);
        return f;
    }

    protected Switch interruptor(String titulo, boolean activado) {
        Switch s = new Switch(this);
        s.setText(titulo);
        s.setTextColor(TEXTO);
        s.setTextSize(15);
        s.setChecked(activado);
        s.setPadding(0, dp(12), 0, dp(12));
        return s;
    }

    protected TextView botonPequeno(String s) {
        TextView b = texto(s, 14, TEXTO, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(redondo(TARJETA, BORDE, 25));
        return b;
    }

    /** Pinta un grupo de botones tipo «chip»: el elegido en verde, el resto apagados. */
    protected void marcarChips(TextView[] chips, int elegido) {
        for (int i = 0; i < chips.length; i++) {
            boolean si = i == elegido;
            chips[i].setBackground(redondo(si ? VERDE : TARJETA, si ? VERDE : BORDE, 18));
            chips[i].setTextColor(si ? FONDO : TEXTO);
        }
    }

    /** Crea una fila de chips; al tocar uno se llama a alElegir con su posición. */
    protected LinearLayout filaChips(String[] nombres, TextView[] chips, int anchoDp, java.util.function.IntConsumer alElegir) {
        LinearLayout f = new LinearLayout(this);
        f.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < nombres.length; i++) {
            final int pos = i;
            TextView chip = botonPequeno(nombres[i]);
            chip.setTextSize(13);
            pulsable(chip, v -> {
                alElegir.accept(pos);
                marcarChips(chips, pos);
            });
            chips[i] = chip;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(anchoDp), dp(36));
            if (i > 0) lp.leftMargin = dp(6);
            f.addView(chip, lp);
        }
        return f;
    }

    protected GradientDrawable redondo(int fondo, int borde, int radioDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fondo);
        g.setCornerRadius(dp(radioDp));
        g.setStroke(dp(1), borde);
        return g;
    }

    /** Hace que una vista reaccione al toque encogiéndose un poco. */
    protected void pulsable(View v, View.OnClickListener l) {
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

    /** Cambia el texto del botón por el icono de la app instalada en el móvil (si lo está). */
    protected void iconoApp(TextView boton, String paquete) {
        try {
            android.graphics.drawable.Drawable icono = getPackageManager().getApplicationIcon(paquete);
            int t = dp(26);
            icono.setBounds(0, 0, t, t);
            boton.setContentDescription(boton.getText());
            boton.setText("");
            boton.setCompoundDrawables(icono, null, null, null);
            boton.setCompoundDrawablePadding(0);
            boton.post(() -> boton.setPadding(Math.max(0, (boton.getWidth() - t) / 2), 0, 0, 0));
        } catch (PackageManager.NameNotFoundException ignored) {
            // App no instalada: el botón se queda con el texto
        }
    }

    protected LinearLayout.LayoutParams pesoBoton() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    protected LinearLayout.LayoutParams margenArriba(int dpArriba) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(dpArriba);
        return lp;
    }

    protected int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    protected void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
