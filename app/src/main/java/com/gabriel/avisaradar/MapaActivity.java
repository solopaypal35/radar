package com.gabriel.avisaradar;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlay;
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlayOptions;
import org.osmdroid.views.overlay.simplefastpoint.SimplePointTheme;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Mapa ligero (OpenStreetMap) con tu posición, los radares y las incidencias de tráfico. */
public class MapaActivity extends Activity {

    private MapView mapa;
    private MyLocationNewOverlay miPosicion;
    private TextView velocidad, botonCentrar;
    private List<RadarDB.Radar> radaresMapa = new ArrayList<>();
    private final List<Overlay> marcasTrafico = new ArrayList<>();
    private final List<Overlay> marcasGasolineras = new ArrayList<>();
    private List<Gasolineras.Estacion> gasolineras = null;
    private Location gasolinerasDesde = null;
    private boolean cargandoGasolineras = false;
    private List<IncidenciasDB.Incidencia> traficoPintado = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean rumboArriba;

    private final Runnable tic = new Runnable() {
        @Override
        public void run() {
            actualizar();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(MainActivity.FONDO);
        getWindow().setNavigationBarColor(MainActivity.FONDO);

        IConfigurationProvider cfg = Configuration.getInstance();
        cfg.setUserAgentValue(getPackageName());
        File base = new File(getCacheDir(), "osmdroid");
        cfg.setOsmdroidBasePath(base);
        cfg.setOsmdroidTileCache(new File(base, "tiles"));
        cfg.setTileFileSystemCacheMaxBytes(100L * 1024 * 1024); // 100 MB de caché como máximo

        rumboArriba = false; // el mapa siempre con el norte arriba

        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(MainActivity.FONDO);

        mapa = new MapView(this);
        mapa.setTileSource(TileSourceFactory.MAPNIK);
        mapa.setMultiTouchControls(true);
        mapa.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        mapa.setTilesScaledToDpi(true);
        mapa.getController().setZoom(16.0);
        mapa.getController().setCenter(new GeoPoint(41.55, 2.10));
        raiz.addView(mapa, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        miPosicion = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapa);
        miPosicion.setDrawAccuracyEnabled(false);
        // Tu posición se dibuja como un coche de carreras que gira según tu dirección
        android.graphics.Bitmap coche = cocheBitmap();
        miPosicion.setDirectionArrow(coche, coche);
        miPosicion.setPersonAnchor(0.5f, 0.5f);
        miPosicion.setDirectionAnchor(0.5f, 0.5f);
        mapa.getOverlays().add(miPosicion);
        mapa.getOverlays().add(new CopyrightOverlay(this));

        // Velocidad arriba a la izquierda
        velocidad = boton("-- km/h", 18);
        FrameLayout.LayoutParams lpVel = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, dp(48), Gravity.TOP | Gravity.START);
        lpVel.setMargins(dp(14), dp(14), 0, 0);
        raiz.addView(velocidad, lpVel);

        // Botones abajo
        LinearLayout botones = new LinearLayout(this);
        botones.setOrientation(LinearLayout.HORIZONTAL);
        TextView volver = boton("Volver", 15);
        volver.setOnClickListener(v -> finish());
        botonCentrar = boton("Centrar", 15);
        botonCentrar.setOnClickListener(v -> {
            miPosicion.enableFollowLocation();
            if (miPosicion.getMyLocation() != null) {
                mapa.getController().animateTo(miPosicion.getMyLocation());
            }
            pintarBotones();
        });
        botones.addView(volver, pesoIgual());
        botones.addView(botonCentrar, pesoIgual());
        FrameLayout.LayoutParams lpBot = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(52), Gravity.BOTTOM);
        lpBot.setMargins(dp(10), 0, dp(10), dp(22));
        raiz.addView(botones, lpBot);

