package com.gabriel.avisaradar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/** Límite de velocidad de la carretera por la que vas (OpenStreetMap, necesita datos móviles). */
public class LimiteVia {

    /** Consulta las carreteras a menos de 25 m y devuelve el límite, o 0 si no se sabe. */
    static int consultar(double lat, double lon) {
        String q = String.format(Locale.US,
                "[out:json][timeout:10];way(around:25,%.6f,%.6f)[highway~\"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link)$\"];out tags geom;",
                lat, lon);
        try {
            String json = RadarDB.consultar(q, 15000);
            JSONArray els = new JSONObject(json).getJSONArray("elements");
            // Se queda con la vía más cercana; si hay empate (cruces), con la más importante
            int mejor = 0;
            double puntuacionMejor = Double.MAX_VALUE;
            for (int i = 0; i < els.length(); i++) {
                JSONObject el = els.getJSONObject(i);
                JSONObject tags = el.optJSONObject("tags");
                if (tags == null) continue;
                String via = tags.optString("highway");
                int lim = interpretar(tags.optString("maxspeed", ""), via);
                if (lim <= 0) continue;
                double dist = distanciaAVia(el.optJSONArray("geometry"), lat, lon);
                double puntuacion = dist + prioridad(via) * 2.0;
                if (puntuacion < puntuacionMejor) {
                    puntuacionMejor = puntuacion;
                    mejor = lim;
                }
            }
            return mejor;
        } catch (Exception e) {
            return -1; // error de red: se mantiene el último valor
        }
    }

    /** Distancia aproximada en metros del punto a la línea de la vía. */
    static double distanciaAVia(JSONArray geom, double lat, double lon) {
        if (geom == null || geom.length() == 0) return 25;
        double kx = 111320 * Math.cos(Math.toRadians(lat)), ky = 110540;
        double min = Double.MAX_VALUE;
        double px = 0, py = 0;
        for (int i = 0; i < geom.length(); i++) {
            JSONObject g = geom.optJSONObject(i);
            if (g == null) continue;
            double x = (g.optDouble("lon") - lon) * kx, y = (g.optDouble("lat") - lat) * ky;
            if (i == 0) {
                min = Math.hypot(x, y);
            } else {
                double dx = x - px, dy = y - py, l2 = dx * dx + dy * dy;
                double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, -(px * dx + py * dy) / l2));
                min = Math.min(min, Math.hypot(px + t * dx, py + t * dy));
            }
            px = x;
            py = y;
        }
        return min;
    }

    /** Convierte el valor de OpenStreetMap a km/h. Si no hay, estima por el tipo de vía. */
    static int interpretar(String v, String via) {
        int n = RadarDB.numero(v);
        if (n > 0) return n;
        switch (v) {
            case "ES:urban":
                return 50;
            case "ES:zone30":
                return 30;
            case "ES:zone20":
            case "ES:living_street":
                return 20;
            case "ES:rural":
                return 90;
            case "ES:trunk":
            case "ES:motorway":
                return 120;
            default:
                break;
        }
        if (v.isEmpty()) {
            // Límites genéricos en España cuando la vía no tiene límite anotado
            switch (via) {
                case "motorway":
                    return 120;
                case "living_street":
                    return 20;
                default:
                    return 0;
            }
        }
        return 0;
    }

    /** Prefiere la vía principal si hay varias cerca (por ejemplo en un cruce). */
    static int prioridad(String via) {
        switch (via) {
            case "motorway":
            case "trunk":
                return 1;
            case "primary":
                return 2;
            case "secondary":
                return 3;
            case "tertiary":
                return 4;
            case "motorway_link":
            case "trunk_link":
            case "primary_link":
            case "secondary_link":
            case "tertiary_link":
                return 5;
            case "unclassified":
            case "residential":
                return 6;
            default:
                return 7;
        }
    }
}
