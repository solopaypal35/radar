package com.gabriel.avisaradar;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Carga, descarga y guarda los radares (OpenStreetMap) y los avisos propios. */
public class RadarDB {

    public static class Radar {
        public double lat, lon;
        public int limite = 0;       // km/h, 0 = desconocido
        public int direccion = -1;   // grados, -1 = desconocida
        public boolean propio = false;
    }

    static final String FICHERO = "radares.json";
    static final String PREFS = "avisaradar";
    static final String CLAVE_PROPIOS = "propios";

    /** Radares fijos marcados en OpenStreetMap: península, Baleares, Canarias, Ceuta y Melilla. */
    static final String CONSULTA =
            "[out:json][timeout:90];("
                    + "node[\"highway\"=\"speed_camera\"](35.1,-9.6,43.9,4.5);"
                    + "node[\"highway\"=\"speed_camera\"](27.5,-18.3,29.5,-13.3);"
                    + ");out body;";

    static final String[] SERVIDORES = {
            "https://overpass-api.de/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
    };

    // ---------- Carga ----------

    public static List<Radar> cargar(Context c) {
        List<Radar> lista = new ArrayList<>();
        String json = null;
        File f = new File(c.getFilesDir(), FICHERO);
        if (f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                json = leer(in);
            } catch (IOException ignored) {
            }
        }
        if (json == null) {
            try (InputStream in = c.getAssets().open(FICHERO)) {
                json = leer(in);
            } catch (IOException ignored) {
            }
        }
        if (json != null) parsear(json, lista);
        lista.addAll(propios(c));
        return lista;
    }

    /** Días que han pasado desde la última actualización de radares, o -1 si no hay radares. */
    public static int diasSinActualizar(Context c) {
        long fecha = 0;
        File f = new File(c.getFilesDir(), FICHERO);
        if (f.exists()) {
            fecha = f.lastModified();
        } else {
            boolean hayIncluidos;
            try (InputStream in = c.getAssets().open(FICHERO)) {
                hayIncluidos = true;
            } catch (IOException e) {
                hayIncluidos = false;
            }
            if (hayIncluidos) {
                try {
                    fecha = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).lastUpdateTime;
                } catch (Exception e) {
                    fecha = System.currentTimeMillis();
                }
            }
        }
        if (fecha == 0) return -1;
        return (int) Math.max(0, (System.currentTimeMillis() - fecha) / 86400000L);
    }

    public static int contarOficiales(Context c) {
        int n = 0;
        for (Radar r : cargar(c)) if (!r.propio) n++;
        return n;
    }

    static void parsear(String json, List<Radar> lista) {
        try {
            JSONArray els = new JSONObject(json).getJSONArray("elements");
            for (int i = 0; i < els.length(); i++) {
                JSONObject e = els.getJSONObject(i);
                if (!e.has("lat") || !e.has("lon")) continue;
                Radar r = new Radar();
                r.lat = e.getDouble("lat");
                r.lon = e.getDouble("lon");
                JSONObject tags = e.optJSONObject("tags");
                if (tags != null) {
                    r.limite = Math.max(0, numero(tags.optString("maxspeed", "")));
                    int d = numero(tags.optString("direction", ""));
                    r.direccion = (d >= 0 && d <= 360) ? d : -1;
                }
                lista.add(r);
            }
        } catch (JSONException ignored) {
        }
    }

    /** Devuelve el número al principio del texto, o -1 si no hay. */
    static int numero(String s) {
        s = s.trim();
        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        if (i == 0) return -1;
        try {
            return Integer.parseInt(s.substring(0, i));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ---------- Descarga ----------

    /** Descarga los radares de España y los guarda. Devuelve cuántos hay. */
    public static int descargar(Context c) throws IOException {
        IOException ultimoError = null;
        for (String servidor : SERVIDORES) {
            try {
                String json = pedir(servidor);
                List<Radar> prueba = new ArrayList<>();
                parsear(json, prueba);
                if (prueba.isEmpty()) throw new IOException("La respuesta no tenía radares");
                File tmp = new File(c.getFilesDir(), FICHERO + ".tmp");
                try (OutputStream out = new FileOutputStream(tmp)) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }
                File f = new File(c.getFilesDir(), FICHERO);
                if (!tmp.renameTo(f)) throw new IOException("No se pudo guardar el fichero");
                return prueba.size();
            } catch (IOException e) {
                ultimoError = e;
            }
        }
        throw ultimoError != null ? ultimoError : new IOException("Error desconocido");
    }

    static String pedir(String servidor) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(servidor).openConnection();
        con.setConnectTimeout(20000);
        con.setReadTimeout(100000);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        con.setRequestProperty("User-Agent", "AvisaRadar/1.0 (app personal)");
        byte[] cuerpo = ("data=" + URLEncoder.encode(CONSULTA, "UTF-8")).getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = con.getOutputStream()) {
            out.write(cuerpo);
        }
        int codigo = con.getResponseCode();
        if (codigo != 200) {
            con.disconnect();
            throw new IOException("El servidor respondió " + codigo);
        }
        try (InputStream in = con.getInputStream()) {
            return leer(in);
        } finally {
            con.disconnect();
        }
    }

    static String leer(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) buf.write(b, 0, n);
        return buf.toString("UTF-8");
    }

    // ---------- Avisos propios ----------

    public static List<Radar> propios(Context c) {
        List<Radar> lista = new ArrayList<>();
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(p.getString(CLAVE_PROPIOS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Radar r = new Radar();
                r.lat = o.getDouble("lat");
                r.lon = o.getDouble("lon");
                r.propio = true;
                lista.add(r);
            }
        } catch (JSONException ignored) {
        }
        return lista;
    }

    public static void anadirPropio(Context c, double lat, double lon) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(p.getString(CLAVE_PROPIOS, "[]"));
            JSONObject o = new JSONObject();
            o.put("lat", lat);
            o.put("lon", lon);
            arr.put(o);
            p.edit().putString(CLAVE_PROPIOS, arr.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    public static void borrarPropios(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(CLAVE_PROPIOS).apply();
    }
}
