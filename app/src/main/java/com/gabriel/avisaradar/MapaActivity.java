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
    private TextView velocidad, botonRumbo, botonCentrar;
    private final List<Overlay> marcasTrafico = new ArrayList<>();
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

        rumboArriba = Ajustes.rumboArriba(this);

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
        botonRumbo = boton("", 15);
        botonRumbo.setOnClickListener(v -> {
            rumboArriba = !rumboArriba;
            Ajustes.setRumboArriba(this, rumboArriba);
            if (!rumboArriba) mapa.setMapOrientation(0);
            pintarBotones();
        });
        botonCentrar = boton("Centrar", 15);
        botonCentrar.setOnClickListener(v -> {
            miPosicion.enableFollowLocation();
            if (miPosicion.getMyLocation() != null) {
                mapa.getController().animateTo(miPosicion.getMyLocation());
            }
            pintarBotones();
        });
        botones.addView(volver, pesoIgual());
        botones.addView(botonRumbo, pesoIgual());
        botones.addView(botonCentrar, pesoIgual());
        FrameLayout.LayoutParams lpBot = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(52), Gravity.BOTTOM);
        lpBot.setMargins(dp(10), 0, dp(10), dp(22));
        raiz.addView(botones, lpBot);

        setContentView(raiz);
        pintarBotones();
        cargarRadares();
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
        if (Ajustes.avisosTrafico(this) && IncidenciasDB.actuales != traficoPintado) {
            pintarTrafico(IncidenciasDB.actuales);
        }
        pintarBotones();
    }

    private void cargarRadares() {
        new Thread(() -> {
            List<RadarDB.Radar> radares = RadarDB.cargar(this);
            List<IGeoPoint> puntos = new ArrayList<>();
            for (RadarDB.Radar r : radares) puntos.add(new GeoPoint(r.lat, r.lon));
            runOnUiThread(() -> {
                if (isFinishing() || puntos.isEmpty()) return;
                Paint relleno = new Paint(Paint.ANTI_ALIAS_FLAG);
                relleno.setColor(MainActivity.ROJO);
                relleno.setStyle(Paint.Style.FILL);
                SimpleFastPointOverlayOptions op = SimpleFastPointOverlayOptions.getDefaultStyle()
                        .setAlgorithm(SimpleFastPointOverlayOptions.RenderingAlgorithm.MAXIMUM_OPTIMIZATION)
                        .setSymbol(SimpleFastPointOverlayOptions.Shape.CIRCLE)
                        .setRadius(dp(6))
                        .setIsClickable(false)
                        .setPointStyle(relleno);
                SimpleFastPointOverlay capa = new SimpleFastPointOverlay(
                        new SimplePointTheme(puntos, false), op);
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
        botonRumbo.setText(rumboArriba ? "Dirección ↑" : "Norte ↑");
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
