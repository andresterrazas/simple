# Historial de cambios

Cada versión publicada en [Releases](https://github.com/andresterrazas/simple/releases) incluye su sección "Novedades". Este archivo resume el historial completo.

## v1.1.2
- Continuar viendo a todo lo ancho con desvanecido en las orillas en lugar de corte recto; la tarjeta enfocada se mantiene dentro de los márgenes del dock.

## v1.1.1
- Corrige los menús desplegables (se cerraban al abrirse).
- Continuar viendo: selector de estilo (Panorámica, Clásica optimizada, Imagen) y de tamaño, con vista previa.

## v1.1.0
- Tarjetas de Continuar viendo rediseñadas: imagen a la derecha, título a todo lo ancho, progreso siempre visible en la base y línea corta "T5 E4 · faltan 23 min".
- Menús desplegables para los ajustes con varias opciones.
- Aplicaciones: visibles arriba y ocultas abajo, con el foco en su lugar al cambiar una.
- Íconos nuevos y sin repetir; engrane real para los ajustes del sistema.
- Textos de "Fire TV" solo en Fire TV; en otras TV dicen "la TV".
- Desplazamiento de los menús corregido (no se traba al final y regresa completo arriba); vidrio estable en filas a medio salir.
- Numeración 1.1.x (`version.properties`).

## v1.0.21
- Clima en el encabezado (compacto o con pronóstico), ubicación automática o por ciudad.
- Cambio automático de fondo entre las fotos subidas.
- Configuración reorganizada: Controles del encabezado agrupa fecha, hora, clima y botones; el orden de apps vive en Aplicaciones.

## v1.0.20
- Perfil de arranque, lista de apps en caché y fondo inmediato al abrir.
- Inicio dentro de simple siempre regresa al dock.
- Menús con formas y márgenes consistentes.
- Diagnóstico de fluidez y registro local de cierres inesperados.
- Código de la interfaz dividido por pantalla.

## v1.0.19
- Compilación intermedia de la v1.0.20 (sin notas propias).

## v1.0.18
- Continuar viendo más compacto, sin recorte en la tarjeta enfocada y con transiciones suaves al aparecer, ocultarse y cambiar de app.
- Rendimiento en Fire TV: menos recomposición al mover el foco y la cuadrícula oculta ya no se dibuja.

## v1.0.17
- Continuar viendo funcional al posarse en apps de streaming (Watch Next del sistema + sugerencias por notificación), tarjetas rediseñadas, exclusión por app (YouTube por defecto) y diagnóstico de contenido detectado.

## v1.0.11 – v1.0.16
- Dock adaptable al ancho de la TV con margen seguro contra sobrebarrido; la siguiente fila asoma bajo el dock.
- Compatibilidad con Android TV / Google TV: elegir simple como inicio predeterminado del sistema (o por ADB); el servicio de respaldo reconoce cualquier launcher del sistema.
- La vista normal del dock ya no se desplaza al regresar de la cuadrícula.
- Primeras versiones de Continuar viendo (notificaciones y Watch Next).

## v1.0.9 – v1.0.10
- Fluidez: perfiles de compilación de Compose, tarjetas de apps en caché de disco y generadas de 2 en 2.
- Al salir de Ajustes, Wi‑Fi o Tienda (con Inicio o Atrás) se regresa a simple.
- Menús más compactos estilo ChillHub y opción "Tamaño de los menús".
- Se retiró el botón Inicio directo experimental.

## v1.0.5 – v1.0.8
- Pantalla de configuración del dock (opacidad, ancho, forma, nombres, zoom, contorno) con vista previa.
- Íconos en alta resolución cuando la app los incluye; imagen propia por app subida desde el celular.
- Contorno de enfoque que cambia a gris en apps claras.
- Menú de pulsación larga con vidrio teñido del color de la app.
- Fondo procesado guardado en disco para aparecer al instante.

## v1.0.3 – v1.0.4
- Primera versión firmada: inicio con dock y cuadrícula, menú de app, ajustes, fondos con desenfoque y subida desde el celular, servicio para el botón Inicio en Fire TV.
- Inicio con zonas fijas, dock siempre visible y foco delimitado.
