package com.gabriel.avisaradar;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Una cámara a pantalla completa. Mientras la miras, vuelve a pedir la foto cada 15 segundos;
 * en cuanto la cámara sube una nueva, aparece sola. Se puede ampliar con dos dedos.
 */
public class CamaraActivity extends Pantalla {

    static final long CADA = 15000;

    private String url;
    private ImageView img;
    private TextView titulo, estado;
    private long ultimaFotoNueva = 0;      // cuándo cambió la imagen por última vez
    private long fechaServidor = 0;        // fecha de la foto según el servidor (si la da)
    private int huellaAnterior = 0;
    private float escala = 1f;
    private float tx = 0, ty = 0, xPrev, yPrev;

    private final Runnable refresco = new Runnable() {
        @Override
        public void run() {
            descargar();
            handler.postDelayed(this, CADA);
        }
    };

    private final Runnable reloj = new Runnable() {
        @Override
        public void run() {
            pintarEstado();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        colorearBarras();
        url = getIntent().getStringExtra("url");

        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(0xFF000000);

        img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        raiz.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Zoom con dos dedos y arrastrar con uno
        ScaleGestureDetector zoom = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                escala = Math.max(1f, Math.min(5f, escala * d.getScaleFactor()));
                aplicarZoom();
                return true;
            }
        });
        raiz.setOnTouchListener((v, e) -> {
            zoom.onTouchEvent(e);
            if (e.getPointerCount() == 1 && !zoom.isInProgress()) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    xPrev = e.getX();
                    yPrev = e.getY();
                } else if (e.getAction() == MotionEvent.ACTION_MOVE && escala > 1f) {
                    tx += e.getX() - xPrev;
                    ty += e.getY() - yPrev;
                    xPrev = e.getX();
                    yPrev = e.getY();
                    aplicarZoom();
                }
            }
            return true;
        });

        // Barra de arriba: volver, carretera y estado
        LinearLayout barra = new LinearLayout(this);
        barra.setGravity(Gravity.CENTER_VERTICAL);
        barra.setPadding(dp(14), dp(14), dp(14), dp(14));
        barra.setBackgroundColor(0xB3000000);
        FrameLayout volver = botonIcono(R.drawable.ic_chevron, TEXTO, 20, relieveOval());
        volver.setContentDescription("Volver");
        pulsable(volver, v -> finish());
        barra.addView(volver, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout textos = new LinearLayout(this);
        textos.setOrientation(LinearLayout.VERTICAL);
        textos.setPadding(dp(12), 0, 0, 0);
        titulo = texto(getIntent().getStringExtra("titulo"), 17, TEXTO, true);
        estado = texto("Cargando…", 12, SUB, false);
        estado.setPadding(0, dp(4), 0, 0);
        textos.addView(titulo);
        textos.addView(estado);
        barra.addView(textos, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        raiz.addView(barra, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        TextView pista = texto("Se actualiza sola cada 15 s · Pellizca para ampliar", 12, SUB, false);
        pista.setGravity(Gravity.CENTER);
        pista.setPadding(dp(16), dp(10), dp(16), dp(26));
        raiz.addView(pista, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        setContentView(raiz);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refresco);
        handler.post(reloj);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresco);
        handler.removeCallbacks(reloj);
    }

    private void aplicarZoom() {
        if (escala <= 1f) {
            tx = 0;
            ty = 0;
        }
        img.setScaleX(escala);
        img.setScaleY(escala);
        img.setTranslationX(tx);
        img.setTranslationY(ty);
    }

    private void descargar() {
        new Thread(() -> {
            Bitmap b = null;
            int huella = 0;
            long fecha = 0;
            try {
                String u = url + (url.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
                HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
                con.setInstanceFollowRedirects(true);
                con.setConnectTimeout(10000);
                con.setReadTimeout(20000);
                con.setUseCaches(false);
                con.setRequestProperty("User-Agent", "AlLoro (app personal)");
                try (InputStream in = con.getInputStream()) {
                    byte[] datos = RadarDB.leerBytes(in);
                    huella = java.util.Arrays.hashCode(datos);
                    fecha = con.getLastModified();
                    b = BitmapFactory.decodeByteArray(datos, 0, datos.length);
                    if (b != null && CamarasActivity.esCartelFueraDeServicio(url, datos.length, b)) b = null;
                } finally {
                    con.disconnect();
                }
            } catch (Exception ignored) {
            }
            Bitmap fin = b;
            int h = huella;
            long f = fecha;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (fin == null) {
                    if (huellaAnterior == 0) estado.setText("Cámara no disponible ahora · se reintenta cada 15 s");
                    return;
                }
                if (h != huellaAnterior) {
                    // Foto nueva
                    huellaAnterior = h;
                    ultimaFotoNueva = System.currentTimeMillis();
                    fechaServidor = f;
                    img.setImageBitmap(fin);
                    img.setAlpha(0.4f);
                    img.animate().alpha(1f).setDuration(350).start();
                }
                pintarEstado();
            });
        }).start();
    }

    /** «Foto de hace 2 min» (si el servidor da la hora) o «Sin cambios desde hace 40 s». */
    private void pintarEstado() {
        if (huellaAnterior == 0) return;
        long ahora = System.currentTimeMillis();
        if (fechaServidor > 0 && fechaServidor <= ahora) {
            estado.setText("Foto de hace " + hace(ahora - fechaServidor));
        } else {
            long s = (ahora - ultimaFotoNueva) / 1000;
            estado.setText(s < 5 ? "Foto actualizada ahora" : "Última foto nueva hace " + hace(ahora - ultimaFotoNueva));
        }
    }

    static String hace(long ms) {
        long s = ms / 1000;
        if (s < 60) return s + " s";
        long m = s / 60;
        if (m < 60) return m + " min";
        return (m / 60) + " h " + (m % 60) + " min";
    }
}
