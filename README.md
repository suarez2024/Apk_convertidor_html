# HTML a APK — Convertidora offline

App Android que convierte proyectos HTML multipágina en APKs instalables **sin internet**,
al estilo "Creador de APK desde HTML".

## Cómo funciona
1. La convertidora lleva dentro 5 plantillas (`stub1..stub5.apk`, mismo WebView con distinto package).
2. Al pulsar **Generar**: copia un stub → reemplaza `assets/www/` con tu proyecto →
   reemplaza icono → re-firma (v1+v2 con keystore debug auto-generado) → lanza el instalador.
3. El APK queda en `Download/Html2Apk/<nombre>.apk`.

## Uso en el móvil
1. Instala la convertidora (descarga el artefacto `HTML-a-APK-convertidora` del CI).
2. Concede `Instalar apps desconocidas` cuando lo pida.
3. Escribe el nombre, pega tu `index.html` o **Importa un ZIP** con `index.html` + css/js/img.
4. (Opcional) Elige icono PNG. Pulsa **Vista previa** para probar. Pulsa **Generar**.

## Compilar (nube, recomendado)
1. Sube esta carpeta a un repo GitHub.
2. Ve a **Actions → Build converter APK → Run workflow**.
3. Descarga el artefacto e instálalo en tu móvil.

## Compilar en local
Necesitas Android Studio + JDK 17. El wrapper no va incluido (lo genera Studio
al abrir el proyecto) o genéralo con `gradle wrapper`:
```bash
gradle :stub:assembleRelease
# copiar los 5 apk a app/src/main/assets/stubs/stub1..5.apk
gradle :app:assembleDebug
```

## Límites del MVP
- Personalización básica: nombre + icono + HTML (sin splash, package manual ni permisos extra).
- El nombre visible se parchea in-place en `resources.arsc`: si es más largo que
  "Mi App HTML" (11 chars) puede conservar el label de plantilla (la app instala igual).
  v2: editor ARSC completo o build con servidor.
- Solo 5 slots de package distintos (stub1..5): la 6ª app generada reutiliza slot.
