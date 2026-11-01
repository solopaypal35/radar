package com.gabriel.avisaradar;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Pantalla {


    /** A partir de cuántos días se avisa de que hay que actualizar los radares. */
    static final int DIAS_ACTUALIZAR = 30;

    private View botonActivar;
    private ImageView iconoPower;
    private TextView textoPower;
    static final int VERDE_ENCENDER = 0xFF22C55E;
    private TextView estadoCabecera, aparcTexto, titularHero, etiquetaHero, senalLimite, botonMapa;
    private VelocimetroView velocimetro;
    private double aparcLatMostrada = Double.NaN;
    private ImageView fotoAparc;
    private String fotoMostrada = null;
    static final int PEDIR_FOTO = 20;
    private LinearLayout tarjetaRadares, tarjetaBateria;
    private TextView radaresTitulo, radaresSub, radaresAccion;
    private ObjectAnimator latidoRadares;
    private boolean descargando = false;

    private final Runnable refresco = new Runnable() {
        @Override
        public void run() {
            refrescar();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        colorearBarras();

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(20), dp(18), dp(28));

        // ---------- Barra superior: logo y píldora de estado ----------
        LinearLayout cabecera = new LinearLayout(this);
        cabecera.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo);
        logo.setBackground(redondo(TARJETA, BORDE, 14));
        cabecera.addView(logo, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView nombre = texto("Al Loro", 18, TEXTO, true);
        nombre.setPadding(dp(12), 0, 0, 0);
        cabecera.addView(nombre, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        estadoCabecera = texto("", 13, TEXTO, true);
        estadoCabecera.setGravity(Gravity.CENTER);
        estadoCabecera.setPadding(dp(16), 0, dp(16), 0);
        cabecera.addView(estadoCabecera, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));
        col.addView(cabecera);

        // ---------- Avisos naranjas (solo aparecen cuando hace falta) ----------
        tarjetaRadares = tarjeta();
        LinearLayout filaRadares = new LinearLayout(this);
        filaRadares.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout textosRadares = new LinearLayout(this);
        textosRadares.setOrientation(LinearLayout.VERTICAL);
        radaresTitulo = texto("Radares", 16, TEXTO, true);
        radaresSub = texto("", 13, SUB, false);
        radaresSub.setPadding(0, dp(4), 0, 0);
        textosRadares.addView(radaresTitulo);
        textosRadares.addView(radaresSub);
        filaRadares.addView(textosRadares, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        radaresAccion = texto("Actualizar", 14, VERDE, true);
        filaRadares.addView(radaresAccion);
        tarjetaRadares.addView(filaRadares);
        pulsable(tarjetaRadares, v -> actualizar());
        col.addView(tarjetaRadares, margenArriba(16));

        tarjetaBateria = tarjeta();
        tarjetaBateria.setBackground(redondo(AMBAR_FONDO, AMBAR, 26));
        tarjetaBateria.addView(texto("Permite que funcione siempre", 16, AMBAR, true));
        TextView expl = texto("Si no, Android puede cerrar Al Loro en viajes largos. Toca aquí y pulsa «Permitir».",
                13, TEXTO, false);
        expl.setPadding(0, dp(6), 0, 0);
        expl.setLineSpacing(dp(2), 1f);
        tarjetaBateria.addView(expl);
        pulsable(tarjetaBateria, v -> pedirSinOptimizar());
        tarjetaBateria.setVisibility(View.GONE); // este aviso vive ahora al final de Ajustes

        // ---------- Tarjeta principal: estado grande + velocímetro ----------
        LinearLayout hero = tarjeta();
        hero.setPadding(dp(22), dp(22), dp(22), dp(22));
        LinearLayout filaHero = new LinearLayout(this);
        filaHero.setGravity(Gravity.CENTER_VERTICAL);
        etiquetaHero = texto("Estado del radar", 12, SUB, false);
        filaHero.addView(etiquetaHero, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        senalLimite = texto("", 15, 0xFF111111, true);
        senalLimite.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable sen = new android.graphics.drawable.GradientDrawable();
        sen.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        sen.setColor(0xFFFFFFFF);
        sen.setStroke(dp(4), 0xFFE0262F);
        senalLimite.setBackground(sen);
        filaHero.addView(senalLimite, new LinearLayout.LayoutParams(dp(42), dp(42)));
        hero.addView(filaHero);
        titularHero = texto("", 36, TEXTO, true);
        titularHero.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        titularHero.setLineSpacing(0, 0.95f);
        titularHero.setPadding(0, dp(10), 0, 0);
        hero.addView(titularHero);
        velocimetro = new VelocimetroView(this);
        LinearLayout.LayoutParams lpVel = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpVel.topMargin = dp(10);
        hero.addView(velocimetro, lpVel);
        botonMapa = texto("Abrir mapa", 15, 0xFF07090A, true);
        botonMapa.setGravity(Gravity.CENTER);
        botonMapa.setBackground(redondo(0xFFFFFFFF, 0xFFFFFFFF, 24));
        pulsable(botonMapa, v -> startActivity(new Intent(this, MapaActivity.class)));
        LinearLayout.LayoutParams lpMapa = new LinearLayout.LayoutParams(dp(150), dp(46));
        lpMapa.topMargin = dp(8);
        // (El mapa se abre desde la barra inferior)
        col.addView(hero, margenArriba(16));

        // ---------- Aparcamiento ----------
        col.addView(tituloSeccion("Aparcamiento", null, null));
        LinearLayout aparc = tarjeta();
        LinearLayout filaAparc = new LinearLayout(this);
        filaAparc.setGravity(Gravity.CENTER_VERTICAL);
        filaAparc.addView(cuadroIcono(R.drawable.ic_parking_b));
        LinearLayout textosAparc = new LinearLayout(this);
        textosAparc.setOrientation(LinearLayout.VERTICAL);
        textosAparc.setPadding(dp(14), 0, dp(8), 0);
        textosAparc.addView(texto("Tu coche", 16, TEXTO, true));
        aparcTexto = texto("", 12, SUB, false);
        aparcTexto.setPadding(0, dp(4), 0, 0);
        textosAparc.addView(aparcTexto);
        filaAparc.addView(textosAparc, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        aparc.addView(filaAparc);
        fotoAparc = new ImageView(this);
        fotoAparc.setScaleType(ImageView.ScaleType.CENTER_CROP);
        fotoAparc.setBackground(redondo(HUNDIDO, HUNDIDO, 18));
        fotoAparc.setClipToOutline(true);
        fotoAparc.setVisibility(View.GONE);
        fotoAparc.setOnClickListener(v -> verFoto());
        fotoAparc.setContentDescription("Foto del aparcamiento (toca para verla)");
        LinearLayout.LayoutParams lpFoto = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(140));
        lpFoto.topMargin = dp(14);
        aparc.addView(fotoAparc, lpFoto);
        LinearLayout accionesAparc = new LinearLayout(this);
        accionesAparc.setGravity(Gravity.TOP);
        accionesAparc.setPadding(0, dp(16), 0, 0);
        accionesAparc.addView(accion(R.drawable.ic_pin, "Guardar ubicación", v -> guardarAparcamientoManual()), pesoAccion());
        accionesAparc.addView(accion(R.drawable.ic_camara, "Foto", v -> hacerFoto()), pesoAccion());
        accionesAparc.addView(accionApp("com.google.android.apps.maps", R.drawable.ic_ruta, "Cómo llegar", v -> comoLlegar()), pesoAccion());
        accionesAparc.addView(accionApp("com.whatsapp", R.drawable.ic_enviar, "WhatsApp", v -> enviarWhatsApp()), pesoAccion());
        aparc.addView(accionesAparc);
        col.addView(aparc);

        // ---------- Música ----------
        col.addView(tituloSeccion("Música", null, null));
        LinearLayout musica = tarjeta();
        LinearLayout filaMusica = new LinearLayout(this);
        filaMusica.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.FrameLayout sp = cuadroApp(Musica.SPOTIFY, R.drawable.ic_musica_b);
        pulsable(sp, v -> Musica.abrirSpotify(this));
        filaMusica.addView(sp);
        View hueco = new View(this);
        filaMusica.addView(hueco, new LinearLayout.LayoutParams(0, 1, 1f));
        filaMusica.addView(botonCircular(R.drawable.ic_anterior, false, v -> Musica.anterior(this)));
        View h1 = new View(this);
        filaMusica.addView(h1, new LinearLayout.LayoutParams(dp(10), 1));
        filaMusica.addView(botonCircular(R.drawable.ic_play, true, v -> Musica.playPausa(this)));
        View h2 = new View(this);
        filaMusica.addView(h2, new LinearLayout.LayoutParams(dp(10), 1));
        filaMusica.addView(botonCircular(R.drawable.ic_siguiente, false, v -> Musica.siguiente(this)));
        musica.addView(filaMusica);
        col.addView(musica);

        // Espacio para que la barra inferior no tape el final
        View pie = new View(this);
        col.addView(pie, new LinearLayout.LayoutParams(1, dp(130)));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(FONDO);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(col);

        // ---------- Barra inferior: navegación en píldora + botón de encendido ----------
        android.widget.FrameLayout raiz = new android.widget.FrameLayout(this);
        raiz.setBackgroundColor(FONDO);
        raiz.addView(scroll);
        LinearLayout barra = new LinearLayout(this);
        barra.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(6), dp(6), dp(6), dp(6));
        nav.setBackground(redondo(0xFF15191A, BORDE, 32));
        nav.addView(navBoton(R.drawable.ic_inicio, true, v -> scroll.smoothScrollTo(0, 0)));
        nav.addView(navBoton(R.drawable.ic_mapa_b, false, v -> {
            if (Ajustes.usarMapa(this)) startActivity(new Intent(this, MapaActivity.class));
            else toast("El mapa está desactivado en Ajustes");
        }));
        nav.addView(navBoton(R.drawable.ic_ajustes, false, v -> startActivity(new Intent(this, AjustesActivity.class))));
        barra.addView(nav);
        View sep = new View(this);
        barra.addView(sep, new LinearLayout.LayoutParams(0, 1, 1f));
        // Botón de encendido: píldora con icono y texto. Verde = «Encender», rojo = «Apagar»
        LinearLayout power = new LinearLayout(this);
        power.setGravity(Gravity.CENTER);
        power.setPadding(dp(18), 0, dp(22), 0);
        iconoPower = icono(R.drawable.ic_power, 0xFFFFFFFF, 22);
        power.addView(iconoPower);
        textoPower = texto("Encender", 16, 0xFFFFFFFF, true);
        textoPower.setPadding(dp(8), 0, 0, 0);
        power.addView(textoPower);
        pulsable(power, v -> {
            vibrarCorto();
            alternar();
        });
        botonActivar = power;
        barra.addView(power, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(64)));
        android.widget.FrameLayout.LayoutParams lpBarra = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        lpBarra.setMargins(dp(18), 0, dp(18), dp(22));
        raiz.addView(barra, lpBarra);
        setContentView(raiz);

        // Animación de entrada: cada bloque aparece deslizándose
        for (int i = 0; i < col.getChildCount(); i++) {
            View v = col.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(dp(24));
            v.animate().alpha(1f).translationY(0).setStartDelay(80L * i).setDuration(450)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refrescarRadares();
        handler.post(refresco);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresco);
    }

    private void refrescar() {
        refrescarAparcamiento();
        boolean activo = RadarService.activo;
        // Botón de encendido: dice lo que va a hacer al pulsarlo
        botonActivar.setBackground(redondo(activo ? ROJO : VERDE_ENCENDER, activo ? ROJO : VERDE_ENCENDER, 32));
        textoPower.setText(activo ? "Apagar" : "Encender");
        botonActivar.setContentDescription(activo ? "Apagar los avisos" : "Encender los avisos");
        // Píldora de estado
        estadoCabecera.setText(activo ? "●  Activo" : "Inactivo");
        estadoCabecera.setTextColor(activo ? VERDE : SUB);
        estadoCabecera.setBackground(redondo(TARJETA, BORDE, 19));
        // Titular grande según la pestaña
        String[] t = titular(velocimetro.modo);
        etiquetaHero.setText(t[0]);
        titularHero.setText(t[1]);
        titularHero.setTextColor(VelocimetroView.colorEstado() == ROJO ? ROJO : TEXTO);
        int lim = RadarService.limiteMostrado();
        senalLimite.setVisibility(activo && lim > 0 ? View.VISIBLE : View.INVISIBLE);
        senalLimite.setText(String.valueOf(lim));
        senalLimite.setTextSize(lim >= 100 ? 12 : 15);


}

    /** Lee los radares guardados y pone la tarjeta normal o naranja. */
    private void refrescarRadares() {
        if (!descargando) {
            int n = RadarDB.contarOficiales(this);
            int propios = RadarDB.propios(this).size();
            int dias = RadarDB.diasSinActualizar(this);
            boolean toca = n == 0 || dias < 0 || dias >= DIAS_ACTUALIZAR;
            String extra = propios > 0 ? " · " + propios + " tuyos" : "";
            if (n == 0) {
                radaresTitulo.setText("Descarga los radares");
                radaresSub.setText("Todavía no hay radares en el móvil" + extra);
            } else if (toca) {
                radaresTitulo.setText("Actualiza los radares");
                radaresSub.setText(n + " radares · última vez hace " + dias + " días" + extra);
            } else {
                radaresTitulo.setText(n + " radares");
                radaresSub.setText("Actualizados " + cuando(dias) + extra);
            }
            ponerAvisoRadares(toca);
            // La tarjeta solo aparece cuando hay que actualizar (si no, está en Ajustes)
            tarjetaRadares.setVisibility(toca ? View.VISIBLE : View.GONE);
        }
    }

    private void ponerAvisoRadares(boolean toca) {
        if (toca) {
            tarjetaRadares.setBackground(redondo(AMBAR_FONDO, AMBAR, 16));
            radaresTitulo.setTextColor(AMBAR);
            radaresAccion.setTextColor(AMBAR);
            if (latidoRadares == null) {
                latidoRadares = ObjectAnimator.ofFloat(tarjetaRadares, "alpha", 1f, 0.65f);
                latidoRadares.setDuration(900);
                latidoRadares.setRepeatMode(ValueAnimator.REVERSE);
                latidoRadares.setRepeatCount(ValueAnimator.INFINITE);
                latidoRadares.setStartDelay(900);
                latidoRadares.start();
            }
        } else {
            tarjetaRadares.setBackground(redondo(TARJETA, BORDE, 16));
            radaresTitulo.setTextColor(TEXTO);
            radaresAccion.setTextColor(VERDE);
            if (latidoRadares != null) {
                latidoRadares.cancel();
                latidoRadares = null;
                tarjetaRadares.setAlpha(1f);
            }
        }
    }

    /** Vibración corta para confirmar que se ha pulsado el botón sin mirar la pantalla. */
    private void vibrarCorto() {
        try {
            android.os.Vibrator v = getSystemService(android.os.Vibrator.class);
            if (v != null) v.vibrate(android.os.VibrationEffect.createOneShot(60, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) {
        }
    }

    /** Texto pequeño de arriba y titular grande de la tarjeta principal, según la pestaña. */
    private String[] titular(int modo) {
        if (!RadarService.activo) return new String[]{"Avisos parados", "Pulsa para\nempezar"};
        if (!RadarService.hayGps) return new String[]{"Esperando", "Buscando\nGPS…"};
        switch (modo) {
            case VelocimetroView.MODO_TRAMOS:
                if (!RadarService.enTramo) return new String[]{"Radar de tramo", "Fuera de\ntramo"};
                int m = RadarService.mediaTramo;
                return new String[]{"Radar de tramo · tu media", (m > 0 ? m : "--") + " km/h\nde media"};
            case VelocimetroView.MODO_TRAFICO:
                String inc = RadarService.incidenciaTexto;
                return new String[]{"Tráfico (DGT)", inc != null ? partir(inc) : "Sin\nincidencias"};
            case VelocimetroView.MODO_PEAJES:
                String p = RadarService.peajeTexto;
                return new String[]{"Peajes", p != null ? partir(p) : "Sin peajes\ncerca"};
            default:
                int d = RadarService.distancia;
                if (d < 0) return new String[]{"Radares", "Vía\nlibre"};
                boolean exceso = VelocimetroView.colorEstado() == ROJO;
                return new String[]{exceso ? "¡Reduce la velocidad!" : "Próximo radar",
                        "Radar a\n" + (Math.round(d / 10f) * 10) + " m"};
        }
    }

    /** Parte un texto largo en dos líneas por la palabra « a ». */
    private static String partir(String s) {
        int i = s.indexOf(" a ");
        return i > 0 ? s.substring(0, i) + "\na " + s.substring(i + 3) : s;
    }

    private LinearLayout tituloSeccion(String titulo, String accion, View.OnClickListener l) {
        LinearLayout f = new LinearLayout(this);
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setPadding(dp(4), dp(26), dp(4), dp(12));
        TextView t = texto(titulo, 17, TEXTO, true);
        f.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (accion != null) {
            TextView a = texto("+  " + accion, 13, TEXTO, true);
            a.setGravity(Gravity.CENTER);
            a.setPadding(dp(14), 0, dp(14), 0);
            a.setBackground(redondo(TARJETA, BORDE, 18));
            pulsable(a, l);
            f.addView(a, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));
        }
        return f;
    }

    /** Cuadro redondeado con un icono turquesa centrado (para las filas de lista). */
    private android.widget.FrameLayout cuadroIcono(int recurso) {
        android.widget.FrameLayout f = botonIcono(recurso, VERDE, 24, redondo(HUNDIDO, BORDE, 16));
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(52), dp(52)));
        return f;
    }

    /** Igual que cuadroIcono, pero con el icono de la app instalada si existe. */
    private android.widget.FrameLayout cuadroApp(String paquete, int reserva) {
        android.widget.FrameLayout f = cuadroIcono(reserva);
        try {
            android.graphics.drawable.Drawable ic = getPackageManager().getApplicationIcon(paquete);
            ImageView i = (ImageView) f.getChildAt(0);
            i.setImageDrawable(ic);
            i.clearColorFilter();
            i.setLayoutParams(new android.widget.FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return f;
    }

    /** Botón redondo de la barra de música. */
    private android.widget.FrameLayout botonCircular(int recurso, boolean principal, View.OnClickListener l) {
        android.widget.FrameLayout f = botonIcono(recurso, principal ? 0xFF061012 : TEXTO, principal ? 22 : 18,
                principal ? degradado(26) : relieveOval());
        pulsable(f, l);
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(principal ? 52 : 44), dp(principal ? 52 : 44)));
        return f;
    }

    /** Botón de la barra inferior; el seleccionado lleva un fondo más claro. */
    private android.widget.FrameLayout navBoton(int recurso, boolean seleccionado, View.OnClickListener l) {
        android.widget.FrameLayout f = botonIcono(recurso, seleccionado ? TEXTO : SUB, 22,
                seleccionado ? redondo(0xFF2A3133, 0xFF2A3133, 26) : null);
        pulsable(f, l);
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(52)));
        return f;
    }

    /** Acción con icono en un círculo y su nombre debajo. */
    private LinearLayout accion(int recurso, String nombre, View.OnClickListener l) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        android.widget.FrameLayout b = botonIcono(recurso, TEXTO, 20, relieveOval());
        c.addView(b, new LinearLayout.LayoutParams(dp(50), dp(50)));
        TextView t = texto(nombre, 11, SUB, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(6), 0, 0);
        c.addView(t);
        pulsable(c, l);
        return c;
    }

    /** Como accion(), pero usando el icono de la app instalada (WhatsApp) si existe. */
    private LinearLayout accionApp(String paquete, int reserva, String nombre, View.OnClickListener l) {
        LinearLayout c = accion(reserva, nombre, l);
        try {
            android.graphics.drawable.Drawable ic = getPackageManager().getApplicationIcon(paquete);
            android.widget.FrameLayout f = (android.widget.FrameLayout) c.getChildAt(0);
            ImageView i = (ImageView) f.getChildAt(0);
            i.setImageDrawable(ic);
            i.clearColorFilter();
            i.setLayoutParams(new android.widget.FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return c;
    }

    /** Baldosa cuadrada con icono, título y una fila inferior (estado + interruptor). */
    private LinearLayout baldosa(int icono, String titulo) {
        LinearLayout t = tarjeta();
        t.setPadding(dp(18), dp(16), dp(14), dp(10));
        ImageView ic = new ImageView(this);
        ic.setImageResource(icono);
        LinearLayout.LayoutParams lpIc = new LinearLayout.LayoutParams(dp(28), dp(28));
        t.addView(ic, lpIc);
        TextView tt = texto(titulo, 16, TEXTO, true);
        tt.setPadding(0, dp(8), 0, dp(2));
        t.addView(tt);
        LinearLayout abajo = new LinearLayout(this);
        abajo.setGravity(Gravity.CENTER_VERTICAL);
        TextView estado = texto("", 12, SUB, false);
        abajo.addView(estado, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        t.addView(abajo);
        return t;
    }

    /** Botón redondo con relieve y, opcionalmente, una etiqueta pequeña debajo. */
    private LinearLayout botonAccion(String simbolo, String etiqueta, View.OnClickListener l) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView b = botonRedondo(simbolo, 18);
        pulsable(b, l);
        c.addView(b, new LinearLayout.LayoutParams(dp(52), dp(52)));
        if (etiqueta != null) {
            TextView e = texto(etiqueta, 11, SUB, false);
            e.setPadding(0, dp(4), 0, 0);
            c.addView(e);
        }
        return c;
    }

    private LinearLayout.LayoutParams pesoAccion() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    static String cuando(int dias) {
        if (dias <= 0) return "hoy";
        if (dias == 1) return "ayer";
        return "hace " + dias + " días";
    }

    // ---------- Activar / parar ----------

    private void alternar() {
        if (RadarService.activo) {
            stopService(new Intent(this, RadarService.class));
            handler.postDelayed(this::refrescar, 300);
            return;
        }
        List<String> faltan = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            faltan.add(Manifest.permission.ACCESS_FINE_LOCATION);
            faltan.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            faltan.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (faltan.isEmpty()) {
            iniciar();
        } else {
            requestPermissions(faltan.toArray(new String[0]), 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permisos, int[] resultados) {
        super.onRequestPermissionsResult(code, permisos, resultados);
        if (code == 4) {
            guardarAparcamientoManual();
            return;
        }
        if (code == 5) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) hacerFoto();
            return;
        }
        if (code != 1) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            iniciar();
        } else {
            toast("Sin permiso de ubicación precisa la app no puede avisar");
        }
    }

    private void iniciar() {
        if (RadarDB.contarOficiales(this) == 0 && RadarDB.propios(this).isEmpty()) {
            toast("Primero descarga los radares");
            return;
        }
        startForegroundService(new Intent(this, RadarService.class));
        handler.postDelayed(this::refrescar, 500);
    }

    // ---------- Radares ----------

    private void actualizar() {
        if (descargando) return;
        descargando = true;
        radaresTitulo.setText("Descargando radares…");
        radaresSub.setText("Puede tardar un minuto");
        radaresAccion.setText("");
        new Thread(() -> {
            try {
                int n = RadarDB.descargar(this);
                try {
                    TramosDB.descargar(this);
                } catch (Exception ignored) {
                    // Si fallan los tramos, se siguen usando los que ya había
                }
                runOnUiThread(() -> {
                    descargando = false;
                    radaresAccion.setText("Actualizar");
                    toast(n + " radares descargados");
                    reiniciarSiActivo();
                    refrescarRadares();
                    refrescar();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    descargando = false;
                    radaresAccion.setText("Actualizar");
                    toast("No se pudo descargar. Revisa la conexión e inténtalo de nuevo.");
                    refrescarRadares();
                });
            }
        }).start();
    }

    private void pedirSinOptimizar() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    // ---------- Piezas de la interfaz ----------

    // ---------- Dónde he aparcado ----------

    private void refrescarAparcamiento() {
        refrescarFoto();
        if (!Ajustes.hayAparcamiento(this)) {
            aparcTexto.setText("Aún no hay ninguno guardado");
            return;
        }
        double lat = Ajustes.aparcLat(this), lon = Ajustes.aparcLon(this);
        long min = (System.currentTimeMillis() - Ajustes.aparcHora(this)) / 60000;
        String hace = min < 1 ? "ahora mismo" : min < 60 ? "hace " + min + " min"
                : min < 48 * 60 ? "hace " + (min / 60) + " h" : "hace " + (min / 1440) + " días";
        String actual = aparcTexto.getText().toString();
        int punto = actual.indexOf(" · ");
        String direccion = (lat == aparcLatMostrada && punto > 0) ? actual.substring(punto) : "";
        aparcTexto.setText("Guardado " + hace + direccion);
        if (lat != aparcLatMostrada) {
            aparcLatMostrada = lat;
            new Thread(() -> {
                String calle = direccion(lat, lon);
                if (calle != null) runOnUiThread(() -> aparcTexto.setText("Guardado " + hace + " · " + calle));
            }).start();
        }
    }

    private String direccion(double lat, double lon) {
        try {
            android.location.Geocoder g = new android.location.Geocoder(this, new java.util.Locale("es", "ES"));
            List<android.location.Address> lista = g.getFromLocation(lat, lon, 1);
            if (lista != null && !lista.isEmpty()) {
                android.location.Address a = lista.get(0);
                String calle = a.getThoroughfare();
                if (calle == null) return a.getLocality();
                return calle + (a.getSubThoroughfare() != null ? " " + a.getSubThoroughfare() : "");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void guardarAparcamientoManual() {
        Location l = RadarService.ultima;
        if (l != null && System.currentTimeMillis() - l.getTime() < 60000) {
            guardarEn(l);
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 4);
            return;
        }
        toast("Buscando tu posición…");
        android.location.LocationManager lm =
                (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(android.location.LocationManager.GPS_PROVIDER, null,
                        getMainExecutor(), loc -> {
                            if (loc != null) guardarEn(loc);
                            else toast("No hay señal GPS. Prueba al aire libre.");
                        });
            } else {
                lm.requestSingleUpdate(android.location.LocationManager.GPS_PROVIDER,
                        this::guardarEn, Looper.getMainLooper());
            }
        } catch (Exception e) {
            toast("No se pudo obtener la posición");
        }
    }

    private void guardarEn(Location l) {
        Ajustes.guardarAparcamiento(this, l.getLatitude(), l.getLongitude());
        aparcLatMostrada = Double.NaN;
        toast("Aparcamiento guardado");
        refrescarAparcamiento();
    }

    private String enlaceAparcamiento() {
        return String.format(java.util.Locale.US, "https://maps.google.com/?q=%.6f,%.6f",
                Ajustes.aparcLat(this), Ajustes.aparcLon(this));
    }

    private void comoLlegar() {
        if (!Ajustes.hayAparcamiento(this)) {
            toast("Primero guarda dónde has aparcado");
            return;
        }
        String url = String.format(java.util.Locale.US,
                "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f&travelmode=walking",
                Ajustes.aparcLat(this), Ajustes.aparcLon(this));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("No hay ninguna app de mapas");
        }
    }

    private void enviarWhatsApp() {
        if (!Ajustes.hayAparcamiento(this)) {
            toast("Primero guarda dónde has aparcado");
            return;
        }
        Uri foto = fotoUri();
        Intent i = new Intent(Intent.ACTION_SEND);
        i.putExtra(Intent.EXTRA_TEXT, "Mi coche está aparcado aquí: " + enlaceAparcamiento());
        if (foto != null) {
            i.setType("image/jpeg");
            i.putExtra(Intent.EXTRA_STREAM, foto);
            i.setClipData(android.content.ClipData.newRawUri("Aparcamiento", foto));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            i.setType("text/plain");
        }
        for (String app : new String[]{"com.whatsapp", "com.whatsapp.w4b"}) {
            try {
                i.setPackage(app);
                startActivity(i);
                return;
            } catch (Exception ignored) {
            }
        }
        i.setPackage(null);
        startActivity(Intent.createChooser(i, "Enviar ubicación"));
    }

    // ---------- Foto del aparcamiento ----------
    // La foto se guarda en la galería (álbum «AvisaRadar»), para que la cámara pueda
    // escribirla y WhatsApp leerla sin problemas en cualquier móvil.

    private Uri fotoPendiente;

    /** La foto del aparcamiento actual, o null si no hay (o se ha borrado de la galería). */
    private Uri fotoUri() {
        String s = Ajustes.aparcFoto(this);
        if (s == null) return null;
        Uri u = Uri.parse(s);
        try (java.io.InputStream in = getContentResolver().openInputStream(u)) {
            return in != null && in.read() != -1 ? u : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void hacerFoto() {
        if (!Ajustes.hayAparcamiento(this)) {
            toast("Primero guarda dónde has aparcado");
            return;
        }
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 5);
            return;
        }
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                "aparcamiento_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                        .format(new java.util.Date()) + ".jpg");
        v.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= 29) {
            v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AlLoro");
        }
        try {
            fotoPendiente = getContentResolver().insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        } catch (Exception e) {
            fotoPendiente = null;
        }
        if (fotoPendiente == null) {
            toast("No se pudo preparar la foto");
            return;
        }
        Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, fotoPendiente);
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(i, PEDIR_FOTO);
        } catch (Exception e) {
            borrarPendiente();
            toast("No se encontró la cámara");
        }
    }

    private void borrarPendiente() {
        if (fotoPendiente != null) {
            try {
                getContentResolver().delete(fotoPendiente, null, null);
            } catch (Exception ignored) {
            }
            fotoPendiente = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle estado) {
        super.onSaveInstanceState(estado);
        if (fotoPendiente != null) estado.putString("foto_pendiente", fotoPendiente.toString());
    }

    @Override
    protected void onRestoreInstanceState(Bundle estado) {
        super.onRestoreInstanceState(estado);
        String s = estado.getString("foto_pendiente");
        if (s != null) fotoPendiente = Uri.parse(s);
    }

    @Override
    protected void onActivityResult(int code, int resultado, Intent datos) {
        super.onActivityResult(code, resultado, datos);
        if (code != PEDIR_FOTO) return;
        if (resultado == RESULT_OK && fotoPendiente != null) {
            Ajustes.setAparcFoto(this, fotoPendiente.toString());
            fotoPendiente = null;
            toast("Foto guardada con el aparcamiento");
        } else {
            borrarPendiente(); // se canceló: no dejar una foto vacía en la galería
        }
        fotoMostrada = null;
        refrescarFoto();
    }

    /** Muestra la miniatura si hay foto (solo vuelve a leerla si ha cambiado). */
    private void refrescarFoto() {
        String actual = Ajustes.aparcFoto(this);
        if (actual == null) actual = "";
        if (actual.equals(fotoMostrada)) return;
        fotoMostrada = actual;
        Uri u = fotoUri();
        android.graphics.Bitmap b = null;
        if (u != null) {
            try (java.io.InputStream in = getContentResolver().openInputStream(u)) {
                android.graphics.BitmapFactory.Options op = new android.graphics.BitmapFactory.Options();
                op.inSampleSize = 4; // miniatura ligera
                b = android.graphics.BitmapFactory.decodeStream(in, null, op);
            } catch (Exception ignored) {
            }
        }
        if (b == null) {
            fotoAparc.setImageDrawable(null);
            fotoAparc.setVisibility(View.GONE);
            return;
        }
        fotoAparc.setImageBitmap(difuminar(rotarSegunExif(u, b)));
        fotoAparc.setVisibility(View.VISIBLE);
    }

    /** La miniatura se ve difuminada; al tocarla se abre la foto nítida. */
    private android.graphics.Bitmap difuminar(android.graphics.Bitmap b) {
        if (Build.VERSION.SDK_INT >= 31) {
            fotoAparc.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                    dp(18), dp(18), android.graphics.Shader.TileMode.CLAMP));
            return b;
        }
        // En Android antiguos: se reduce mucho y se vuelve a ampliar, que también difumina
        int w = Math.max(1, b.getWidth() / 24), h = Math.max(1, b.getHeight() / 24);
        android.graphics.Bitmap peq = android.graphics.Bitmap.createScaledBitmap(b, w, h, true);
        return android.graphics.Bitmap.createScaledBitmap(peq, b.getWidth(), b.getHeight(), true);
    }

    private android.graphics.Bitmap rotarSegunExif(Uri u, android.graphics.Bitmap b) {
        try (java.io.InputStream in = getContentResolver().openInputStream(u)) {
            int o = new android.media.ExifInterface(in).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL);
            int grados = o == android.media.ExifInterface.ORIENTATION_ROTATE_90 ? 90
                    : o == android.media.ExifInterface.ORIENTATION_ROTATE_180 ? 180
                    : o == android.media.ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            if (grados == 0) return b;
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.postRotate(grados);
            return android.graphics.Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        } catch (Exception e) {
            return b;
        }
    }

    private void verFoto() {
        Uri u = fotoUri();
        if (u == null) return;
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(u, "image/jpeg");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(i);
        } catch (Exception e) {
            toast("No hay ninguna app para ver fotos");
        }
    }

    // ---------- Distancias de aviso ----------

}
