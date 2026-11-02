package com.gabriel.avisaradar;

import android.content.Context;
import android.content.SharedPreferences;

/** Opciones que el usuario puede activar o desactivar. */
public class Ajustes {

    /** Tipos de aviso. */
    public static final int AVISO_VOZ = 0, AVISO_PITIDO = 1, AVISO_VIBRACION = 2;

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(RadarDB.PREFS, Context.MODE_PRIVATE);
    }

    public static boolean usarMapa(Context c) {
        return prefs(c).getBoolean("usar_mapa", true);
    }

    public static void setUsarMapa(Context c, boolean v) {
        prefs(c).edit().putBoolean("usar_mapa", v).apply();
    }

    public static boolean avisosTrafico(Context c) {
        return prefs(c).getBoolean("avisos_trafico", true);
    }

    public static void setAvisosTrafico(Context c, boolean v) {
        prefs(c).edit().putBoolean("avisos_trafico", v).apply();
    }

    public static boolean rumboArriba(Context c) {
        return prefs(c).getBoolean("rumbo_arriba", true);
    }

    public static void setRumboArriba(Context c, boolean v) {
        prefs(c).edit().putBoolean("rumbo_arriba", v).apply();
    }

    // ---------- Tipo de aviso ----------

    public static int tipoAviso(Context c) {
        return prefs(c).getInt("tipo_aviso", AVISO_VOZ);
    }

    public static void setTipoAviso(Context c, int v) {
        prefs(c).edit().putInt("tipo_aviso", v).apply();
    }

    public static String nombreTipoAviso(int t) {
        switch (t) {
            case AVISO_PITIDO:
                return "Solo pitido";
            case AVISO_VIBRACION:
                return "Solo vibración";
            default:
                return "Voz";
        }
    }

    /** Si está activo, los radares solo avisan cuando vas por encima del límite. */
    public static boolean soloSiMePaso(Context c) {
        return prefs(c).getBoolean("solo_si_me_paso", false);
    }

    public static void setSoloSiMePaso(Context c, boolean v) {
        prefs(c).edit().putBoolean("solo_si_me_paso", v).apply();
    }

    // ---------- Burbuja ----------

    public static boolean burbuja(Context c) {
        return prefs(c).getBoolean("burbuja", false);
    }

    public static void setBurbuja(Context c, boolean v) {
        prefs(c).edit().putBoolean("burbuja", v).apply();
    }

    public static int burbujaX(Context c) {
        return prefs(c).getInt("burbuja_x", 30);
    }

    public static int burbujaY(Context c) {
        return prefs(c).getInt("burbuja_y", 300);
    }

    public static void setBurbujaPos(Context c, int x, int y) {
        prefs(c).edit().putInt("burbuja_x", x).putInt("burbuja_y", y).apply();
    }

    // ---------- Distancias de aviso de radar ----------

    /** Cuatro distancias en metros, de mayor a menor. 1ª, 3ª y 4ª siempre; 2ª solo si te pasas. */
    public static int[] distancias(Context c) {
        String[] partes = prefs(c).getString("distancias", "500,200,100,50").split(",");
        int[] d = {500, 200, 100, 50};
        if (partes.length == 4) {
            try {
                for (int i = 0; i < 4; i++) d[i] = Integer.parseInt(partes[i].trim());
            } catch (NumberFormatException e) {
                return new int[]{500, 200, 100, 50};
            }
        }
        return d;
    }

    public static void setDistancias(Context c, int[] d) {
        prefs(c).edit().putString("distancias", d[0] + "," + d[1] + "," + d[2] + "," + d[3]).apply();
    }

    /** Qué hace cada distancia de aviso: con voz, solo si te pasas o en silencio (solo notificación). */
    public static final int DIST_VOZ = 0, DIST_SI_ME_PASO = 1, DIST_SILENCIO = 2;

    public static int modoDistancia(Context c, int i) {
        int[] porDefecto = {DIST_VOZ, DIST_SI_ME_PASO, DIST_VOZ, DIST_VOZ};
        return prefs(c).getInt("modo_dist_" + i, porDefecto[i]);
    }

    public static void setModoDistancia(Context c, int i, int modo) {
        prefs(c).edit().putInt("modo_dist_" + i, modo).apply();
    }

    // ---------- Sonido de cada tipo de aviso ----------

    public static final String[] TIPOS = {"radar", "tramo", "semaforo", "peaje", "trafico"};
    public static final String[] NOMBRES_TIPOS = {"Radares fijos", "Radares de tramo",
            "Semáforos con cámara", "Peajes", "Tráfico (DGT)"};

    /** Si está apagado, ese tipo de aviso solo sale en la notificación, sin sonido. */
    public static boolean sonido(Context c, String tipo) {
        return prefs(c).getBoolean("sonido_" + tipo, true);
    }

    public static void setSonido(Context c, String tipo, boolean v) {
        prefs(c).edit().putBoolean("sonido_" + tipo, v).apply();
    }

    // ---------- Arranque automático ----------

    /** Dirección del Bluetooth del coche ("" = ninguno). */
    public static String bluetoothCoche(Context c) {
        return prefs(c).getString("bt_coche", "");
    }

    public static String nombreBluetoothCoche(Context c) {
        return prefs(c).getString("bt_coche_nombre", "");
    }

    public static void setBluetoothCoche(Context c, String direccion, String nombre) {
        prefs(c).edit().putString("bt_coche", direccion).putString("bt_coche_nombre", nombre).apply();
    }

    public static boolean arranqueNavegador(Context c) {
        return prefs(c).getBoolean("arranque_navegador", false);
    }

    public static void setArranqueNavegador(Context c, boolean v) {
        prefs(c).edit().putBoolean("arranque_navegador", v).apply();
    }

    // ---------- Voz ----------

    public static final float[] VELOCIDADES = {0.85f, 1.0f, 1.2f};
    public static final float[] VOLUMENES = {0.6f, 0.8f, 1.0f};

    public static int velocidadVoz(Context c) {
        return prefs(c).getInt("vel_voz", 1);
    }

    public static void setVelocidadVoz(Context c, int v) {
        prefs(c).edit().putInt("vel_voz", v).apply();
    }

    public static int volumenVoz(Context c) {
        return prefs(c).getInt("vol_voz", 2);
    }

    public static void setVolumenVoz(Context c, int v) {
        prefs(c).edit().putInt("vol_voz", v).apply();
    }

    public static boolean resumenViaje(Context c) {
        return prefs(c).getBoolean("resumen_viaje", true);
    }

    public static void setResumenViaje(Context c, boolean v) {
        prefs(c).edit().putBoolean("resumen_viaje", v).apply();
    }

        // ---------- Gasolineras ----------

    /** Posición en Gasolineras.IDS del combustible que usas (0 = gasolina 95). */
    public static int combustible(Context c) {
        return prefs(c).getInt("combustible", 0);
    }

    public static void setCombustible(Context c, int v) {
        prefs(c).edit().putInt("combustible", v).apply();
    }

    public static boolean gasolineras(Context c) {
        return prefs(c).getBoolean("gasolineras", true);
    }

    public static void setGasolineras(Context c, boolean v) {
        prefs(c).edit().putBoolean("gasolineras", v).apply();
    }

    // ---------- Dónde he aparcado ----------

    public static boolean aparcarAuto(Context c) {
        return prefs(c).getBoolean("aparcar_auto", true);
    }

    public static void setAparcarAuto(Context c, boolean v) {
        prefs(c).edit().putBoolean("aparcar_auto", v).apply();
    }

    public static boolean hayAparcamiento(Context c) {
        return prefs(c).contains("aparc_lat");
    }

    public static double aparcLat(Context c) {
        return Double.longBitsToDouble(prefs(c).getLong("aparc_lat", 0));
    }

    public static double aparcLon(Context c) {
        return Double.longBitsToDouble(prefs(c).getLong("aparc_lon", 0));
    }

    public static long aparcHora(Context c) {
        return prefs(c).getLong("aparc_hora", 0);
    }

    public static void guardarAparcamiento(Context c, double lat, double lon) {
        // Un aparcamiento nuevo no conserva la foto del anterior
        new java.io.File(c.getFilesDir(), FotoProvider.FICHERO).delete();
        prefs(c).edit()
                .remove("aparc_foto")
                .putLong("aparc_lat", Double.doubleToRawLongBits(lat))
                .putLong("aparc_lon", Double.doubleToRawLongBits(lon))
                .putLong("aparc_hora", System.currentTimeMillis())
                .apply();
    }

    /** Dirección (content://) de la foto del aparcamiento en la galería, o null. */
    public static String aparcFoto(Context c) {
        return prefs(c).getString("aparc_foto", null);
    }

    public static void setAparcFoto(Context c, String uri) {
        prefs(c).edit().putString("aparc_foto", uri).apply();
    }

    public static void borrarAparcamiento(Context c) {
        prefs(c).edit().remove("aparc_lat").remove("aparc_lon").remove("aparc_hora").apply();
    }
}