        setContentView(raiz);
        pintarBotones();
        cargarRadares();
        cargarPuntos();
        pintarAparcamiento();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapa.onResume();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            miPosicion.enableMyLocation();
            miPosicion.enableFollowLocation();
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 2);
        }
        // Si los avisos no están activos, el mapa descarga él mismo las incidencias
        if (Ajustes.avisosTrafico(this) && !RadarService.activo
                && System.currentTimeMillis() - IncidenciasDB.ultimaDescarga > 5 * 60 * 1000L) {
            Thread t = new Thread(IncidenciasDB::actualizar);
            t.setDaemon(true);
            t.start();
        }
        handler.post(tic);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tic);
        miPosicion.disableMyLocation();
        mapa.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permisos, int[] resultados) {
        super.onRequestPermissionsResult(code, permisos, resultados);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            miPosicion.enableMyLocation();
            miPosicion.enableFollowLocation();
        }
    }

    /** Cada segundo: velocidad, giro del mapa e incidencias nuevas. */
    private void actualizar() {
        Location fix = miPosicion.getLastFix();
        if (fix != null) {
            int kmh = fix.hasSpeed() ? Math.round(fix.getSpeed() * 3.6f) : 0;
            velocidad.setText(kmh + " km/h");
            velocidad.setTextColor(RadarService.exceso ? MainActivity.ROJO : MainActivity.TEXTO);
            if (rumboArriba && fix.hasBearing() && kmh > 8) {
                mapa.setMapOrientation(-fix.getBearing());
            }
        }
        if (Ajustes.gasolineras(this)) revisarGasolineras(fix);
        if (Ajustes.avisosTrafico(this) && IncidenciasDB.actuales != traficoPintado) {
            pintarTrafico(IncidenciasDB.actuales);
        }
        pintarBotones();
    }

    private void cargarRadares() {
        new Thread(() -> {
            List<RadarDB.Radar> radares = RadarDB.cargar(this);
            List<IGeoPoint> puntos = new ArrayList<>();
            radaresMapa = radares;
            for (RadarDB.Radar r : radares) puntos.add(new GeoPoint(r.lat, r.lon));
            runOnUiThread(() -> {
                if (isFinishing() || puntos.isEmpty()) return;
                Paint relleno = new Paint(Paint.ANTI_ALIAS_FLAG);
                relleno.setColor(0xFF2F80ED); // radares en azul
                relleno.setStyle(Paint.Style.FILL);
                SimpleFastPointOverlayOptions op = SimpleFastPointOverlayOptions.getDefaultStyle()
                        .setAlgorithm(SimpleFastPointOverlayOptions.RenderingAlgorithm.MAXIMUM_OPTIMIZATION)
                        .setSymbol(SimpleFastPointOverlayOptions.Shape.CIRCLE)
                        .setRadius(dp(6))
                        .setIsClickable(true)
                        .setPointStyle(relleno);
                SimpleFastPointOverlay capa = new SimpleFastPointOverlay(
                        new SimplePointTheme(puntos, false), op);
                capa.setOnClickListener((lista, indice) -> {
                    if (indice != null && indice >= 0 && indice < radaresMapa.size()) {
                        mostrarRadar(radaresMapa.get(indice));
                    }
                });
                mapa.getOverlays().add(0, capa);
                mapa.invalidate();
            });
        }).start();
    }

    /** Pinta las incidencias de tráfico a menos de 80 km. */
    private void pintarTrafico(List<IncidenciasDB.Incidencia> lista) {
        traficoPintado = lista;
        mapa.getOverlays().removeAll(marcasTrafico);
        marcasTrafico.clear();
        Location fix = miPosicion.getLastFix();
        float[] res = new float[1];
        for (IncidenciasDB.Incidencia i : lista) {
            if (fix != null) {
                Location.distanceBetween(fix.getLatitude(), fix.getLongitude(), i.lat, i.lon, res);
                if (res[0] > 80000) continue;
            }
            Marker m = new Marker(mapa);
            m.setPosition(new GeoPoint(i.lat, i.lon));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            m.setIcon(circulo(i.importante ? MainActivity.AMBAR : 0xFF8A9BB8));
            m.setTitle(i.tipo);
            m.setSnippet(i.carretera);
            marcasTrafico.add(m);
            if (fix == null && marcasTrafico.size() > 400) break; // sin posición, no saturar el mapa
        }
        mapa.getOverlays().addAll(marcasTrafico);
        mapa.invalidate();
    }

    private android.graphics.Bitmap cocheBitmap() {
        android.graphics.drawable.Drawable d = getDrawable(R.drawable.ic_coche);
        int w = dp(30), h = dp(45);
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        d.setBounds(0, 0, w, h);
        d.draw(c);
        return b;
    }

    // ---------- Gasolineras ----------

    /** Descarga los precios una vez y vuelve a pintar si te alejas más de 3 km. */
    private void revisarGasolineras(Location fix) {
        if (fix == null || cargandoGasolineras) return;
        if (gasolinerasDesde != null && gasolinerasDesde.distanceTo(fix) < 3000) return;
        gasolinerasDesde = fix;
        if (gasolineras != null) {
            pintarGasolineras(fix);
            return;
        }
        cargandoGasolineras = true;
        int comb = Ajustes.combustible(this);
        new Thread(() -> {
            List<Gasolineras.Estacion> lista = Gasolineras.obtener(this, comb);
            runOnUiThread(() -> {
                cargandoGasolineras = false;
                if (isFinishing()) return;
                if (lista.isEmpty()) {
                    gasolinerasDesde = null; // se reintentará
                    return;
                }
                gasolineras = lista;
                pintarGasolineras(fix);
            });
        }).start();
    }

    /** Las 30 gasolineras más cercanas (a menos de 10 km), coloreadas de más barata a más cara. */
    private void pintarGasolineras(Location fix) {
        mapa.getOverlays().removeAll(marcasGasolineras);
        marcasGasolineras.clear();
        List<Gasolineras.Estacion> cerca = new ArrayList<>();
        List<Float> distancias = new ArrayList<>();
        float[] res = new float[1];
        for (Gasolineras.Estacion e : gasolineras) {
            Location.distanceBetween(fix.getLatitude(), fix.getLongitude(), e.lat, e.lon, res);
            if (res[0] > 10000) continue;
            int i = 0;
            while (i < distancias.size() && distancias.get(i) < res[0]) i++;
            distancias.add(i, res[0]);
            cerca.add(i, e);
        }
        if (cerca.size() > 30) cerca = new ArrayList<>(cerca.subList(0, 30));
        if (cerca.isEmpty()) return;
        double min = Double.MAX_VALUE, max = 0;
        for (Gasolineras.Estacion e : cerca) {
            min = Math.min(min, e.precio);
            max = Math.max(max, e.precio);
        }
        String comb = Gasolineras.NOMBRES_LARGOS[Ajustes.combustible(this)];
        final double minimo = min;
        for (Gasolineras.Estacion e : cerca) {
            double f = max > min ? (e.precio - min) / (max - min) : 0;
            int color = f < 0.34 ? MainActivity.VERDE : f < 0.67 ? MainActivity.AMBAR : MainActivity.ROJO;
            Marker m = new Marker(mapa);
            m.setPosition(new GeoPoint(e.lat, e.lon));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setIcon(new android.graphics.drawable.BitmapDrawable(getResources(),
                    etiquetaPrecio(e, color, e.precio == min)));
            m.setOnMarkerClickListener((marca, vista) -> {
                mostrarGasolinera(e, comb, e.precio == minimo);
                return true;
            });
            marcasGasolineras.add(m);
        }
        mapa.getOverlays().addAll(marcasGasolineras);
        mapa.invalidate();
    }

    /** Etiqueta con el precio y el nombre de la gasolinera. */
    private android.graphics.Bitmap etiquetaPrecio(Gasolineras.Estacion e, int color, boolean masBarata) {
        String precio = String.format(java.util.Locale.US, "%.3f", e.precio).replace('.', ',') + " €";
        String nombre = e.rotulo.isEmpty() ? "Gasolinera" : e.rotulo;
        if (nombre.length() > 14) nombre = nombre.substring(0, 13) + "…";
        Paint p1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        p1.setTextSize(dp(13));
        p1.setTypeface(Typeface.DEFAULT_BOLD);
        Paint p2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        p2.setTextSize(dp(9));
        int ancho = (int) Math.max(p1.measureText(precio), p2.measureText(nombre)) + dp(14);
        int alto = dp(36), pico = dp(6);
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(ancho, alto + pico,
                android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        Paint fondo = new Paint(Paint.ANTI_ALIAS_FLAG);
        fondo.setColor(color);
        c.drawRoundRect(0, 0, ancho, alto, dp(8), dp(8), fondo);
        android.graphics.Path punta = new android.graphics.Path();
        punta.moveTo(ancho / 2f - pico, alto);
        punta.lineTo(ancho / 2f + pico, alto);
        punta.lineTo(ancho / 2f, alto + pico);
        punta.close();
        c.drawPath(punta, fondo);
        if (masBarata) {
            Paint borde = new Paint(Paint.ANTI_ALIAS_FLAG);
            borde.setStyle(Paint.Style.STROKE);
            borde.setStrokeWidth(dp(2));
            borde.setColor(Color.WHITE);
            c.drawRoundRect(dp(1), dp(1), ancho - dp(1), alto - dp(1), dp(8), dp(8), borde);
        }
        p1.setColor(MainActivity.FONDO);
        p2.setColor(MainActivity.FONDO);
        p1.setTextAlign(Paint.Align.CENTER);
        p2.setTextAlign(Paint.Align.CENTER);
        c.drawText(precio, ancho / 2f, dp(16), p1);
        c.drawText(nombre, ancho / 2f, dp(29), p2);
        return b;
    }

    private void mostrarGasolinera(Gasolineras.Estacion e, String comb, boolean masBarata) {
        String precio = String.format(java.util.Locale.US, "%.3f", e.precio).replace('.', ',');
        String texto = comb + ": " + precio + " €/l" + (masBarata ? "  (la más barata de la zona)" : "")
                + "\n\n" + e.direccion + (e.localidad.isEmpty() ? "" : ", " + e.localidad)
                + (e.horario.isEmpty() ? "" : "\nHorario: " + e.horario);
        new android.app.AlertDialog.Builder(this)
                .setTitle(e.rotulo.isEmpty() ? "Gasolinera" : e.rotulo)
                .setMessage(texto)
                .setPositiveButton("Cómo llegar", (d, w) -> {
                    String url = String.format(java.util.Locale.US,
                            "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f&travelmode=driving", e.lat, e.lon);
                    startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)));
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    /** Semáforos con cámara (morado) y peajes (amarillo). Al tocarlos se ve qué son. */
    private void cargarPuntos() {
        new Thread(() -> {
            List<PuntosDB.Punto> sem = PuntosDB.semaforos(this);
            List<PuntosDB.Punto> pea = PuntosDB.peajes(this);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                anadirCapa(sem, 0xFFB57CFF, p -> new android.app.AlertDialog.Builder(this)
                        .setTitle("Semáforo con cámara")
                        .setMessage("Multa si pasas con el semáforo en rojo.")
                        .setPositiveButton("Ver en Street View", (d, w) -> abrirStreetView(p.lat, p.lon))
                        .setNegativeButton("Cerrar", null).show());
                anadirCapa(pea, 0xFFFFD43B, p -> new android.app.AlertDialog.Builder(this)
                        .setTitle(p.nombre.isEmpty() ? "Peaje" : "Peaje · " + p.nombre)
                        .setMessage(p.precio.isEmpty() ? "Precio no disponible" : "Precio: " + p.precio)
                        .setNegativeButton("Cerrar", null).show());
            });
        }).start();
    }

    interface AlTocar {
        void punto(PuntosDB.Punto p);
    }

    private void anadirCapa(List<PuntosDB.Punto> lista, int color, AlTocar alTocar) {
        if (lista.isEmpty()) return;
        List<IGeoPoint> puntos = new ArrayList<>();
        for (PuntosDB.Punto p : lista) puntos.add(new GeoPoint(p.lat, p.lon));
        Paint relleno = new Paint(Paint.ANTI_ALIAS_FLAG);
        relleno.setColor(color);
        relleno.setStyle(Paint.Style.FILL);
        SimpleFastPointOverlayOptions op = SimpleFastPointOverlayOptions.getDefaultStyle()
                .setAlgorithm(SimpleFastPointOverlayOptions.RenderingAlgorithm.MAXIMUM_OPTIMIZATION)
                .setSymbol(SimpleFastPointOverlayOptions.Shape.SQUARE)
                .setRadius(dp(6))
                .setIsClickable(true)
                .setPointStyle(relleno);
        SimpleFastPointOverlay capa = new SimpleFastPointOverlay(new SimplePointTheme(puntos, false), op);
        capa.setOnClickListener((l, indice) -> {
            if (indice != null && indice >= 0 && indice < lista.size()) alTocar.punto(lista.get(indice));
        });
        mapa.getOverlays().add(0, capa);
        mapa.invalidate();
    }

    /** Al tocar un radar: su límite y un botón para verlo en Street View. */
    private void mostrarRadar(RadarDB.Radar r) {
        String titulo = r.propio ? "Aviso propio" : "Radar fijo";
        String nota = "\n\nStreet View te enseña la carretera desde el punto más cercano al radar.";
        android.app.AlertDialog dialogo = new android.app.AlertDialog.Builder(this)
                .setTitle(titulo)
                .setMessage((r.limite > 0 ? "Límite " + r.limite + " km/h" : "Buscando el límite de la carretera…") + nota)
                .setPositiveButton("Ver en Street View", (d, w) -> abrirStreetView(r.lat, r.lon))
                .setNegativeButton("Cerrar", null)
                .show();
        if (r.limite <= 0) {
            new Thread(() -> {
                int lim = LimiteVia.consultar(r.lat, r.lon);
                String t = lim > 0 ? "Límite " + lim + " km/h (el de la carretera donde está)"
                        : lim == 0 ? "Límite no indicado en el mapa" : "Sin conexión: no se pudo consultar el límite";
                runOnUiThread(() -> {
                    if (dialogo.isShowing()) dialogo.setMessage(t + nota);
                });
            }).start();
        }
    }

    private void abrirStreetView(double lat, double lon) {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(String.format(java.util.Locale.US, "google.streetview:cbll=%.6f,%.6f", lat, lon)));
        i.setPackage("com.google.android.apps.maps");
        try {
            startActivity(i);
        } catch (Exception e) {
            // Sin la app de Google Maps: se abre en el navegador
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(
                    String.format(java.util.Locale.US,
                            "https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=%.6f,%.6f", lat, lon))));
        }
    }

    /** Marca «P» verde donde has dejado el coche. */
    private void pintarAparcamiento() {
        if (!Ajustes.hayAparcamiento(this)) return;
        Marker m = new Marker(mapa);
        m.setPosition(new GeoPoint(Ajustes.aparcLat(this), Ajustes.aparcLon(this)));
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        int t = dp(30);
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(t, t, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        Paint fondo = new Paint(Paint.ANTI_ALIAS_FLAG);
        fondo.setColor(MainActivity.VERDE);
        c.drawCircle(t / 2f, t / 2f, t / 2f - dp(1), fondo);
        fondo.setColor(Color.WHITE);
        fondo.setStyle(Paint.Style.STROKE);
        fondo.setStrokeWidth(dp(2));
        c.drawCircle(t / 2f, t / 2f, t / 2f - dp(1), fondo);
        Paint letra = new Paint(Paint.ANTI_ALIAS_FLAG);
        letra.setColor(MainActivity.FONDO);
        letra.setTextSize(dp(17));
        letra.setTypeface(Typeface.DEFAULT_BOLD);
        letra.setTextAlign(Paint.Align.CENTER);
        c.drawText("P", t / 2f, t / 2f + dp(6), letra);
        m.setIcon(new android.graphics.drawable.BitmapDrawable(getResources(), b));
        m.setTitle("Tu coche");
        mapa.getOverlays().add(m);
    }

    private GradientDrawable circulo(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        g.setStroke(dp(2), Color.WHITE);
        g.setSize(dp(16), dp(16));
        return g;
    }

    private void pintarBotones() {
        botonCentrar.setTextColor(miPosicion.isFollowLocationEnabled() ? MainActivity.SUB : MainActivity.VERDE);
    }

    private TextView boton(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(MainActivity.TEXTO);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), 0, dp(16), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xEE16213A);
        g.setCornerRadius(dp(26));
        g.setStroke(dp(1), MainActivity.BORDE);
        t.setBackground(g);
        t.setClickable(true);
        return t;
    }

    private LinearLayout.LayoutParams pesoIgual() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
