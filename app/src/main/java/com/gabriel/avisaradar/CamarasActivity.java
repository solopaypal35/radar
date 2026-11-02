package com.gabriel.avisaradar;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Las cámaras de la DGT más cercanas a tu posición, con su imagen en directo. */
public class CamarasActivity extends Pantalla {

    static final int CUANTAS = 15;
    private LinearLayout lista;
    private TextView estado;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        colorearBarras();

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(16), dp(18), dp(32));

        LinearLayout cabecera = new LinearLayout(this);
        cabecera.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout volver = botonIcono(R.drawable.ic_chevron, TEXTO, 20, relieveOval());
        volver.setContentDescription("Volver");
        pulsable(volver, v -> finish());
        cabecera.addView(volver, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout titulos = new LinearLayout(this);
        titulos.setOrientation(LinearLayout.VERTICAL);
        titulos.setPadding(dp(14), 0, 0, 0);
        titulos.addView(texto("Cámaras DGT", 24, TEXTO, true));
        estado = texto("Buscando las más cercanas…", 12, SUB, false);
        estado.setPadding(0, dp(4), 0, 0);
        titulos.addView(estado);
        cabecera.addView(titulos, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        FrameLayout recargar = botonIcono(R.drawable.ic_recargar, TEXTO, 20, relieveOval());
        recargar.setContentDescription("Actualizar imágenes");
        pulsable(recargar, v -> cargar());
        cabecera.addView(recargar, new LinearLayout.LayoutParams(dp(46), dp(46)));
        col.addView(cabecera);

        lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        col.addView(lista, margenArriba(12));

        TextView creditos = texto("Imágenes: Dirección General de Tráfico. No incluye Cataluña ni País Vasco.",
                11, SUB, false);
        creditos.setPadding(dp(6), dp(18), dp(6), 0);
        col.addView(creditos);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(FONDO);
        scroll.addView(col);
        setContentView(scroll);
        cargar();
    }

    private void cargar() {
        estado.setText("Buscando las más cercanas…");
        lista.removeAllViews();
        ProgressBar rueda = new ProgressBar(this);
        lista.addView(rueda, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));
        new Thread(() -> {
            Location yo = posicion();
            List<CamarasDB.Camara> todas = CamarasDB.obtener(this);
            List<CamarasDB.Camara> cerca = new ArrayList<>();
            if (yo != null) {
                float[] r = new float[1];
                for (CamarasDB.Camara c : todas) {
                    Location.distanceBetween(yo.getLatitude(), yo.getLongitude(), c.lat, c.lon, r);
                    c.distancia = r[0];
                }
                Collections.sort(todas, (a, b) -> Float.compare(a.distancia, b.distancia));
                for (int i = 0; i < Math.min(CUANTAS, todas.size()); i++) cerca.add(todas.get(i));
            }
            runOnUiThread(() -> {
                if (isFinishing()) return;
                lista.removeAllViews();
                if (yo == null) {
                    estado.setText("Sin posición. Activa la ubicación y vuelve a intentarlo.");
                } else if (todas.isEmpty()) {
                    estado.setText("No se pudo descargar la lista. Revisa la conexión.");
                } else {
                    estado.setText("Las " + cerca.size() + " más cercanas a ti");
                    for (CamarasDB.Camara c : cerca) lista.addView(tarjetaCamara(c), margenArriba(14));
                }
            });
        }).start();
    }

    private View tarjetaCamara(CamarasDB.Camara c) {
        LinearLayout t = tarjeta();
        t.setPadding(dp(14), dp(14), dp(14), dp(14));

        FrameLayout marco = new FrameLayout(this);
        marco.setBackground(redondo(HUNDIDO, HUNDIDO, 18));
        marco.setClipToOutline(true);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        marco.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView cargando = texto("Cargando imagen…", 12, SUB, false);
        marco.addView(cargando, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        t.addView(marco, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190)));

        LinearLayout fila = new LinearLayout(this);
        fila.setGravity(Gravity.CENTER_VERTICAL);
        fila.setPadding(dp(4), dp(12), dp(4), 0);
        LinearLayout textos = new LinearLayout(this);
        textos.setOrientation(LinearLayout.VERTICAL);
        String titulo = c.carretera + (c.km.isEmpty() ? "" : " · km " + c.km.replace('.', ','));
        textos.addView(texto(titulo, 16, TEXTO, true));
        String sub = (c.destino.isEmpty() ? "" : "Sentido " + capital(c.destino))
                + (c.provincia.isEmpty() ? "" : (c.destino.isEmpty() ? "" : " · ") + capital(c.provincia));
        if (!sub.isEmpty()) {
            TextView s = texto(sub, 12, SUB, false);
            s.setPadding(0, dp(4), 0, 0);
            textos.addView(s);
        }
        fila.addView(textos, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView dist = texto(distancia(c.distancia), 14, VERDE, true);
        fila.addView(dist);
        t.addView(fila);

        // Al tocar la tarjeta se vuelve a descargar la imagen
        pulsable(t, v -> cargarImagen(c.imagen, img, cargando));
        cargarImagen(c.imagen, img, cargando);
        return t;
    }

    private void cargarImagen(String url, ImageView img, TextView cargando) {
        cargando.setVisibility(View.VISIBLE);
        cargando.setText("Cargando imagen…");
        new Thread(() -> {
            Bitmap b = null;
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(url + "?t=" + System.currentTimeMillis()).openConnection();
                con.setInstanceFollowRedirects(true);
                con.setConnectTimeout(10000);
                con.setReadTimeout(20000);
                con.setRequestProperty("User-Agent", "AlLoro (app personal)");
                try (InputStream in = con.getInputStream()) {
                    b = BitmapFactory.decodeStream(in);
                } finally {
                    con.disconnect();
                }
            } catch (Exception ignored) {
            }
            Bitmap fin = b;
            runOnUiThread(() -> {
                if (fin != null) {
                    img.setImageBitmap(fin);
                    cargando.setVisibility(View.GONE);
                } else {
                    cargando.setText("Imagen no disponible ahora");
                }
            });
        }).start();
    }

    private Location posicion() {
        Location l = RadarService.ultima;
        if (l != null && System.currentTimeMillis() - l.getTime() < 10 * 60 * 1000L) return l;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location mejor = null;
        for (String prov : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER}) {
            try {
                Location x = lm.getLastKnownLocation(prov);
                if (x != null && (mejor == null || x.getTime() > mejor.getTime())) mejor = x;
            } catch (Exception ignored) {
            }
        }
        return mejor;
    }

    static String distancia(float m) {
        if (m < 1000) return Math.round(m / 10f) * 10 + " m";
        if (m < 10000) return String.format(new Locale("es", "ES"), "%.1f km", m / 1000f);
        return Math.round(m / 1000f) + " km";
    }

    static String capital(String s) {
        if (s.isEmpty()) return s;
        String t = s.toLowerCase(new Locale("es", "ES"));
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}
