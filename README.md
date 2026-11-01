# AvisaRadar

App Android que avisa por voz de los radares fijos mientras conduces. Funciona en segundo plano, por ejemplo con Google Maps o Waze delante y con la pantalla apagada.

## Qué hace

- Avisa siempre a 500 m y a 100 m de cada radar, con pitido, voz y notificación («Radar a 500 metros, límite 80»).
- Si vas por encima del límite, avisa también a 200 y 50 m, y añade «Reduce la velocidad».
- Solo avisa de los radares que tienes **delante**, y si el radar tiene sentido conocido, solo si coincide con el tuyo.
- Puedes guardar tus propios avisos con «Añadir aviso aquí».
- Los radares se guardan en el móvil, así que los avisos funcionan sin conexión.

Datos de radares: © colaboradores de [OpenStreetMap](https://www.openstreetmap.org/copyright) (licencia ODbL).

## Cómo conseguir la APK con GitHub

1. Sube esta carpeta a un repositorio de GitHub.
2. GitHub compila la app sola (pestaña **Actions**, tarda unos 5 minutos).
3. Ve a **Releases → AvisaRadar (última versión)** y descarga `AvisaRadar.apk` desde el móvil.
4. Ábrela e instálala. Android te pedirá permitir «instalar apps de origen desconocido» para tu navegador o gestor de archivos.

Cada vez que cambies algo y lo subas, GitHub crea una APK nueva que se instala encima de la anterior.

## Primer uso

1. Pulsa **Descargar / actualizar radares** (si la APK ya los trae, puedes saltarte este paso).
2. Pulsa **Que no se cierre (batería)** y acepta.
3. Pulsa **Activar avisos** y acepta ubicación precisa y notificaciones.
4. Abre tu navegador habitual. Para parar, usa **Parar** en la notificación.

## Aviso

Es una ayuda; la responsabilidad es de quien conduce. Los datos de OpenStreetMap pueden estar incompletos. No incluye radares móviles.
