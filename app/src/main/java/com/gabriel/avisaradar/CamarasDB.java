package com.gabriel.avisaradar;

import android.content.Context;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Cámaras de tráfico de la DGT (datos abiertos, formato DATEX II).
 * Cada cámara tiene su posición, la carretera, el punto kilométrico y una foto en directo.
 * La lista se descarga una vez al día y se guarda en el móvil.
 */
public class CamarasDB {

    public static class Camara {
        public double lat, lon;
        public String carretera = "", destino = "", km = "", provincia = "", imagen = "";
        public float distancia; // metros hasta ti (se rellena al ordenar)
    }

    static final String URL_LISTA = "https://nap.dgt.es/datex2/v3/dgt/DevicePublication/camaras_datex2_v36.xml";
    static final String FICHERO = "camaras.xml";
    static final long VALIDEZ = 24 * 60 * 60 * 1000L;

    static final String URL_CATALUNYA = "http://www.gencat.cat/transit/opendata/cameres.xml";
    static final String URL_EUSKADI = "https://api.euskadi.eus/traffic/v1.0/cameras?_page=";

    /**
     * Devuelve todas las cámaras: DGT (toda España salvo Cataluña y País Vasco),
     * Servei Català de Trànsit y tráfico de Euskadi. Cada lista se descarga como mucho una vez al día.
     */
    public static List<Camara> obtener(Context c) {
        List<Camara> lista = new ArrayList<>();
        // DGT
        File f = new File(c.getFilesDir(), FICHERO);
        if (caducado(f)) descargar(URL_LISTA, f);
        if (f.exists()) {
            try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
                leer(in, lista);
            } catch (Exception ignored) {
            }
        }
        // Cataluña
        File fc = new File(c.getFilesDir(), "camaras_cat.xml");
        if (caducado(fc)) descargar(URL_CATALUNYA, fc);
        if (fc.exists()) {
            try (InputStream in = new BufferedInputStream(new FileInputStream(fc))) {
                leerCatalunya(in, lista);
            } catch (Exception ignored) {
            }
        }
        // País Vasco
        File fe = new File(c.getFilesDir(), "camaras_eus.json");
        if (caducado(fe)) descargarEuskadi(fe);
        if (fe.exists()) {
            try (InputStream in = new FileInputStream(fe)) {
                leerEuskadi(RadarDB.leer(in), lista);
            } catch (Exception ignored) {
            }
        }
        return lista;
    }

    static boolean caducado(File f) {
        return !f.exists() || System.currentTimeMillis() - f.lastModified() > VALIDEZ;
    }

    static void descargar(File destino) {
        descargar(URL_LISTA, destino);
    }

    // ---------- Cataluña (Servei Català de Trànsit) ----------

    static void leerCatalunya(InputStream in, List<Camara> lista) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        p.setInput(in, null);
        Camara actual = null;
        String etiqueta = null;
        int ev = p.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                etiqueta = p.getName();
                if ("cameres".equals(etiqueta)) actual = new Camara();
            } else if (ev == XmlPullParser.TEXT && actual != null && etiqueta != null) {
                String t = p.getText().trim();
                if (!t.isEmpty()) {
                    switch (etiqueta) {
                        case "coordinates": {
                            String[] xy = t.split(",");
                            if (xy.length >= 2) {
                                actual.lon = numero(xy[0]);
                                actual.lat = numero(xy[1]);
                            }
                            break;
                        }
                        case "carretera":
                            actual.carretera = t;
                            break;
                        case "municipi":
                            actual.provincia = t;
                            break;
                        case "pk":
                            actual.km = t;
                            break;
                        case "link":
                            actual.imagen = t;
                            break;
                        default:
                            break;
                    }
                }
            } else if (ev == XmlPullParser.END_TAG) {
                if ("cameres".equals(p.getName()) && actual != null) {
                    if (actual.lat != 0 && !Double.isNaN(actual.lat) && actual.imagen.startsWith("http")) lista.add(actual);
                    actual = null;
                }
                etiqueta = null;
            }
            ev = p.next();
        }
    }

    // ---------- País Vasco (Open Data Euskadi) ----------

    /** La API va por páginas: se juntan todas en un solo fichero JSON (un array de cámaras). */
    static void descargarEuskadi(File destino) {
        try {
            org.json.JSONArray todas = new org.json.JSONArray();
            int pagina = 1, total = 1;
            while (pagina <= total && pagina <= 60) {
                String txt = pedirTexto(URL_EUSKADI + pagina);
                if (txt == null) return;
                org.json.JSONObject o = new org.json.JSONObject(txt);
                total = o.optInt("totalPages", 1);
                org.json.JSONArray cams = o.optJSONArray("cameras");
                if (cams != null) for (int i = 0; i < cams.length(); i++) todas.put(cams.get(i));
                pagina++;
            }
            if (todas.length() == 0) return;
            try (OutputStream out = new FileOutputStream(destino)) {
                out.write(todas.toString().getBytes("UTF-8"));
            }
        } catch (Exception ignored) {
        }
    }

    static void leerEuskadi(String json, List<Camara> lista) throws Exception {
        org.json.JSONArray arr = new org.json.JSONArray(json);
        for (int i = 0; i < arr.length(); i++) {
            org.json.JSONObject o = arr.getJSONObject(i);
            String img = o.optString("urlImage", "");
            if (!img.startsWith("http") || !img.substring(7).contains(".")) continue;
            double y = numero(o.optString("latitude", "")), x = numero(o.optString("longitude", ""));
            if (Double.isNaN(x) || Double.isNaN(y)) continue;
            Camara c = new Camara();
            if (Math.abs(y) <= 90 && Math.abs(x) <= 180) {
                c.lat = y; // ya viene en grados
                c.lon = x;
            } else {
                double[] g = utmAGrados(x, y, 30); // UTM huso 30 (ETRS89)
                c.lat = g[0];
                c.lon = g[1];
            }
            c.carretera = o.optString("road", "").replace(" - ", "-");
            String km = o.optString("kilometer", "");
            c.km = km.replace("+", ".").replaceFirst("^0+(?=\\d)", "");
            c.provincia = o.optString("address", "");
            c.destino = "";
            c.imagen = img;
            lista.add(c);
        }
    }

    /** Convierte coordenadas UTM (hemisferio norte) a latitud y longitud. */
    static double[] utmAGrados(double este, double norte, int huso) {
        double a = 6378137.0, f = 1 / 298.257222101, k0 = 0.9996;
        double e2 = f * (2 - f), ep2 = e2 / (1 - e2);
        double x = este - 500000.0, y = norte;
        double m = y / k0;
        double mu = m / (a * (1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256));
        double e1 = (1 - Math.sqrt(1 - e2)) / (1 + Math.sqrt(1 - e2));
        double phi1 = mu + (3 * e1 / 2 - 27 * Math.pow(e1, 3) / 32) * Math.sin(2 * mu)
                + (21 * e1 * e1 / 16 - 55 * Math.pow(e1, 4) / 32) * Math.sin(4 * mu)
                + (151 * Math.pow(e1, 3) / 96) * Math.sin(6 * mu);
        double n1 = a / Math.sqrt(1 - e2 * Math.sin(phi1) * Math.sin(phi1));
        double t1 = Math.tan(phi1) * Math.tan(phi1);
        double c1 = ep2 * Math.cos(phi1) * Math.cos(phi1);
        double r1 = a * (1 - e2) / Math.pow(1 - e2 * Math.sin(phi1) * Math.sin(phi1), 1.5);
        double d = x / (n1 * k0);
        double lat = phi1 - (n1 * Math.tan(phi1) / r1) * (d * d / 2
                - (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * ep2) * Math.pow(d, 4) / 24
                + (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * ep2 - 3 * c1 * c1) * Math.pow(d, 6) / 720);
        double lon = (d - (1 + 2 * t1 + c1) * Math.pow(d, 3) / 6
                + (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * ep2 + 24 * t1 * t1) * Math.pow(d, 5) / 120) / Math.cos(phi1);
        double lon0 = Math.toRadians((huso - 1) * 6 - 180 + 3);
        return new double[]{Math.toDegrees(lat), Math.toDegrees(lon0 + lon)};
    }

    static String pedirTexto(String direccion) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(direccion).openConnection();
            con.setConnectTimeout(15000);
            con.setReadTimeout(30000);
            con.setRequestProperty("User-Agent", "AlLoro (app personal)");
            con.setRequestProperty("Accept", "application/json");
            try (InputStream in = con.getInputStream()) {
                return con.getResponseCode() == 200 ? RadarDB.leer(in) : null;
            } finally {
                con.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    static void descargar(String direccion, File destino) {
        File tmp = new File(destino.getPath() + ".tmp");
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(direccion).openConnection();
            con.setConnectTimeout(15000);
            con.setReadTimeout(60000);
            con.setRequestProperty("User-Agent", "AlLoro (app personal)");
            if (con.getResponseCode() != 200) return;
            try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] b = new byte[32768];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
            } finally {
                con.disconnect();
            }
            if (tmp.length() > 2000) tmp.renameTo(destino);
        } catch (Exception ignored) {
            tmp.delete();
        }
    }

    static void leer(InputStream in, List<Camara> lista) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        p.setInput(in, null);
        Camara actual = null;
        String etiqueta = null;
        int ev = p.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                etiqueta = p.getName();
                if ("device".equals(etiqueta)) {
                    actual = new Camara();
                    actual.lat = Double.NaN;
                    actual.lon = Double.NaN;
                }
            } else if (ev == XmlPullParser.TEXT && actual != null && etiqueta != null) {
                String t = p.getText().trim();
                if (!t.isEmpty()) {
                    switch (etiqueta) {
                        case "latitude":
                            if (Double.isNaN(actual.lat)) actual.lat = numero(t);
                            break;
                        case "longitude":
                            if (Double.isNaN(actual.lon)) actual.lon = numero(t);
                            break;
                        case "roadName":
                            actual.carretera = t;
                            break;
                        case "roadDestination":
                            actual.destino = t;
                            break;
                        case "kilometerPoint":
                            actual.km = t;
                            break;
                        case "province":
                            actual.provincia = t;
                            break;
                        case "deviceUrl":
                            actual.imagen = t;
                            break;
                        default:
                            break;
                    }
                }
            } else if (ev == XmlPullParser.END_TAG) {
                if ("device".equals(p.getName()) && actual != null) {
                    if (!Double.isNaN(actual.lat) && !Double.isNaN(actual.lon) && actual.imagen.startsWith("http")) {
                        lista.add(actual);
                    }
                    actual = null;
                }
                etiqueta = null;
            }
            ev = p.next();
        }
    }

    static double numero(String s) {
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (Exception e) {
            return Double.NaN;
        }
    }
}
