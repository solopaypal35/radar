package com.gabriel.avisaradar;

import android.content.Context;
import android.content.SharedPreferences;

/** Opciones que el usuario puede activar o desactivar. */
public class Ajustes {

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
}
