package com.gabriel.avisaradar;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Servicio de accesibilidad limitado a Waze y Google Maps (ver res/xml/arranque_navegador.xml):
 * solo se entera de que una de esas dos apps se ha abierto, y entonces enciende los avisos.
 * No lee nada de lo que hay en la pantalla.
 */
public class ArranqueNavegador extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (!Ajustes.arranqueNavegador(this) || RadarService.activo) return;
        CharSequence app = e.getPackageName();
        if (app == null) return;
        String p = app.toString();
        if (p.equals("com.waze") || p.equals("com.google.android.apps.maps")) {
            try {
                startForegroundService(new Intent(this, RadarService.class));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onInterrupt() {
    }
}
