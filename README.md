# TV Limpia

App Android para limpiar y acelerar una TV con Android TV / Google TV (pensada para TCL) desde el móvil, por la Wi‑Fi, sin PC ni root.

## Qué hace
1. **Buscar mi TV** – escanea la Wi‑Fi y encuentra la TV con la depuración por red activada.
2. **Conectar** – la TV pide permiso una sola vez («Permitir siempre»).
3. **Limpiar** – desactiva las apps marcadas (`pm disable-user`). No borra nada: todo se puede reactivar.

El botón principal **Limpiar y optimizar** además:
- acelera las animaciones (0,5x),
- quita el segundo plano a las apps no esenciales,
- congela apps en caché y apaga los escaneos Wi‑Fi/Bluetooth continuos,
- cierra procesos de fondo y vacía cachés,
- compila las apps para que abran más rápido,
- y muestra la RAM libre antes y después.

**Tus APK nunca se tocan**: todo lo que instalaste tú aparece en «Tus apps» y queda fuera de la limpieza y de la optimización. También puedes marcar cualquier otra app con ★ Mantener.

Todo es reversible: «Deshacer» (optimización) y «Restaurar todo» (apps desactivadas).

## Seguridad
- Las apps del sistema importantes (inicio, ajustes, HDMI, mando, actualizaciones, servicios de Google, chip de la TV…) están **protegidas** y no aparecen como opción.
- Solo se marcan solas las de la lista segura. El resto hay que marcarlo a mano.
- «Restaurar todo» reactiva todo lo que haya desactivado la app.

## Preparar la TV (solo una vez)
1. Ajustes → Sistema → Acerca de → pulsar 7 veces en «Compilación del SO de Android TV».
2. Ajustes → Sistema → Opciones de desarrollador → activar «Depuración USB» y «Depuración por red».
3. Móvil y TV en la misma Wi‑Fi.

## Compilar
Al subir el proyecto a GitHub, la pestaña **Actions** compila la APK sola. Se descarga en el artefacto `TVLimpia-apk`.
