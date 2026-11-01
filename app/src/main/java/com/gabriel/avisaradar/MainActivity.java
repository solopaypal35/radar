package com.gabriel.avisaradar;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Pantalla {


    /** A partir de cuántos días se avisa de que hay que actualizar los radares. */
    static final int DIAS_ACTUALIZAR = 30;

    private TextView botonActivar, botonMapa, aparcTexto;
    private double aparcLatMostrada = Double.NaN;
    private ImageView fotoAparc;
    private long fotoMostrada = -1;
    static final int PEDIR_FOTO = 20;
    private LinearLayout tarjetaRadares, tarjetaBateria;
    private TextView radaresTitulo, radaresSub, radaresAccion;
    private ObjectAnimator latidoRadares;
    private boolean descargando = false;

    private final Runnable refresco = new Runnable() {
        @Override
        public void run() {
            refrescar();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        colorearBarras();

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(20), dp(18), dp(28));

        // Cabecera con logo
        LinearLayout cabecera = new LinearLayout(this);
        cabecera.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo);
        logo.setBackground(redondo(FONDO, BORDE, 14));
        cabecera.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout titulos = new LinearLayout(this);
        titulos.setOrientation(LinearLayout.VERTICAL);
        titulos.setPadding(dp(12), 0, 0, 0);
        TextView titulo = texto("AvisaRadar", 24, TEXTO, true);
        titulos.addView(titulo);
        titulos.addView(texto("Avisos de radares y tráfico", 13, SUB, false));
        cabecera.addView(titulos, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView ajustes = texto("⚙", 22, TEXTO, false);
        ajustes.setGravity(Gravity.CENTER);
        ajustes.setBackground(redondo(TARJETA, BORDE, 24));
        ajustes.setContentDescription("Ajustes");
        pulsable(ajustes, v -> startActivity(new Intent(this, AjustesActivity.class)));
        cabecera.addView(ajustes, new LinearLayout.LayoutParams(dp(48), dp(48)));
        col.addView(cabecera);

        // Radar animado
        RadarView radar = new RadarView(this);
        LinearLayout.LayoutParams lpRadar = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpRadar.topMargin = dp(12);
        col.addView(radar, lpRadar);

        // Botón principal
        botonActivar = texto("Activar avisos", 19, FONDO, true);
        botonActivar.setGravity(Gravity.CENTER);
        botonActivar.setBackground(redondo(VERDE, VERDE, 30));
        pulsable(botonActivar, v -> alternar());
        LinearLayout.LayoutParams lpBoton = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
        lpBoton.topMargin = dp(8);
        col.addView(botonActivar, lpBoton);

        // Botón del mapa (solo si la opción está activada)
        botonMapa = texto("Abrir mapa", 16, VERDE, true);
        botonMapa.setGravity(Gravity.CENTER);
        botonMapa.setBackground(redondo(TARJETA, VERDE, 26));
        pulsable(botonMapa, v -> startActivity(new Intent(this, MapaActivity.class)));
        LinearLayout.LayoutParams lpMapa = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lpMapa.topMargin = dp(10);
        col.addView(botonMapa, lpMapa);

        // Tarjeta de radares (se pone naranja cuando toca actualizar)
        tarjetaRadares = tarjeta();
        LinearLayout filaRadares = new LinearLayout(this);
        filaRadares.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout textosRadares = new LinearLayout(this);
        textosRadares.setOrientation(LinearLayout.VERTICAL);
        radaresTitulo = texto("Radares", 16, TEXTO, true);
        radaresSub = texto("", 13, SUB, false);
        textosRadares.addView(radaresTitulo);
        textosRadares.addView(radaresSub);
        filaRadares.addView(textosRadares, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        radaresAccion = texto("Actualizar", 14, VERDE, true);
        filaRadares.addView(radaresAccion);
        tarjetaRadares.addView(filaRadares);
        pulsable(tarjetaRadares, v -> actualizar());
        col.addView(tarjetaRadares, margenArriba(12));

        // Tarjeta de batería (solo aparece si Android puede cerrar la app)
        tarjetaBateria = tarjeta();
        tarjetaBateria.setBackground(redondo(AMBAR_FONDO, AMBAR, 16));
        tarjetaBateria.addView(texto("Permite que funcione siempre", 16, AMBAR, true));
        TextView expl = texto("Android puede cerrar AvisaRadar en viajes largos para ahorrar batería, "
                + "y dejarías de oír los avisos. Toca aquí y pulsa «Permitir».", 13, TEXTO, false);
        expl.setPadding(0, dp(4), 0, 0);
        tarjetaBateria.addView(expl);
        pulsable(tarjetaBateria, v -> pedirSinOptimizar());
        col.addView(tarjetaBateria, margenArriba(12));

        // Dónde he aparcado
        LinearLayout aparc = tarjeta();
        aparc.addView(texto("Dónde he aparcado", 16, TEXTO, true));
        aparcTexto = texto("", 13, SUB, false);
        aparcTexto.setPadding(0, dp(2), 0, dp(10));
        aparc.addView(aparcTexto);
        fotoAparc = new ImageView(this);
        fotoAparc.setScaleType(ImageView.ScaleType.CENTER_CROP);
        fotoAparc.setBackground(redondo(FONDO, BORDE, 12));
        fotoAparc.setClipToOutline(true);
        fotoAparc.setVisibility(View.GONE);
        fotoAparc.setOnClickListener(v -> verFoto());
        LinearLayout.LayoutParams lpFoto = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(140));
        lpFoto.bottomMargin = dp(10);
        aparc.addView(fotoAparc, lpFoto);
        LinearLayout filaAparc = new LinearLayout(this);
        TextView guardar = botonPequeno("Guardar aquí");
        pulsable(guardar, v -> guardarAparcamientoManual());
        TextView llegar = botonPequeno("Cómo llegar");
        pulsable(llegar, v -> comoLlegar());
        TextView whatsapp = botonPequeno("WhatsApp");
        whatsapp.setTextColor(VERDE);
        iconoApp(whatsapp, "com.whatsapp");
        pulsable(whatsapp, v -> enviarWhatsApp());
        TextView foto = botonPequeno("📷");
        foto.setTextSize(18);
        pulsable(foto, v -> hacerFoto());
        filaAparc.addView(guardar, pesoBoton());
        filaAparc.addView(foto, pesoBoton());
        filaAparc.addView(llegar, pesoBoton());
        filaAparc.addView(whatsapp, pesoBoton());
        aparc.addView(filaAparc);
        col.addView(aparc, margenArriba(12));

        // Música (Spotify u otra app que esté sonando)
        LinearLayout musica = tarjeta();
        musica.addView(texto("Música", 16, TEXTO, true));
        LinearLayout filaMusica = new LinearLayout(this);
        filaMusica.setPadding(0, dp(10), 0, 0);
        TextView ant = botonPequeno("⏮");
        ant.setTextSize(20);
        pulsable(ant, v -> Musica.anterior(this));
        TextView pp = botonPequeno("⏯");
        pp.setTextSize(20);
        pulsable(pp, v -> Musica.playPausa(this));
        TextView sig = botonPequeno("⏭");
        sig.setTextSize(20);
        pulsable(sig, v -> Musica.siguiente(this));
        TextView spotify = botonPequeno("Spotify");
        spotify.setTextColor(VERDE);
        iconoApp(spotify, Musica.SPOTIFY);
        pulsable(spotify, v -> Musica.abrirSpotify(this));
        filaMusica.addView(ant, pesoBoton());
        filaMusica.addView(pp, pesoBoton());
        filaMusica.addView(sig, pesoBoton());
        filaMusica.addView(spotify, pesoBoton());
        musica.addView(filaMusica);
        col.addView(musica, margenArriba(12));

        TextView ayuda = texto("Activa los avisos y abre Maps o Waze: AvisaRadar sigue avisando "
                + "en segundo plano.", 12, SUB, false);
        ayuda.setPadding(dp(4), dp(18), dp(4), 0);
        col.addView(ayuda);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(FONDO);
        scroll.setFillViewport(true);
        scroll.addView(col);
        setContentView(scroll);

        // Animación de entrada: cada bloque aparece deslizándose
        for (int i = 0; i < col.getChildCount(); i++) {
            View v = col.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(dp(24));
            v.animate().alpha(1f).translationY(0).setStartDelay(80L * i).setDuration(450)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refrescarRadares();
        handler.post(refresco);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresco);
    }

    private void refrescar() {
        refrescarAparcamiento();
        botonMapa.setVisibility(Ajustes.usarMapa(this) ? View.VISIBLE : View.GONE);
        // Botón principal
        if (RadarService.activo) {
            botonActivar.setText("Parar avisos");
            botonActivar.setTextColor(TEXTO);
            botonActivar.setBackground(redondo(ROJO, ROJO, 30));
        } else {
            botonActivar.setText("Activar avisos");
            botonActivar.setTextColor(FONDO);
            botonActivar.setBackground(redondo(VERDE, VERDE, 30));
        }

        // Tarjeta de batería
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        tarjetaBateria.setVisibility(pm.isIgnoringBatteryOptimizations(getPackageName())
                ? View.GONE : View.VISIBLE);
    }

    /** Lee los radares guardados y pone la tarjeta normal o naranja. */
    private void refrescarRadares() {
        if (!descargando) {
            int n = RadarDB.contarOficiales(this);
            int propios = RadarDB.propios(this).size();
            int dias = RadarDB.diasSinActualizar(this);
            boolean toca = n == 0 || dias < 0 || dias >= DIAS_ACTUALIZAR;
            String extra = propios > 0 ? " · " + propios + " tuyos" : "";
            if (n == 0) {
                radaresTitulo.setText("Descarga los radares");
                radaresSub.setText("Todavía no hay radares en el móvil" + extra);
            } else if (toca) {
                radaresTitulo.setText("Actualiza los radares");
                radaresSub.setText(n + " radares · última vez hace " + dias + " días" + extra);
            } else {
                radaresTitulo.setText(n + " radares");
                radaresSub.setText("Actualizados " + cuando(dias) + extra);
            }
            ponerAvisoRadares(toca);
            // La tarjeta solo aparece cuando hay que actualizar (si no, está en Ajustes)
            tarjetaRadares.setVisibility(toca ? View.VISIBLE : View.GONE);
        }
    }

    private void ponerAvisoRadares(boolean toca) {
        if (toca) {
            tarjetaRadares.setBackground(redondo(AMBAR_FONDO, AMBAR, 16));
            radaresTitulo.setTextColor(AMBAR);
            radaresAccion.setTextColor(AMBAR);
            if (latidoRadares == null) {
                latidoRadares = ObjectAnimator.ofFloat(tarjetaRadares, "alpha", 1f, 0.65f);
                latidoRadares.setDuration(900);
                latidoRadares.setRepeatMode(ValueAnimator.REVERSE);
                latidoRadares.setRepeatCount(ValueAnimator.INFINITE);
                latidoRadares.setStartDelay(900);
                latidoRadares.start();
            }
        } else {
            tarjetaRadares.setBackground(redondo(TARJETA, BORDE, 16));
            radaresTitulo.setTextColor(TEXTO);
            radaresAccion.setTextColor(VERDE);
            if (latidoRadares != null) {
                latidoRadares.cancel();
                latidoRadares = null;
                tarjetaRadares.setAlpha(1f);
            }
        }
    }

    static String cuando(int dias) {
        if (dias <= 0) return "hoy";
        if (dias == 1) return "ayer";
        return "hace " + dias + " días";
    }

    // ---------- Activar / parar ----------

    private void alternar() {
        if (RadarService.activo) {
            stopService(new Intent(this, RadarService.class));
            handler.postDelayed(this::refrescar, 300);
            return;
        }
        List<String> faltan = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            faltan.add(Manifest.permission.ACCESS_FINE_LOCATION);
            faltan.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            faltan.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (faltan.isEmpty()) {
            iniciar();
        } else {
            requestPermissions(faltan.toArray(new String[0]), 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permisos, int[] resultados) {
        super.onRequestPermissionsResult(code, permisos, resultados);
        if (code == 4) {
            guardarAparcamientoManual();
            return;
        }
        if (code != 1) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            iniciar();
        } else {
            toast("Sin permiso de ubicación precisa la app no puede avisar");
        }
    }

    private void iniciar() {
        if (RadarDB.contarOficiales(this) == 0 && RadarDB.propios(this).isEmpty()) {
            toast("Primero descarga los radares");
            return;
        }
        startForegroundService(new Intent(this, RadarService.class));
        handler.postDelayed(this::refrescar, 500);
    }

    // ---------- Radares ----------

    private void actualizar() {
        if (descargando) return;
        descargando = true;
        radaresTitulo.setText("Descargando radares…");
        radaresSub.setText("Puede tardar un minuto");
        radaresAccion.setText("");
        new Thread(() -> {
            try {
                int n = RadarDB.descargar(this);
                try {
                    TramosDB.descargar(this);
                } catch (Exception ignored) {
                    // Si fallan los tramos, se siguen usando los que ya había
                }
                runOnUiThread(() -> {
                    descargando = false;
                    radaresAccion.setText("Actualizar");
                    toast(n + " radares descargados");
                    reiniciarSiActivo();
                    refrescarRadares();
                    refrescar();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    descargando = false;
                    radaresAccion.setText("Actualizar");
                    toast("No se pudo descargar. Revisa la conexión e inténtalo de nuevo.");
                    refrescarRadares();
                });
            }
        }).start();
    }

    private void pedirSinOptimizar() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    // ---------- Piezas de la interfaz ----------

    // ---------- Dónde he aparcado ----------

    private void refrescarAparcamiento() {
        refrescarFoto();
        if (!Ajustes.hayAparcamiento(this)) {
            aparcTexto.setText("Aún no hay ninguno guardado");
            return;
        }
        double lat = Ajustes.aparcLat(this), lon = Ajustes.aparcLon(this);
        long min = (System.currentTimeMillis() - Ajustes.aparcHora(this)) / 60000;
        String hace = min < 1 ? "ahora mismo" : min < 60 ? "hace " + min + " min"
                : min < 48 * 60 ? "hace " + (min / 60) + " h" : "hace " + (min / 1440) + " días";
        String actual = aparcTexto.getText().toString();
        int punto = actual.indexOf(" · ");
        String direccion = (lat == aparcLatMostrada && punto > 0) ? actual.substring(punto) : "";
        aparcTexto.setText("Guardado " + hace + direccion);
        if (lat != aparcLatMostrada) {
            aparcLatMostrada = lat;
            new Thread(() -> {
                String calle = direccion(lat, lon);
                if (calle != null) runOnUiThread(() -> aparcTexto.setText("Guardado " + hace + " · " + calle));
            }).start();
        }
    }

    private String direccion(double lat, double lon) {
        try {
            android.location.Geocoder g = new android.location.Geocoder(this, new java.util.Locale("es", "ES"));
            List<android.location.Address> lista = g.getFromLocation(lat, lon, 1);
            if (lista != null && !lista.isEmpty()) {
                android.location.Address a = lista.get(0);
                String calle = a.getThoroughfare();
                if (calle == null) return a.getLocality();
                return calle + (a.getSubThoroughfare() != null ? " " + a.getSubThoroughfare() : "");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void guardarAparcamientoManual() {
        Location l = RadarService.ultima;
        if (l != null && System.currentTimeMillis() - l.getTime() < 60000) {
            guardarEn(l);
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 4);
            return;
        }
        toast("Buscando tu posición…");
        android.location.LocationManager lm =
                (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(android.location.LocationManager.GPS_PROVIDER, null,
                        getMainExecutor(), loc -> {
                            if (loc != null) guardarEn(loc);
                            else toast("No hay señal GPS. Prueba al aire libre.");
                        });
            } else {
                lm.requestSingleUpdate(android.location.LocationManager.GPS_PROVIDER,
                        this::guardarEn, Looper.getMainLooper());
            }
        } catch (Exception e) {
            toast("No se pudo obtener la posición");
        }
    }

    private void guardarEn(Location l) {
        Ajustes.guardarAparcamiento(this, l.getLatitude(), l.getLongitude());
        aparcLatMostrada = Double.NaN;
        toast("Aparcamiento guardado");
        refrescarAparcamiento();
    }

    private String enlaceAparcamiento() {
        return String.format(java.util.Locale.US, "https://maps.google.com/?q=%.6f,%.6f",
                Ajustes.aparcLat(this), Ajustes.aparcLon(this));
    }

    private void comoLlegar() {
        if (!Ajustes.hayAparcamiento(this)) {
            toast("Primero guarda dónde has aparcado");
            return;
        }
        String url = String.format(java.util.Locale.US,
                "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f&travelmode=walking",
                Ajustes.aparcLat(this), Ajustes.aparcLon(this));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("No hay ninguna app de mapas");
        }
    }

    private void enviarWhatsApp() {
        if (!Ajustes.hayAparcamiento(this)) {
            toast("Primero guarda dónde has aparcado");
            return;
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.putExtra(Intent.EXTRA_TEXT, "Mi coche está aparcado aquí: " + enlaceAparcamiento());
        if (fotoFichero().exists()) {
            i.setType("image/jpeg");
            i.putExtra(Intent.EXTRA_STREAM, FotoProvider.URI);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            i.setType("text/plain");
        }
        for (String app : new String[]{"com.whatsapp", "com.whatsapp.w4b"}) {
            try {
                i.setPackage(app);
                startActivity(i);
                return;
            } catch (Exception ignored) {
            }
        }
        i.setPackage(null);
        startActivity(Intent.createChooser(i, "Enviar ubicación"));
    }

    // ---------- Foto del aparcamiento ----------

    private java.io.File fotoFichero() {
        return new java.io.File(getFilesDir(), FotoProvider.FICHERO);
    }

    private void hacerFoto() {
        if (!Ajustes.hayAparcamiento(this)) {
            toast("Primero guarda dónde has aparcado");
            return;
        }
        Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, FotoProvider.URI);
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(android.content.ClipData.newRawUri("foto", FotoProvider.URI));
        try {
            startActivityForResult(i, PEDIR_FOTO);
        } catch (Exception e) {
            toast("No se encontró la cámara");
        }
    }

    @Override
    protected void onActivityResult(int code, int resultado, Intent datos) {
        super.onActivityResult(code, resultado, datos);
        if (code == PEDIR_FOTO) {
            if (resultado == RESULT_OK && fotoFichero().length() > 0) toast("Foto guardada con el aparcamiento");
            fotoMostrada = -1;
            refrescarFoto();
        }
    }

    /** Muestra la miniatura si hay foto (solo vuelve a leerla si ha cambiado). */
    private void refrescarFoto() {
        java.io.File f = fotoFichero();
        long marca = f.exists() && f.length() > 0 ? f.lastModified() : 0;
        if (marca == fotoMostrada) return;
        fotoMostrada = marca;
        if (marca == 0) {
            fotoAparc.setImageDrawable(null);
            fotoAparc.setVisibility(View.GONE);
            return;
        }
        android.graphics.BitmapFactory.Options op = new android.graphics.BitmapFactory.Options();
        op.inSampleSize = 4; // miniatura ligera
        android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeFile(f.getPath(), op);
        if (b == null) {
            fotoAparc.setVisibility(View.GONE);
            return;
        }
        fotoAparc.setImageBitmap(rotarSegunExif(f, b));
        fotoAparc.setVisibility(View.VISIBLE);
    }

    private android.graphics.Bitmap rotarSegunExif(java.io.File f, android.graphics.Bitmap b) {
        try {
            int o = new android.media.ExifInterface(f.getPath()).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL);
            int grados = o == android.media.ExifInterface.ORIENTATION_ROTATE_90 ? 90
                    : o == android.media.ExifInterface.ORIENTATION_ROTATE_180 ? 180
                    : o == android.media.ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            if (grados == 0) return b;
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.postRotate(grados);
            return android.graphics.Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        } catch (Exception e) {
            return b;
        }
    }

    private void verFoto() {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(FotoProvider.URI, "image/jpeg");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(i);
        } catch (Exception e) {
            toast("No hay ninguna app para ver fotos");
        }
    }

    // ---------- Distancias de aviso ----------

}
