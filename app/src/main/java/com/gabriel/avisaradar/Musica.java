package com.gabriel.avisaradar;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.Toast;

/**
 * Controla la música que esté sonando (Spotify u otra app) con las mismas
 * teclas que usan los auriculares: anterior, play/pausa y siguiente.
 */
public class Musica {

    static final String SPOTIFY = "com.spotify.music";

    public static void anterior(Context c) {
        tecla(c, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    public static void playPausa(Context c) {
        tecla(c, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    public static void siguiente(Context c) {
        tecla(c, KeyEvent.KEYCODE_MEDIA_NEXT);
    }

    static void tecla(Context c, int codigo) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        long t = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, codigo, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, codigo, 0));
    }

    public static void abrirSpotify(Context c) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(SPOTIFY);
        if (i == null) {
            Toast.makeText(c, "Spotify no está instalado", Toast.LENGTH_SHORT).show();
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }
}
