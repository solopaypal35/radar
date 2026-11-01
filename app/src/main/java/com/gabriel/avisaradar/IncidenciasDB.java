package com.gabriel.avisaradar;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Incidencias de tráfico en tiempo real (formato DATEX II) del Punto de Acceso Nacional de la DGT.
 * DGT: toda España salvo Cataluña y País Vasco. SCT: Cataluña. DT-GV: País Vasco.
 */
public class IncidenciasDB {

    public static class Incidencia {
        public double lat, lon;
        public String tipo;        // texto para decir en voz alta: "Accidente", "Obras"...
        public String carretera = "";
        public boolean importante; // accidentes, retenciones, obstáculos, cortes
    }

    static final String[] FUENTES = {
            "https://nap.dgt.es/datex2/v3/dgt/SituationPublication/datex2_v37.xml",
            "https://nap.dgt.es/datex2/sct/SituationPublication/all/content.xml",
            "https://nap.dgt.es/datex2/dt-gv/SituationPublication/all/content.xml"
    };

    /** Última lista descargada (la comparten el servicio y el mapa). */
    public static volatile List<Incidencia> actuales = Collections.emptyList();
    public static volatile long ultimaDescarga = 0;

    /** Descarga todas las fuentes. Si una falla, sigue con las demás. */
    public static void actualizar() {
        List<Incidencia> todas = new ArrayList<>();
        int correctas = 0;
        for (String url : FUENTES) {
            try {
                todas.addAll(descargar(url));
                correctas++;
            } catch (Exception ignored) {
            }
        }
        if (correctas > 0) {
            actuales = todas;
            ultimaDescarga = System.currentTimeMillis();
        }
    }

    static List<Incidencia> descargar(String direccion) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(direccion).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(60000);
        con.setRequestProperty("User-Agent", "AvisaRadar/1.2 (app personal)");
        try (InputStream in = new BufferedInputStream(con.getInputStream())) {
            if (con.getResponseCode() != 200) throw new Exception("HTTP " + con.getResponseCode());
            return leer(in);
        } finally {
            con.disconnect();
        }
    }

    /** Lector genérico que sirve para DATEX II 1.0, 2.x y 3.x. */
    static List<Incidencia> leer(InputStream in) throws Exception {
        List<Incidencia> lista = new ArrayList<>();
        XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        p.setInput(in, null);

        boolean dentro = false;
        int profundidad = 0;
        String tipoRegistro = null;
        String detalle = null;
        String carretera = "";
        double lat = Double.NaN, lon = Double.NaN;
        String etiqueta = null;

        int ev = p.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                String nombre = p.getName();
                if (!dentro && "situationRecord".equals(nombre)) {
                    dentro = true;
                    profundidad = p.getDepth();
                    tipoRegistro = sinPrefijo(atributoTipo(p));
                    detalle = null;
                    carretera = "";
                    lat = Double.NaN;
                    lon = Double.NaN;
                }
                etiqueta = nombre;
            } else if (ev == XmlPullParser.TEXT && dentro && etiqueta != null) {
                String t = p.getText().trim();
                if (!t.isEmpty()) {
                    switch (etiqueta) {
                        case "latitude":
                            if (Double.isNaN(lat)) lat = numero(t);
                            break;
                        case "longitude":
                            if (Double.isNaN(lon)) lon = numero(t);
                            break;
                        case "roadNumber":
                        case "roadName":
                            if (carretera.isEmpty()) carretera = t;
                            break;
                        case "accidentType":
                        case "roadOrCarriagewayOrLaneManagementType":
                        case "roadMaintenanceType":
                        case "abnormalTrafficType":
                        case "vehicleObstructionType":
                        case "obstructionType":
                        case "animalPresenceType":
                        case "poorEnvironmentType":
                        case "nonWeatherRelatedRoadConditionType":
                        case "weatherRelatedRoadConditionType":
                            detalle = (detalle == null ? "" : detalle) + etiqueta + ":" + t + ";";
                            break;
                        default:
                            break;
                    }
                }
            } else if (ev == XmlPullParser.END_TAG) {
                etiqueta = null;
                if (dentro && p.getDepth() == profundidad && "situationRecord".equals(p.getName())) {
                    dentro = false;
                    if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                        Incidencia i = clasificar(tipoRegistro, detalle);
                        if (i != null) {
                            i.lat = lat;
                            i.lon = lon;
                            i.carretera = carretera;
                            lista.add(i);
                        }
                    }
                }
            }
            ev = p.next();
        }
        return lista;
    }

    /** Convierte el tipo DATEX en una frase corta en español. Devuelve null si no interesa. */
    static Incidencia clasificar(String tipo, String detalle) {
        if (tipo == null) tipo = "";
        String d = detalle == null ? "" : detalle;
        Incidencia i = new Incidencia();
        i.importante = true;
        if (tipo.equals("Accident")) {
            i.tipo = "Accidente";
        } else if (tipo.equals("AbnormalTraffic")) {
            i.tipo = d.contains("stationary") || d.contains("queuing") ? "Retención" : "Tráfico lento";
        } else if (tipo.equals("VehicleObstruction")) {
            i.tipo = "Vehículo detenido";
        } else if (tipo.equals("AnimalPresenceObstruction")) {
            i.tipo = "Animales en la vía";
        } else if (tipo.contains("Obstruction")) {
            i.tipo = "Obstáculo en la vía";
        } else if (tipo.equals("RoadOrCarriagewayOrLaneManagement") || tipo.equals("NetworkManagement")) {
            boolean obras = d.contains("roadworks") || d.contains("maintenance") || d.contains("Maintenance");
            if (d.contains("roadClosed") || d.contains("carriagewayClosures")) {
                i.tipo = "Carretera cortada";
            } else if (d.contains("lane") || d.contains("Lane") || d.contains("narrow")) {
                i.tipo = obras ? "Carril cortado por obras" : "Carril cortado";
            } else {
                i.tipo = "Restricción de tráfico";
                i.importante = false;
            }
        } else if (tipo.contains("Maintenance") || tipo.contains("Construction") || tipo.contains("Roadworks")) {
            i.tipo = "Obras";
            i.importante = false;
        } else if (tipo.contains("Weather") || tipo.contains("PoorEnvironment")) {
            i.tipo = "Mal tiempo";
        } else if (tipo.contains("RoadCondition")) {
            i.tipo = "Calzada en mal estado";
        } else if (tipo.contains("Event") || tipo.contains("Activity")) {
            i.tipo = "Evento en la vía";
            i.importante = false;
        } else {
            return null;
        }
        return i;
    }

    static String atributoTipo(XmlPullParser p) {
        for (int i = 0; i < p.getAttributeCount(); i++) {
            if ("type".equals(p.getAttributeName(i))) return p.getAttributeValue(i);
        }
        return null;
    }

    static String sinPrefijo(String s) {
        if (s == null) return null;
        int i = s.indexOf(':');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    static double numero(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
