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
        prefs(c).edit()
                .putLong("aparc_lat", Double.doubleToRawLongBits(lat))
                .putLong("aparc_lon", Double.doubleToRawLongBits(lon))
                .putLong("aparc_hora", System.currentTimeMillis())
                .apply();
    }

    public static void borrarAparcamiento(Context c) {
        prefs(c).edit().remove("aparc_lat").remove("aparc_lon").remove("aparc_hora").apply();
    }
}
