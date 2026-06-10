# Política de privacidad — IPTV JDH

Última actualización: **10 de junio de 2026**

**IPTV JDH** es un reproductor multimedia personal para Android. La aplicación **no recoge, transmite ni vende ningún dato personal**. Toda la información que la app gestiona se guarda exclusivamente en el almacenamiento local del dispositivo del usuario.

## 1. Quién es el responsable

Jorge Díaz Hernández (en adelante «el desarrollador»), particular, contactable en **contacto@jorgedihe.net**.

## 2. Datos personales que se recogen

**Ninguno se envía al desarrollador ni a terceros.**

La aplicación almacena *localmente en tu dispositivo* los siguientes datos, exclusivamente para su funcionamiento:

- **URL del servidor IPTV y credenciales** (usuario y contraseña) que tú introduces voluntariamente para conectar con tu proveedor.
- **Lista de canales y catálogos** descargados desde tu proveedor IPTV.
- **Programación EPG (XMLTV)** descargada desde tu proveedor IPTV.
- **Canales favoritos, historial de reproducción y preferencias** de visualización.

Estos datos residen únicamente en el directorio privado de la app dentro de tu Android (`/data/data/net.jorgedihe.iptv/`) y se eliminan al desinstalar la aplicación.

## 3. Permisos solicitados por la app y para qué

- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`: para conectarte con tu servidor IPTV y descargar los datos del catálogo.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: para reproducir vídeo en segundo plano y sincronizar listas largas sin interrupciones.
- `POST_NOTIFICATIONS`: para mostrar el progreso de la descarga de canales y EPG.
- `WAKE_LOCK`: para evitar que el dispositivo se duerma durante la reproducción.
- `CHANGE_WIFI_MULTICAST_STATE`: para soporte de DLNA / Cast cuando el usuario lo activa.

## 4. Cookies y tecnologías de seguimiento

La aplicación **no utiliza cookies, ni SDKs de analítica, ni herramientas de seguimiento de terceros** (sin Google Analytics, sin Firebase, sin Crashlytics, sin AdMob, sin Facebook SDK).

## 5. Compartición de datos con terceros

El desarrollador **no comparte ningún dato** con terceros, porque no recoge ninguno. La app se comunica únicamente con:

- El servidor IPTV que tú configures voluntariamente.
- Las URL de EPG (XMLTV) asociadas a ese servidor.

Esa comunicación se establece directamente entre tu dispositivo y el servidor — el desarrollador no la intercepta ni la registra.

## 6. Contenido de terceros

IPTV JDH es un reproductor genérico. El contenido que se reproduce **lo proporciona exclusivamente el servidor que tú configures**. El desarrollador no es responsable de la legalidad, disponibilidad ni calidad de dicho contenido.

## 7. Menores

La aplicación no está dirigida a menores de 13 años. Como no recoge ningún dato, no procesa información de menores.

## 8. Seguridad

Tus credenciales IPTV se almacenan en el almacenamiento privado de la app (no accesible por otras aplicaciones del dispositivo). Aun así, te recomendamos no compartir tu dispositivo desbloqueado con terceros y usar bloqueo de pantalla.

## 9. Derechos del usuario

Como la aplicación no recoge ni envía datos, no existen registros tuyos en servidores del desarrollador. Para eliminar todos los datos locales basta con desinstalar la aplicación o borrar sus datos desde **Ajustes de Android**.

## 10. Código fuente

IPTV JDH es software libre publicado bajo licencia **GPL-3.0**. Puedes auditar el código completo en [github.com/jorgedihe/iptv-jdh](https://github.com/jorgedihe/iptv-jdh).

## 11. Cambios en esta política

Si la política se actualizara, el cambio quedará reflejado en esta misma URL con la fecha de «Última actualización» arriba. Cambios significativos se anunciarán en la propia aplicación.

## 12. Contacto

Para cualquier consulta relativa a esta política: **contacto@jorgedihe.net**

---

© 2026 Jorge Díaz Hernández · GPL-3.0
