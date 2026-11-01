package com.gabriel.avisaradar;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Pantalla de ajustes, ordenada por secciones. */
public class AjustesActivity extends Pantalla {

    private final TextView[] chipsTipo = new TextView[3];
    private final TextView[] chipsComb = new TextView[Gasolineras.NOMBRES.length];
    private final TextView[] metrosDist = new TextView[4];
    private final TextView[][] chipsDist = new TextView[4][3];
    private TextView radaresEstado;
    private boolean descargando = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        colorearBarras();

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(16), dp(18), dp(32));

        // Cabecera con botón de volver
        LinearLayout cabecera = new LinearLayout(this);
        cabecera.setGravity(Gravity.CENTER_VERTICAL);
        TextView volver = texto("←", 24, TEXTO, true);
        volver.setPadding(dp(4), dp(4), dp(16), dp(4));
        pulsable(volver, v -> finish());
        cabecera.addView(volver);
        cabecera.addView(texto("Ajustes", 24, TEXTO, true));
        col.addView(cabecera);

        // ---------- Avisos ----------
        col.addView(seccion("Avisos"));
        LinearLayout avisos = tarjeta();
        avisos.addView(fila("Cómo avisa", null, null));
        avisos.addView(filaChips(new String[]{"Voz", "Pitido", "Vibrar"}, chipsTipo, 74,
                pos -> Ajustes.setTipoAviso(this, pos)));
        marcarChips(chipsTipo, Ajustes.tipoAviso(this));
        espacio(avisos, 10);
        avisos.addView(separador());
        Switch solo = interruptor("Solo si me paso del límite", Ajustes.soloSiMePaso(this));
        solo.setOnCheckedChangeListener((b, si) -> Ajustes.setSoloSiMePaso(this, si));
        avisos.addView(solo);
        col.addView(avisos);

        // ---------- Distancias de los radares ----------
        col.addView(seccion("Distancias de los radares"));
        LinearLayout dist = tarjeta();
        TextView expl = texto("Toca los metros para cambiarlos. En cada distancia elige si suena con voz, "
                + "solo si te pasas, o en silencio (solo notificación).", 12, SUB, false);
        expl.setPadding(0, 0, 0, dp(6));
        dist.addView(expl);
        int[] d = Ajustes.distancias(this);
        for (int i = 0; i < 4; i++) {
            final int pos = i;
            if (i > 0) dist.addView(separador());
            LinearLayout f = new LinearLayout(this);
            f.setGravity(Gravity.CENTER_VERTICAL);
            f.setPadding(0, dp(10), 0, dp(10));
            metrosDist[i] = botonPequeno(d[i] + " m");
            metrosDist[i].setTextColor(VERDE);
            pulsable(metrosDist[i], v -> editarMetros(pos));
            f.addView(metrosDist[i], new LinearLayout.LayoutParams(dp(64), dp(36)));
            LinearLayout chips = filaChips(new String[]{"Voz", "Si paso", "Silencio"}, chipsDist[i], 62,
                    modo -> {
                        Ajustes.setModoDistancia(this, pos, modo);
                        reiniciarSiActivo();
                    });
            LinearLayout.LayoutParams lpChips = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lpChips.leftMargin = dp(8);
            chips.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            f.addView(chips, lpChips);
            marcarChips(chipsDist[i], Ajustes.modoDistancia(this, i));
            dist.addView(f);
        }
        TextView porDefecto = texto("Volver a 500 · 200 · 100 · 50 m", 13, SUB, false);
        porDefecto.setPadding(0, dp(8), 0, 0);
        pulsable(porDefecto, v -> {
            Ajustes.setDistancias(this, new int[]{500, 200, 100, 50});
            int[] modos = {Ajustes.DIST_VOZ, Ajustes.DIST_SI_ME_PASO, Ajustes.DIST_VOZ, Ajustes.DIST_VOZ};
            for (int i = 0; i < 4; i++) {
                Ajustes.setModoDistancia(this, i, modos[i]);
                marcarChips(chipsDist[i], modos[i]);
            }
            pintarMetros();
            reiniciarSiActivo();
        });
        dist.addView(porDefecto);
        col.addView(dist);

        // ---------- Sonido de cada aviso ----------
        col.addView(seccion("Sonido de cada aviso"));
        LinearLayout sonidos = tarjeta();
        for (int i = 0; i < Ajustes.TIPOS.length; i++) {
            final String tipo = Ajustes.TIPOS[i];
            if (i > 0) sonidos.addView(separador());
            Switch s = interruptor(Ajustes.NOMBRES_TIPOS[i], Ajustes.sonido(this, tipo));
            s.setOnCheckedChangeListener((b, si) -> Ajustes.setSonido(this, tipo, si));
            sonidos.addView(s);
        }
        col.addView(sonidos);

        // ---------- Tráfico ----------
        col.addView(seccion("Tráfico"));
        LinearLayout trafico = tarjeta();
        Switch traf = interruptor("Avisos de la DGT", Ajustes.avisosTrafico(this));
        traf.setOnCheckedChangeListener((b, si) -> {
            Ajustes.setAvisosTrafico(this, si);
            reiniciarSiActivo();
        });
        trafico.addView(traf);
        col.addView(trafico);

        // ---------- Mapa y gasolineras ----------
        col.addView(seccion("Mapa y gasolineras"));
        LinearLayout mapa = tarjeta();
        Switch usarMapa = interruptor("Mapa", Ajustes.usarMapa(this));
        usarMapa.setOnCheckedChangeListener((b, si) -> Ajustes.setUsarMapa(this, si));
        mapa.addView(usarMapa);
        mapa.addView(separador());
        Switch gas = interruptor("Gasolineras en el mapa", Ajustes.gasolineras(this));
        gas.setOnCheckedChangeListener((b, si) -> Ajustes.setGasolineras(this, si));
        mapa.addView(gas);
        mapa.addView(separador());
        mapa.addView(fila("Tu combustible", null, null));
        mapa.addView(filaChips(Gasolineras.NOMBRES, chipsComb, 66, pos -> Ajustes.setCombustible(this, pos)));
        marcarChips(chipsComb, Ajustes.combustible(this));
        espacio(mapa, 10);
        col.addView(mapa);

        // ---------- Conducción ----------
        col.addView(seccion("Conducción"));
        LinearLayout cond = tarjeta();
        Switch burbuja = interruptor("Burbuja flotante", Ajustes.burbuja(this));
        burbuja.setOnCheckedChangeListener((b, si) -> {
            Ajustes.setBurbuja(this, si);
            if (si && !Burbuja.tienePermiso(this)) {
                toast("Activa «Mostrar sobre otras apps» para AvisaRadar y vuelve");
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
            reiniciarSiActivo();
        });
        cond.addView(burbuja);
        cond.addView(separador());
        Switch aparcar = interruptor("Guardar aparcamiento solo", Ajustes.aparcarAuto(this));
        aparcar.setOnCheckedChangeListener((b, si) -> {
            Ajustes.setAparcarAuto(this, si);
            if (si && Build.VERSION.SDK_INT >= 31
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 3);
            }
        });
        cond.addView(aparcar);
        col.addView(cond);

        // ---------- Datos ----------
        col.addView(seccion("Datos"));
        LinearLayout datos = tarjeta();
        TextView actualizar = texto("Actualizar", 14, VERDE, true);
        pulsable(actualizar, v -> actualizarRadares());
        LinearLayout filaRad = fila("Radares y tramos", "", actualizar);
        radaresEstado = (TextView) ((LinearLayout) filaRad.getChildAt(0)).getChildAt(1);
        datos.addView(filaRad);
        col.addView(datos);

        TextView creditos = texto("Radares, tramos, semáforos y peajes: © OpenStreetMap. "
                + "Tráfico: DGT, SCT y Gobierno Vasco. Precios: Ministerio para la Transición Ecológica.",
                11, SUB, false);
        creditos.setPadding(dp(6), dp(20), dp(6), 0);
        col.addView(creditos);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(FONDO);
        scroll.addView(col);
        setContentView(scroll);
        pintarRadares();
    }

    private void espacio(LinearLayout l, int dpAlto) {
        android.view.View v = new android.view.View(this);
        l.addView(v, new LinearLayout.LayoutParams(1, dp(dpAlto)));
    }

    private void pintarMetros() {
        int[] d = Ajustes.distancias(this);
        for (int i = 0; i < 4; i++) metrosDist[i].setText(d[i] + " m");
    }

    /** Cambia los metros de una distancia, manteniendo el orden de mayor a menor. */
    private void editarMetros(int pos) {
        int[] d = Ajustes.distancias(this);
        EditText campo = new EditText(this);
        campo.setInputType(InputType.TYPE_CLASS_NUMBER);
        campo.setText(String.valueOf(d[pos]));
        campo.setSelectAllOnFocus(true);
        LinearLayout caja = new LinearLayout(this);
        caja.setPadding(dp(22), dp(8), dp(22), 0);
        caja.addView(campo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        String[] nombres = {"1er aviso", "2º aviso", "3er aviso", "4º aviso"};
        new AlertDialog.Builder(this)
                .setTitle(nombres[pos] + " — metros")
                .setView(caja)
                .setPositiveButton("Guardar", (dlg, w) -> {
                    int v;
                    try {
                        v = Integer.parseInt(campo.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        toast("Escribe solo números");
                        return;
                    }
                    int max = pos == 0 ? 2000 : d[pos - 1] - 10;
                    int min = pos == 3 ? 20 : d[pos + 1] + 10;
                    if (v < min || v > max) {
                        toast("Tiene que estar entre " + min + " y " + max + " m");
                        return;
                    }
                    d[pos] = v;
                    Ajustes.setDistancias(this, d);
                    pintarMetros();
                    reiniciarSiActivo();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void pintarRadares() {
        int n = RadarDB.contarOficiales(this);
        int dias = RadarDB.diasSinActualizar(this);
        radaresEstado.setText(n == 0 ? "Sin descargar" : n + " radares · " + MainActivity.cuando(dias));
    }

    private void actualizarRadares() {
        if (descargando) return;
        descargando = true;
        radaresEstado.setText("Descargando…");
        new Thread(() -> {
            try {
                int n = RadarDB.descargar(this);
                try {
                    TramosDB.descargar(this);
                } catch (Exception ignored) {
                }
                runOnUiThread(() -> {
                    descargando = false;
                    toast(n + " radares descargados");
                    reiniciarSiActivo();
                    pintarRadares();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    descargando = false;
                    toast("No se pudo descargar. Revisa la conexión.");
                    pintarRadares();
                });
            }
        }).start();
    }
}
