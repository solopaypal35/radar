package com.gabriel.avisaradar;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Semáforos con cámara y peajes (OpenStreetMap), incluidos en la app al compilarla. */
public class PuntosDB {

    public static class Punto {
        public double lat, lon;
        public String precio = ""; // solo peajes: "4,30 €" si está anotado
        public String nombre = "";
    }

    /** Semáforos con cámara: relaciones enforcement=traffic_signals. Se usa el semáforo («to») o la cámara. */
    public static List<Punto> semaforos(Context c) {
        List<Punto> lista = new ArrayList<>();
        String json = leerAsset(c, "semaforos.json");
        if (json == null) return lista;
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
                double[] semaforo = null, camara = null, otro = null;
                for (int j = 0; j < miembros.length(); j++) {
                    JSONObject m = miembros.getJSONObject(j);
                    if (!"node".equals(m.optString("type"))) continue;
                    double[] p = nodos.get(m.getLong("ref"));
                    if (p == null) continue;
                    String rol = m.optString("role");
                    if ("to".equals(rol)) semaforo = p;
                    else if ("device".equals(rol)) camara = p;
                    else if (otro == null) otro = p;
                }
                double[] p = semaforo != null ? semaforo : camara != null ? camara : otro;
                if (p == null) continue;
                Punto pt = new Punto();
                pt.lat = p[0];
                pt.lon = p[1];
                lista.add(pt);
            }
        } catch (Exception ignored) {
        }
        return lista;
    }

    /** Peajes: nodos barrier=toll_booth, con precio si tienen la etiqueta «charge». */
    public static List<Punto> peajes(Context c) {
        List<Punto> lista = new ArrayList<>();
        String json = leerAsset(c, "peajes.json");
        if (json == null) return lista;
        try {
            JSONArray els = new JSONObject(json).getJSONArray("elements");
            for (int i = 0; i < els.length(); i++) {
                JSONObject e = els.getJSONObject(i);
                if (!e.has("lat")) continue;
                Punto pt = new Punto();
                pt.lat = e.getDouble("lat");
                pt.lon = e.getDouble("lon");
                JSONObject tags = e.optJSONObject("tags");
                if (tags != null) {
                    pt.precio = precio(tags.optString("charge", ""));
                    pt.nombre = tags.optString("name", "");
                }
                lista.add(pt);
            }
        } catch (Exception ignored) {
        }
        return lista;
    }

    /** "4.30 EUR" o "4,30 €" → "4,30 euros". Si hay varios precios o no se entiende, se deja vacío. */
    static String precio(String charge) {
        if (charge.isEmpty() || charge.contains(";")) return "";
        String num = charge.replaceAll("[^0-9.,]", "").replace(',', '.');
        try {
            double v = Double.parseDouble(num);
            if (v <= 0 || v > 100) return "";
            return String.format(java.util.Locale.US, "%.2f", v).replace('.', ',') + " euros";
        } catch (Exception e) {
            return "";
        }
    }

    static String leerAsset(Context c, String nombre) {
        try (InputStream in = c.getAssets().open(nombre)) {
            return RadarDB.leer(in);
        } catch (Exception e) {
            return null;
        }
    }
}
