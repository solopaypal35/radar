package com.gabriel.avisaradar;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * Burbuja flotante encima de otras apps (Maps, Waze…), con el mismo estilo que el velocímetro
 * de la app: fondo negro, velocidad en números de puntos y anillo de puntos.
 * Se arrastra con el dedo. Al tocarla aparecen los controles en una ventana aparte,
 * así la burbuja no se mueve de sitio.
 */
public class Burbuja {

    private final Context ctx;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Circulo circulo;
    private LinearLayout panel;
    private WindowManager.LayoutParams lp, lpPanel;
    private boolean visible = false, panelVisible = false;

    private final Runnable tic = new Runnable() {
        @Override
        public void run() {
            if (circulo != null) circulo.invalidate();
            handler.postDelayed(this, 250);
        }
    };

    public Burbuja(Context c) {
        ctx = c;
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
    }

    public static boolean tienePermiso(Context c) {
        return Settings.canDrawOverlays(c);
    }

    private static WindowManager.LayoutParams parametros() {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        return p;
    }

    public void mostrar() {
        if (visible || !tienePermiso(ctx)) return;

        // ---------- El círculo (ventana propia, tamaño fijo) ----------
        circulo = new Circulo(ctx);
        lp = parametros();
        lp.width = dp(96);
        lp.height = dp(96);
        lp.x = Ajustes.burbujaX(ctx);
        lp.y = Ajustes.burbujaY(ctx);

        circulo.setOnTouchListener(new View.OnTouchListener() {
            float x0, y0;
            int lx0, ly0;
            boolean movido;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        x0 = e.getRawX();
                        y0 = e.getRawY();
                        lx0 = lp.x;
                        ly0 = lp.y;
                        movido = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - x0, dy = e.getRawY() - y0;
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) {
                            if (!movido) ocultarPanel();
                            movido = true;
                        }
                        if (movido) {
                            lp.x = lx0 + (int) dx;
                            lp.y = ly0 + (int) dy;
                            try {
                                wm.updateViewLayout(circulo, lp);
                            } catch (Exception ignored) {
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (movido) {
                            Ajustes.setBurbujaPos(ctx, lp.x, lp.y);
                        } else if (panelVisible) {
                            ocultarPanel();
                        } else {
                            mostrarPanel();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });

        // ---------- Panel de controles (ventana aparte, debajo del círculo) ----------
        panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable fondo = new GradientDrawable();
        fondo.setColor(0xF2131718);
        fondo.setCornerRadius(dp(28));
        fondo.setStroke(dp(1), Pantalla.BORDE);
        panel.setBackground(fondo);
        panel.setPadding(dp(6), dp(6), dp(6), dp(6));
        panel.addView(boton(R.drawable.ic_anterior, Pantalla.TEXTO, false, v -> Musica.anterior(ctx)));
        panel.addView(boton(R.drawable.ic_play, 0xFF061012, true, v -> Musica.playPausa(ctx)));
        panel.addView(boton(R.drawable.ic_siguiente, Pantalla.TEXTO, false, v -> Musica.siguiente(ctx)));
        panel.addView(boton(R.drawable.ic_inicio, Pantalla.TEXTO, false, v -> {
            ocultarPanel();
            Intent i = new Intent(ctx, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            ctx.startActivity(i);
        }));
        panel.addView(boton(R.drawable.ic_cerrar, Pantalla.ROJO, false, v -> ocultar()));
        lpPanel = parametros();

        try {
            wm.addView(circulo, lp);
            visible = true;
            handler.post(tic);
        } catch (Exception e) {
            visible = false;
        }
    }

    /** Coloca el panel centrado bajo el círculo (o encima, si no cabe), sin salirse de la pantalla. */
    private void mostrarPanel() {
        if (panelVisible) return;
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int pw = panel.getMeasuredWidth(), ph = panel.getMeasuredHeight();
        int x = lp.x + lp.width / 2 - pw / 2;
        x = Math.max(dp(8), Math.min(x, dm.widthPixels - pw - dp(8)));
        int y = lp.y + lp.height + dp(6);
        if (y + ph > dm.heightPixels - dp(40)) y = lp.y - ph - dp(6);
        lpPanel.x = x;
        lpPanel.y = y;
        try {
            wm.addView(panel, lpPanel);
            panelVisible = true;
        } catch (Exception ignored) {
        }
    }

    private void ocultarPanel() {
        if (!panelVisible) return;
        try {
            wm.removeView(panel);
        } catch (Exception ignored) {
        }
        panelVisible = false;
    }

    public void ocultar() {
        handler.removeCallbacks(tic);
        ocultarPanel();
        if (visible && circulo != null) {
            try {
                wm.removeView(circulo);
            } catch (Exception ignored) {
            }
        }
        visible = false;
    }

    /** Botón redondo con icono centrado. */
    private FrameLayout boton(int icono, int color, boolean principal, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(ctx);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(principal ? Pantalla.VERDE : 0x00000000);
        f.setBackground(g);
        ImageView i = new ImageView(ctx);
        i.setImageResource(icono);
        i.setColorFilter(color);
        f.addView(i, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        f.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(44), dp(44));
        p.setMargins(dp(2), 0, dp(2), 0);
        f.setLayoutParams(p);
        return f;
    }

    private int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    /** El círculo: fondo negro, anillo de puntos y velocidad con números de puntos. */
    static class Circulo extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint texto = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint senal = new Paint(Paint.ANTI_ALIAS_FLAG);

        Circulo(Context c) {
            super(c);
            texto.setTextAlign(Paint.Align.CENTER);
            senal.setTextAlign(Paint.Align.CENTER);
            senal.setTypeface(Typeface.DEFAULT_BOLD);
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f - 2 * d;
            int color = VelocimetroView.colorEstado();
            boolean activo = RadarService.activo && RadarService.hayGps;
            int vel = RadarService.velocidad;

            // Fondo negro con borde fino
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xF207090A);
            c.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.2f * d);
            p.setColor(Pantalla.BORDE);
            c.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.FILL);

            // Anillo de puntos (270°, abierto abajo) que se enciende con la velocidad
            int n = 24;
            int on = activo ? Math.round(Math.min(1f, vel / (float) VelocimetroView.VELOCIDAD_MAX) * n) : 0;
            float ra = r - 7 * d;
            for (int i = 0; i < n; i++) {
                double a = Math.toRadians(135 + 270.0 * i / (n - 1));
                p.setColor(i < on ? color : Pantalla.HUNDIDO);
                c.drawCircle(cx + ra * (float) Math.cos(a), cy + ra * (float) Math.sin(a), (i < on ? 2.3f : 1.8f) * d, p);
            }

            // Velocidad en números de puntos
            String v = activo ? String.valueOf(vel) : "--";
            float paso = 3.3f * d, radio = paso * 0.38f;
            float anchoDig = 5 * paso, hueco = paso * 1.1f;
            float ancho = v.length() * anchoDig + (v.length() - 1) * hueco;
            float x0 = cx - ancho / 2f + paso / 2f, y0 = cy - 3.5f * paso - 3 * d;
            p.setColor(color == Pantalla.ROJO ? Pantalla.ROJO : Color.WHITE);
            for (int k = 0; k < v.length(); k++) {
                char ch = v.charAt(k);
                String[] g = VelocimetroView.DIGITOS[ch == '-' ? 10 : ch - '0'];
                for (int fila = 0; fila < 7; fila++) {
                    for (int col = 0; col < 5; col++) {
                        if (g[fila].charAt(col) == '1') {
                            c.drawCircle(x0 + k * (anchoDig + hueco) + col * paso, y0 + fila * paso, radio, p);
                        }
                    }
                }
            }

            // Debajo: distancia al radar, media del tramo o «km/h»
            String abajo;
            if (RadarService.enTramo) abajo = "media " + RadarService.mediaTramo;
            else if (RadarService.distancia >= 0) abajo = (Math.round(RadarService.distancia / 10f) * 10) + " m";
            else abajo = "km/h";
            texto.setColor(abajo.equals("km/h") ? Pantalla.SUB : color);
            texto.setTextSize(10 * d);
            texto.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            c.drawText(abajo, cx, y0 + 7 * paso + 11 * d, texto);

            // Señal de límite pequeña, arriba a la derecha
            int lim = RadarService.limiteMostrado();
            if (RadarService.activo && lim > 0) {
                float sr = 12 * d, sx = w - sr - 1 * d, sy = sr + 1 * d;
                senal.setStyle(Paint.Style.FILL);
                senal.setColor(Color.WHITE);
                c.drawCircle(sx, sy, sr, senal);
                senal.setStyle(Paint.Style.STROKE);
                senal.setStrokeWidth(2.6f * d);
                senal.setColor(0xFFE0262F);
                c.drawCircle(sx, sy, sr - 1.3f * d, senal);
                senal.setStyle(Paint.Style.FILL);
                senal.setColor(Color.BLACK);
                senal.setTextSize(lim >= 100 ? 8.5f * d : 10.5f * d);
                Paint.FontMetrics fm = senal.getFontMetrics();
                c.drawText(String.valueOf(lim), sx, sy - (fm.ascent + fm.descent) / 2f, senal);
            }
        }
    }
}
