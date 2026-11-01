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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Burbuja flotante encima de otras apps (Maps, Waze…): velocidad, límite y próximo radar.
 * Se puede arrastrar. Al tocarla se despliegan los controles de música.
 */
public class Burbuja {

    private final Context ctx;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout raiz;
    private Circulo circulo;
    private LinearLayout panel;
    private WindowManager.LayoutParams lp;
    private boolean visible = false;

    private final Runnable tic = new Runnable() {
        @Override
        public void run() {
            if (circulo != null) circulo.invalidate();
            handler.postDelayed(this, 500);
        }
    };

    public Burbuja(Context c) {
        ctx = c;
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
    }

    public static boolean tienePermiso(Context c) {
        return Settings.canDrawOverlays(c);
    }

    public void mostrar() {
        if (visible || !tienePermiso(ctx)) return;
        raiz = new LinearLayout(ctx);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setGravity(Gravity.CENTER_HORIZONTAL);

        circulo = new Circulo(ctx);
        raiz.addView(circulo, new LinearLayout.LayoutParams(dp(84), dp(84)));

        panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable fondo = new GradientDrawable();
        fondo.setColor(0xEE16213A);
        fondo.setCornerRadius(dp(22));
        fondo.setStroke(dp(1), MainActivity.BORDE);
        panel.setBackground(fondo);
        panel.setPadding(dp(4), dp(2), dp(4), dp(2));
        panel.addView(boton("⏮", v -> Musica.anterior(ctx)));
        panel.addView(boton("⏯", v -> Musica.playPausa(ctx)));
        panel.addView(boton("⏭", v -> Musica.siguiente(ctx)));
        panel.addView(boton("▣", v -> {
            Intent i = new Intent(ctx, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            ctx.startActivity(i);
        }));
        panel.setVisibility(View.GONE);
        LinearLayout.LayoutParams lpPanel = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(44));
        lpPanel.topMargin = dp(6);
        raiz.addView(panel, lpPanel);

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
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
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) movido = true;
                        if (movido) {
                            lp.x = lx0 + (int) dx;
                            lp.y = ly0 + (int) dy;
                            try {
                                wm.updateViewLayout(raiz, lp);
                            } catch (Exception ignored) {
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (movido) {
                            Ajustes.setBurbujaPos(ctx, lp.x, lp.y);
                        } else {
                            panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });

        try {
            wm.addView(raiz, lp);
            visible = true;
            handler.post(tic);
        } catch (Exception e) {
            visible = false;
        }
    }

    public void ocultar() {
        handler.removeCallbacks(tic);
        if (visible && raiz != null) {
            try {
                wm.removeView(raiz);
            } catch (Exception ignored) {
            }
        }
        visible = false;
    }

    private TextView boton(String s, View.OnClickListener l) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(20);
        t.setTextColor(MainActivity.TEXTO);
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(l);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(46), LinearLayout.LayoutParams.MATCH_PARENT));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    /** El círculo: velocidad grande, señal de límite y distancia al radar. */
    static class Circulo extends View {
        final Paint fondo = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint borde = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint texto = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint senal = new Paint(Paint.ANTI_ALIAS_FLAG);

        Circulo(Context c) {
            super(c);
            borde.setStyle(Paint.Style.STROKE);
            texto.setTextAlign(Paint.Align.CENTER);
            texto.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            senal.setTextAlign(Paint.Align.CENTER);
            senal.setTypeface(Typeface.DEFAULT_BOLD);
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f - 3 * d;

            int lim = RadarService.limiteMostrado();
            boolean rapido = RadarService.exceso
                    || (lim > 0 && RadarService.velocidad > lim + 3);
            int color = rapido ? RadarView.ROJO
                    : RadarService.distancia >= 0 || RadarService.enTramo ? RadarView.AMBAR : RadarView.VERDE;

            fondo.setColor(0xEE0B1220);
            c.drawCircle(cx, cy, r, fondo);
            borde.setColor(color);
            borde.setStrokeWidth(4 * d);
            c.drawCircle(cx, cy, r, borde);

            texto.setColor(rapido ? RadarView.ROJO : Color.WHITE);
            texto.setTextSize(26 * d);
            c.drawText(RadarService.hayGps ? String.valueOf(RadarService.velocidad) : "--",
                    cx, cy + 6 * d, texto);

            texto.setTextSize(10 * d);
            texto.setColor(color);
            String abajo;
            if (RadarService.enTramo) {
                abajo = "media " + RadarService.mediaTramo;
            } else if (RadarService.distancia >= 0) {
                abajo = (Math.round(RadarService.distancia / 10f) * 10) + " m";
            } else {
                abajo = "km/h";
            }
            c.drawText(abajo, cx, cy + 22 * d, texto);

            // Señal de límite en la esquina
            if (lim > 0) {
                float sr = 13 * d, sx = w - sr - 1 * d, sy = sr + 1 * d;
                senal.setColor(Color.WHITE);
                senal.setStyle(Paint.Style.FILL);
                c.drawCircle(sx, sy, sr, senal);
                senal.setStyle(Paint.Style.STROKE);
                senal.setStrokeWidth(3 * d);
                senal.setColor(0xFFE0262F);
                c.drawCircle(sx, sy, sr - 1.5f * d, senal);
                senal.setStyle(Paint.Style.FILL);
                senal.setColor(Color.BLACK);
                senal.setTextSize(lim >= 100 ? 9 * d : 11 * d);
                c.drawText(String.valueOf(lim), sx, sy + 4 * d, senal);
            }
        }
    }
}
