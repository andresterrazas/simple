# simple

Launcher ligero para Fire TV (probado en Fire TV Stick 4K Plus · Fire OS 8). Hecho con Kotlin y Jetpack Compose.

- Fondo desenfocado con paneles de "vidrio" que no cuestan rendimiento (el desenfoque se calcula una sola vez).
- Fila de favoritos que se expande a la cuadrícula completa al bajar.
- Mantén OK (o el botón ☰) sobre una app: mover, favoritos, ocultar, cambiar ícono, información, tienda y desinstalar.
- Detecta apps de TV y apps instaladas por fuera de la tienda; genera banners para las que no tienen.
- Accesos directos a los ajustes nativos del Fire TV.
- Fondos propios: súbelos desde el celular escaneando un código QR.

## Instalar

En el Fire TV abre **Downloader** y escribe:

```
https://github.com/andresterrazas/simple/releases/latest/download/simple.apk
```

Cada cambio en `main` compila una nueva versión; para actualizar, repite lo mismo.

## Hacerlo el inicio predeterminado

Fire OS no permite elegir otro launcher, así que simple incluye un servicio de accesibilidad que lo abre al presionar Inicio. Se activa una sola vez:

1. Activa **Mi Fire TV › Opciones para desarrolladores › Depuración ADB**.
2. Desde una computadora (o una app de ADB en el celular), conectado a la IP del Fire TV:

```
adb shell settings put secure enabled_accessibility_services com.andresterrazas.simple/com.andresterrazas.simple.home.HomeService
adb shell settings put secure accessibility_enabled 1
adb shell pm grant com.andresterrazas.simple android.permission.WRITE_SECURE_SETTINGS
```

El último comando permite que simple se reactive solo si una actualización de Fire OS lo desactiva. El launcher de Amazon sigue instalado por detrás, así que la suspensión, la Appstore y los ajustes siguen funcionando.

Si al presionar Inicio no se abre simple, cambia **Personalizar › Configurar simple como inicio predeterminado › Detección** a *Amplio* y revisa **Diagnóstico**.

## Compilar

```
./gradlew assembleRelease
```

Las fuentes Quicksand se distribuyen bajo la SIL Open Font License (ver `FONT-LICENSE-OFL.txt`).
