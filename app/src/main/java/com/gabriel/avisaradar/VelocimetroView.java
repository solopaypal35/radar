package com.gabriel.avisaradar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/**
 * Velocímetro circular: arco de fondo hundido, arco cian que crece con la velocidad,
 * velocidad en el centro, señal del límite y una línea de estado según la pestaña elegida.
 */
public class VelocimetroView extends View {

    public static final int MODO_RADARES = 0, MODO_TRAMOS = 1, MODO_TRAFICO = 2, MODO_PEAJES = 3;
    public int modo = MODO_RADARES;

    static final float INICIO = 135f, BARRIDO = 270f; // arco de 270°, abierto por abajo
    static final int VELOCIDAD_MAX = 160;

    private final Paint pista = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arco = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint relleno = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numero = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint texto = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint senal = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF caja = new RectF();
    private float mostrada = 0; // velocidad animada (suaviza los saltos del GPS)
    private final long inicio = System.currentTimeMillis();

    public VelocimetroView(Context c) {
        super(c);
        pista.setStyle(Paint.Style.STROKE);
        pista.setStrokeCap(Paint.Cap.ROUND);
        arco.setStyle(Paint.Style.STROKE);
        arco.setStrokeCap(Paint.Cap.ROUND);
        numero.setTextAlign(Paint.Align.CENTER);
        numero.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        texto.setTextAlign(Paint.Align.CENTER);
        senal.setTextAlign(Paint.Align.CENTER);
        senal.setTypeface(Typeface.DEFAULT_BOLD);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, (int) (w * 0.92f));
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h * 0.47f;
        float r = Math.min(w, h) * 0.38f;
        float grosor = 16 * d;
        float t = (System.currentTimeMillis() - inicio) / 1000f;

        boolean activo = RadarService.activo, gps = RadarService.hayGps;
        int lim = RadarService.limiteMostrado();
        boolean exceso = RadarService.exceso || (activo && gps && lim > 0 && RadarService.velocidad > lim + 3);
        int color = !activo ? Pantalla.SUB : exceso ? Pantalla.ROJO
                : (RadarService.distancia >= 0 || RadarService.enTramo) ? Pantalla.AMBAR : Pantalla.VERDE;

        // Velocidad animada hacia la real
        float objetivo = activo && gps ? RadarService.velocidad : 0;
        mostrada += (objetivo - mostrada) * 0.15f;

        // Disco central
        relleno.setColor(Pantalla.FONDO);
        c.drawCircle(cx, cy, r + grosor, relleno);

        // Pista hundida
        caja.set(cx - r, cy - r, cx + r, cy + r);
        pista.setColor(Pantalla.HUNDIDO);
        pista.setStrokeWidth(grosor);
        c.drawArc(caja, INICIO, BARRIDO, false, pista);

        // Arco de velocidad (late un poco si vas rápido)
        float frac = Math.min(1f, mostrada / VELOCIDAD_MAX);
        if (frac > 0.005f) {
            arco.setColor(color);
            arco.setStrokeWidth(grosor * (exceso ? 1f + 0.08f * (float) Math.abs(Math.sin(t * 6)) : 1f));
            c.drawArc(caja, INICIO, BARRIDO * frac, false, arco);
            double ang = Math.toRadians(INICIO + BARRIDO * frac);
            relleno.setColor(Color.WHITE);
            c.drawCircle(cx + r * (float) Math.cos(ang), cy + r * (float) Math.sin(ang), grosor * 0.38f, relleno);
        }

        // Marca del límite sobre la pista
        if (activo && lim > 0 && lim <= VELOCIDAD_MAX) {
            double a = Math.toRadians(INICIO + BARRIDO * lim / (float) VELOCIDAD_MAX);
            float x1 = cx + (r - grosor * 0.9f) * (float) Math.cos(a), y1 = cy + (r - grosor * 0.9f) * (float) Math.sin(a);
            float x2 = cx + (r + grosor * 0.9f) * (float) Math.cos(a), y2 = cy + (r + grosor * 0.9f) * (float) Math.sin(a);
            Paint marca = new Paint(Paint.ANTI_ALIAS_FLAG);
            marca.setColor(0xFFE0262F);
            marca.setStrokeWidth(3 * d);
            c.drawLine(x1, y1, x2, y2, marca);
        }

        // Centro
        relleno.setColor(Pantalla.TARJETA);
        c.drawCircle(cx, cy, r * 0.62f, relleno);
        numero.setColor(exceso ? Pantalla.ROJO : Pantalla.TEXTO);
        numero.setTextSize(r * 0.5f);
        c.drawText(activo && gps ? String.valueOf(Math.round(mostrada)) : "--", cx, cy + r * 0.12f, numero);
        texto.setColor(Pantalla.SUB);
        texto.setTextSize(11 * d);
        texto.setTypeface(Typeface.DEFAULT);
        c.drawText("KM/H", cx, cy + r * 0.32f, texto);

        // Señal de límite arriba a la derecha
        if (activo && lim > 0) {
            float sr = 22 * d, sx = w - sr - 4 * d, sy = sr + 4 * d;
            senal.setStyle(Paint.Style.FILL);
            senal.setColor(Color.WHITE);
            c.drawCircle(sx, sy, sr, senal);
            senal.setStyle(Paint.Style.STROKE);
            senal.setStrokeWidth(4.5f * d);
            senal.setColor(0xFFE0262F);
            c.drawCircle(sx, sy, sr - 2.3f * d, senal);
            senal.setStyle(Paint.Style.FILL);
            senal.setColor(Color.BLACK);
            senal.setTextSize(sr * (lim >= 100 ? 0.72f : 0.88f));
            c.drawText(String.valueOf(lim), sx, sy + sr * 0.31f, senal);
        }

        // Línea de estado bajo el velocímetro, según la pestaña
        String linea;
        int colorLinea = Pantalla.VERDE;
        if (!activo) {
            linea = "Avisos parados";
            colorLinea = Pantalla.SUB;
        } else if (!gps) {
            linea = "Buscando GPS…";
            colorLinea = Pantalla.SUB;
        } else {
            switch (modo) {
                case MODO_TRAMOS:
                    if (RadarService.enTramo) {
                        int m = RadarService.mediaTramo;
                        boolean pasado = RadarService.limiteTramo > 0 && m > RadarService.limiteTramo;
                        linea = "Tramo · media " + (m > 0 ? m + " km/h" : "--");
                        colorLinea = pasado ? Pantalla.ROJO : Pantalla.AMBAR;
                    } else {
                        linea = "Fuera de tramo";
                    }
                    break;
                case MODO_TRAFICO:
                    String inc = RadarService.incidenciaTexto;
                    linea = inc != null ? inc : "Sin incidencias delante";
                    if (inc != null) colorLinea = Pantalla.AMBAR;
                    break;
                case MODO_PEAJES:
                    String p = RadarService.peajeTexto;
                    linea = p != null ? p : "Sin peajes cerca";
                    break;
                default:
                    int dist = RadarService.distancia;
                    if (dist >= 0) {
                        linea = (exceso ? "¡Reduce! " : "") + "Radar a " + (Math.round(dist / 10f) * 10) + " m";
                        colorLinea = exceso ? Pantalla.ROJO : Pantalla.AMBAR;
                    } else {
                        linea = "Vía libre";
                    }
                    break;
            }
        }
        texto.setColor(colorLinea);
        texto.setTextSize(17 * d);
        texto.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        c.drawText(linea, cx, h - 12 * d, texto);

        postInvalidateOnAnimation();
    }
}
