package com.gabriel.avisaradar;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Se despierta cuando el móvil se conecta o desconecta de un Bluetooth.
 * Si es el del coche: al conectar enciende los avisos; al desconectar los apaga
 * (y al apagarse se guarda el aparcamiento y se dice el resumen del viaje).
 */
public class ArranqueBluetooth extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent i) {
        String coche = Ajustes.bluetoothCoche(c);
        if (coche.isEmpty()) return;
        BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (d == null || !coche.equalsIgnoreCase(d.getAddress())) return;
        try {
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(i.getAction())) {
                if (!RadarService.activo) c.startForegroundService(new Intent(c, RadarService.class));
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(i.getAction())) {
                if (RadarService.activo) {
                    c.startService(new Intent(c, RadarService.class).setAction(RadarService.ACCION_PARAR));
                }
            }
        } catch (Exception ignored) {
            // Android puede impedir arrancar desde segundo plano si falta algún permiso
        }
    }
}
