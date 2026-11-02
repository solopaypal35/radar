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

    /** Devuelve todas las cámaras (descarga la lista si no hay copia o tiene más de un día). */
    public static List<Camara> obtener(Context c) {
        File f = new File(c.getFilesDir(), FICHERO);
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > VALIDEZ) {
            descargar(f);
        }
        List<Camara> lista = new ArrayList<>();
        if (!f.exists()) return lista;
        try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
            leer(in, lista);
        } catch (Exception ignored) {
        }
        return lista;
    }

    static void descargar(File destino) {
        File tmp = new File(destino.getPath() + ".tmp");
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(URL_LISTA).openConnection();
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
            if (tmp.length() > 10000) tmp.renameTo(destino);
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
