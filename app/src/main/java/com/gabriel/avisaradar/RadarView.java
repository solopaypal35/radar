package com.gabriel.avisaradar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.View;

/** Pantalla de radar animada: velocidad en el centro, barrido giratorio y radar de delante. */
public class RadarView extends View {

    static final int VERDE = 0xFF2EE6A6;
    static final int AMBAR = 0xFFFFB020;
    static final int ROJO = 0xFFFF4D5E;
    static final int GRIS = 0xFF4A5876;
    static final int TEXTO = 0xFFE8EEF8;
    static final int SUB = 0xFF8A9BB8;

    private final Paint anillo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barrido = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linea = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint punto = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numero = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint texto = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint senal = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matriz = new Matrix();
    private float angulo = 0;
    private int colorShader = 0;
    private SweepGradient shader;
    private final long inicio = System.currentTimeMillis();

    public RadarView(Context c) {
        super(c);
        anillo.setStyle(Paint.Style.STROKE);
        linea.setStyle(Paint.Style.STROKE);
        linea.setStrokeCap(Paint.Cap.ROUND);
        numero.setTextAlign(Paint.Align.CENTER);
        numero.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        texto.setTextAlign(Paint.Align.CENTER);
        senal.setTextAlign(Paint.Align.CENTER);
        senal.setTypeface(Typeface.DEFAULT_BOLD);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, (int) (w * 0.95f));
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f * 0.86f;
        float d = getResources().getDisplayMetrics().density;
        float t = (System.currentTimeMillis() - inicio) / 1000f;

        boolean activo = RadarService.activo;
        boolean gps = RadarService.hayGps;
        int dist = RadarService.distancia;
        int lim = RadarService.limite;
        boolean exceso = RadarService.exceso;

        int color = !activo ? GRIS : exceso ? ROJO : (dist >= 0 ? AMBAR : VERDE);

        // Latido cuando vas rápido cerca de un radar
        float escala = exceso ? 1f + 0.025f * (float) Math.sin(t * 9) : 1f;
        float rr = r * escala;

        // Anillos
        for (int i = 1; i <= 4; i++) {
            anillo.setColor(conAlfa(color, i == 4 ? 200 : 70));
            anillo.setStrokeWidth((i == 4 ? 3f : 1.5f) * d);
            c.drawCircle(cx, cy, rr * i / 4f, anillo);
        }
        // Cruz
        anillo.setColor(conAlfa(color, 40));
        anillo.setStrokeWidth(1f * d);
        c.drawLine(cx - rr, cy, cx + rr, cy, anillo);
        c.drawLine(cx, cy - rr, cx, cy + rr, anillo);

        // Barrido giratorio
        if (activo) {
            if (shader == null || colorShader != color) {
                shader = new SweepGradient(cx, cy,
                        new int[]{Color.TRANSPARENT, Color.TRANSPARENT, conAlfa(color, 140)},
                        new float[]{0f, 0.75f, 1f});
                colorShader = color;
            }
            angulo = (angulo + (exceso ? 6f : 3f)) % 360f;
            matriz.setRotate(angulo, cx, cy);
            shader.setLocalMatrix(matriz);
            barrido.setShader(shader);
            c.drawCircle(cx, cy, rr, barrido);
            double rad = Math.toRadians(angulo);
            linea.setColor(color);
            linea.setStrokeWidth(2.5f * d);
            c.drawLine(cx, cy, cx + rr * (float) Math.cos(rad), cy + rr * (float) Math.sin(rad), linea);
        }

        // Radar de delante (más cerca del centro cuanto más cerca está)
        if (activo && dist >= 0) {
            float frac = Math.min(1f, dist / 1500f);
            float py = cy - rr * (0.25f + 0.75f * frac);
            float pulso = 7f + 3f * (float) Math.abs(Math.sin(t * 5));
            punto.setColor(conAlfa(ROJO, 70));
            c.drawCircle(cx, py, (pulso + 7) * d, punto);
            punto.setColor(ROJO);
            c.drawCircle(cx, py, pulso * d, punto);
        }

        // Velocidad en el centro
        numero.setColor(exceso ? ROJO : TEXTO);
        numero.setTextSize(r * 0.42f);
        String vel = activo && gps ? String.valueOf(RadarService.velocidad) : "--";
        c.drawText(vel, cx, cy + r * 0.12f, numero);
        texto.setColor(SUB);
        texto.setTextSize(15 * d);
        c.drawText("km/h", cx, cy + r * 0.30f, texto);

        // Línea de estado
        String estado;
        int colorEstado;
        if (!activo) {
            estado = "Avisos parados";
            colorEstado = SUB;
        } else if (!gps) {
            estado = "Buscando GPS…";
            colorEstado = SUB;
        } else if (dist >= 0) {
            estado = (exceso ? "¡Reduce! " : "") + "Radar a " + (Math.round(dist / 10f) * 10) + " m";
            colorEstado = exceso ? ROJO : AMBAR;
        } else {
            estado = "Vía libre";
            colorEstado = VERDE;
        }
        texto.setColor(colorEstado);
        texto.setTextSize(17 * d);
        texto.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText(estado, cx, cy + r * 0.52f, texto);
        texto.setTypeface(Typeface.DEFAULT);

        // Incidencia de tráfico delante (DGT)
        String inc = RadarService.incidenciaTexto;
        if (activo && inc != null) {
            texto.setColor(AMBAR);
            texto.setTextSize(13 * d);
            c.drawText("⚠ " + inc, cx, cy + r * 0.70f, texto);
        }

        // Señal de límite de velocidad
        if (activo && dist >= 0 && lim > 0) {
            float sr = 26 * d;
            float sx = w - sr - 8 * d, sy = h - sr - 8 * d;
            punto.setColor(Color.WHITE);
            c.drawCircle(sx, sy, sr, punto);
            anillo.setColor(0xFFE0262F);
            anillo.setStrokeWidth(5 * d);
            c.drawCircle(sx, sy, sr - 2.5f * d, anillo);
            senal.setColor(Color.BLACK);
            senal.setTextSize(sr * (lim >= 100 ? 0.75f : 0.9f));
            c.drawText(String.valueOf(lim), sx, sy + sr * 0.32f, senal);
        }

        postInvalidateOnAnimation();
    }

    static int conAlfa(int color, int alfa) {
        return (color & 0x00FFFFFF) | (alfa << 24);
    }
}
