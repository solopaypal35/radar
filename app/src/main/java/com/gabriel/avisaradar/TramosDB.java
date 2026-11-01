package com.gabriel.avisaradar;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Radares de tramo (control de velocidad media) de OpenStreetMap.
 * Cada tramo tiene dos extremos; el que pasas primero es el inicio.
 */
public class TramosDB {

    public static class Tramo {
        public double latA, lonA, latB, lonB;
        public int limite; // km/h, 0 = desconocido
        public String nombre = "";
    }

    static final String FICHERO = "tramos.json";

    static final String CONSULTA =
            "[out:json][timeout:90];("
                    + "relation[\"enforcement\"=\"average_speed\"](35.1,-9.6,43.9,4.5);"
                    + "relation[\"enforcement\"=\"average_speed\"](27.5,-18.3,29.5,-13.3);"
                    + ");out body;>;out skel qt;";

    public static List<Tramo> cargar(Context c) {
        String json = null;
        File f = new File(c.getFilesDir(), FICHERO);
        try {
            if (f.exists()) {
                try (InputStream in = new FileInputStream(f)) {
                    json = RadarDB.leer(in);
                }
            } else {
                try (InputStream in = c.getAssets().open(FICHERO)) {
                    json = RadarDB.leer(in);
                }
            }
        } catch (Exception ignored) {
        }
        List<Tramo> lista = new ArrayList<>();
        if (json != null) parsear(json, lista);
        return lista;
    }

    /** Descarga los tramos y los guarda. Devuelve cuántos hay. */
    public static int descargar(Context c) throws Exception {
        Exception ultimo = null;
        for (String servidor : RadarDB.SERVIDORES) {
            try {
                String json = RadarDB.pedir(servidor, CONSULTA);
                List<Tramo> prueba = new ArrayList<>();
                parsear(json, prueba);
                if (prueba.isEmpty()) throw new Exception("Sin tramos");
                try (OutputStream out = new FileOutputStream(new File(c.getFilesDir(), FICHERO))) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }
                return prueba.size();
            } catch (Exception e) {
                ultimo = e;
            }
        }
        throw ultimo != null ? ultimo : new Exception("Error desconocido");
    }

    static void parsear(String json, List<Tramo> lista) {
        try {
            JSONArray els = new JSONObject(json).getJSONArray("elements");
            Map<Long, double[]> nodos = new HashMap<>();
            for (int i = 0; i < els.length(); i++) {
                JSONObject e = els.getJSONObject(i);
                if ("node".equals(e.optString("type")) && e.has("lat")) {
                    nodos.put(e.getLong("id"), new double[]{e.getDouble("lat"), e.getDouble("lon")});
                }
            }
            for (int i = 0; i < els.length(); i++) {
                JSONObject e = els.getJSONObject(i);
                if (!"relation".equals(e.optString("type"))) continue;
                JSONArray miembros = e.optJSONArray("members");
                if (miembros == null) continue;
                double[] desde = null, hasta = null;
                List<double[]> aparatos = new ArrayList<>();
                for (int j = 0; j < miembros.length(); j++) {
                    JSONObject m = miembros.getJSONObject(j);
                    if (!"node".equals(m.optString("type"))) continue;
                    double[] p = nodos.get(m.getLong("ref"));
                    if (p == null) continue;
                    String rol = m.optString("role");
                    if ("from".equals(rol)) desde = p;
                    else if ("to".equals(rol)) hasta = p;
                    else if ("device".equals(rol)) aparatos.add(p);
                }
                if (desde == null && hasta == null && aparatos.size() >= 2) {
                    desde = aparatos.get(0);
                    hasta = aparatos.get(aparatos.size() - 1);
                }
                if (desde == null || hasta == null) continue;
                Tramo t = new Tramo();
                t.latA = desde[0];
                t.lonA = desde[1];
                t.latB = hasta[0];
                t.lonB = hasta[1];
                JSONObject tags = e.optJSONObject("tags");
                if (tags != null) {
                    t.limite = Math.max(0, RadarDB.numero(tags.optString("maxspeed", "")));
                    t.nombre = tags.optString("name", "");
                }
                lista.add(t);
            }
        } catch (Exception ignored) {
        }
    }
}
