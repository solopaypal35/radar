package com.gabriel.avisaradar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

/**
 * Velocímetro: semicírculo de puntos que se encienden con la velocidad
 * y la velocidad escrita con números de puntos (como un marcador LED).
 */
public class VelocimetroView extends View {

    public static final int MODO_RADARES = 0, MODO_TRAMOS = 1, MODO_TRAFICO = 2, MODO_PEAJES = 3;
    public int modo = MODO_RADARES;

    static final int VELOCIDAD_MAX = 160;
    static final int PUNTOS_ARCO = 29;

    /** Dígitos de 5×7 puntos. */
    static final String[][] DIGITOS = {
            {"01110", "10001", "10011", "10101", "11001", "10001", "01110"}, // 0
            {"00100", "01100", "00100", "00100", "00100", "00100", "01110"}, // 1
            {"01110", "10001", "00001", "00010", "00100", "01000", "11111"}, // 2
            {"11110", "00001", "00001", "01110", "00001", "00001", "11110"}, // 3
            {"00010", "00110", "01010", "10010", "11111", "00010", "00010"}, // 4
            {"11111", "10000", "11110", "00001", "00001", "10001", "01110"}, // 5
            {"00110", "01000", "10000", "11110", "10001", "10001", "01110"}, // 6
            {"11111", "00001", "00010", "00100", "01000", "01000", "01000"}, // 7
            {"01110", "10001", "10001", "01110", "10001", "10001", "01110"}, // 8
            {"01110", "10001", "10001", "01111", "00001", "00010", "01100"}, // 9
            {"00000", "00000", "00000", "11111", "00000", "00000", "00000"}  // -
    };

    private final Paint punto = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint texto = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float mostrada = 0;

    public VelocimetroView(Context c) {
        super(c);
        texto.setTextAlign(Paint.Align.CENTER);
        texto.setTypeface(Typeface.DEFAULT);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, (int) (w * 0.62f));
    }

    /** Color según el estado: turquesa normal, ámbar con algo delante, rojo si te pasas. */
    static int colorEstado() {
        if (!RadarService.activo) return Pantalla.SUB;
        int lim = RadarService.limiteMostrado();
        boolean exceso = RadarService.exceso
                || (RadarService.hayGps && lim > 0 && RadarService.velocidad > lim + 3);
        if (exceso) return Pantalla.ROJO;
        if (RadarService.distancia >= 0 || RadarService.enTramo) return Pantalla.AMBAR;
        return Pantalla.VERDE;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth(), h = getHeight();
        boolean activo = RadarService.activo, gps = RadarService.hayGps;
        int color = colorEstado();

        float objetivo = activo && gps ? RadarService.velocidad : 0;
        mostrada += (objetivo - mostrada) * 0.15f;

        // Semicírculo de puntos (de izquierda a derecha por arriba)
        float cx = w / 2f, cy = h * 0.86f;
        float r = Math.min(w / 2f - 10 * d, h * 0.80f);
        int encendidos = Math.round(Math.min(1f, mostrada / VELOCIDAD_MAX) * PUNTOS_ARCO);
        for (int i = 0; i < PUNTOS_ARCO; i++) {
            double a = Math.toRadians(180 + 180.0 * i / (PUNTOS_ARCO - 1));
            float x = cx + r * (float) Math.cos(a), y = cy + r * (float) Math.sin(a);
            punto.setColor(i < encendidos ? color : Pantalla.HUNDIDO);
            c.drawCircle(x, y, (i < encendidos ? 4.2f : 3.4f) * d, punto);
        }

        // Velocidad en números de puntos, centrada
        String v = activo && gps ? String.valueOf(Math.round(mostrada)) : "--";
        float paso = Math.min(r * 0.075f, 9.5f * d);   // separación entre puntos
        float radio = paso * 0.36f;
        float anchoDigito = 5 * paso, hueco = paso * 1.2f;
        float ancho = v.length() * anchoDigito + (v.length() - 1) * hueco;
        float x0 = cx - ancho / 2f + paso / 2f;
        float y0 = cy - r * 0.62f;
        int colorNum = color == Pantalla.ROJO ? Pantalla.ROJO : Color.WHITE;
        for (int k = 0; k < v.length(); k++) {
            char ch = v.charAt(k);
            String[] g = DIGITOS[ch == '-' ? 10 : ch - '0'];
            for (int fila = 0; fila < 7; fila++) {
                for (int col = 0; col < 5; col++) {
                    boolean on = g[fila].charAt(col) == '1';
                    if (!on) continue;
                    punto.setColor(colorNum);
                    c.drawCircle(x0 + k * (anchoDigito + hueco) + col * paso, y0 + fila * paso, radio, punto);
                }
            }
        }

        // Unidad bajo los números
        texto.setColor(Pantalla.SUB);
        texto.setTextSize(12 * d);
        c.drawText("km/h", cx, y0 + 7 * paso + 16 * d, texto);

        postInvalidateOnAnimation();
    }
}
