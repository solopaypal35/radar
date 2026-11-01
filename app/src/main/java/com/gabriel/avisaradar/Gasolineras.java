package com.gabriel.avisaradar;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Precios de las gasolineras de España (datos abiertos del Ministerio, actualizados a diario).
 */
public class Gasolineras {

    public static class Estacion {
        public double lat, lon, precio;
        public String rotulo = "", direccion = "", localidad = "", horario = "";
    }

    /** Combustibles: identificador del Ministerio, nombre corto y campo del listado completo. */
    public static final int[] IDS = {1, 3, 4, 5};
    public static final String[] NOMBRES = {"95", "98", "Diésel", "Diésel+"};
    public static final String[] NOMBRES_LARGOS = {"Gasolina 95", "Gasolina 98", "Diésel", "Diésel Premium"};
    static final String[] CAMPOS = {"Precio Gasolina 95 E5", "Precio Gasolina 98 E5",
            "Precio Gasoleo A", "Precio Gasoleo Premium"};

    static final String BASE =
            "https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/";
    static final long VALIDEZ = 6 * 60 * 60 * 1000L; // los precios se guardan 6 horas

    /** Devuelve las gasolineras con precio del combustible elegido (usa la copia guardada si es reciente). */
    public static List<Estacion> obtener(Context c, int indice) {
        File f = new File(c.getCacheDir(), "gasolineras_" + IDS[indice] + ".json");
        String json = null;
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > VALIDEZ) {
            json = descargar(BASE + "FiltroProducto/" + IDS[indice]);
            if (json == null) json = descargar(BASE); // plan B: el listado completo
            if (json != null) {
                try (OutputStream out = new FileOutputStream(f)) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) {
                }
            }
        }
        if (json == null && f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                json = RadarDB.leer(in);
            } catch (Exception ignored) {
            }
        }
        List<Estacion> lista = new ArrayList<>();
        if (json != null) parsear(json, CAMPOS[indice], lista);
        return lista;
    }

    static String descargar(String direccion) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(direccion).openConnection();
            con.setConnectTimeout(15000);
            con.setReadTimeout(60000);
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("User-Agent", "AlLoro (app personal)");
            try (InputStream in = con.getInputStream()) {
                if (con.getResponseCode() != 200) return null;
                String s = RadarDB.leer(in);
                return s.contains("ListaEESSPrecio") ? s : null;
            } finally {
                con.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    static void parsear(String json, String campoCompleto, List<Estacion> lista) {
        try {
            JSONArray arr = new JSONObject(json).getJSONArray("ListaEESSPrecio");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String precio = o.optString("PrecioProducto", "");
                if (precio.isEmpty()) precio = o.optString(campoCompleto, "");
                double p = numero(precio);
                double lat = numero(o.optString("Latitud", ""));
                double lon = numero(o.optString("Longitud (WGS84)", ""));
                if (Double.isNaN(p) || p <= 0 || Double.isNaN(lat) || Double.isNaN(lon)) continue;
                Estacion e = new Estacion();
                e.lat = lat;
                e.lon = lon;
                e.precio = p;
                e.rotulo = o.optString("Rótulo", "").trim();
                e.direccion = o.optString("Dirección", "").trim();
                e.localidad = o.optString("Localidad", "").trim();
                e.horario = o.optString("Horario", "").trim();
                lista.add(e);
            }
        } catch (Exception ignored) {
        }
    }

    /** Los datos usan coma decimal: "1,489". */
    static double numero(String s) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return Double.NaN;
        }
    }
}
