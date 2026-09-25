package com.html2apk.converter.core

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Motor offline: parchea una plantilla stub pre-compilada.
 *
 * Plantillas esperadas en assets/stubs/: stub1.apk ... stub5.apk
 * (generadas por el CI a partir del modulo :stub).
 *
 * Pasos:
 *  1. Copia el stub rotando (para evitar colision de package).
 *  2. Reemplaza assets/www/* con el proyecto del usuario.
 *  3. Reemplaza icono si el usuario eligio uno (res/mipmap-*/ic_launcher.png).
 *  4. Intenta parchear el label en resources.arsc (si falla, se conserva
 *     el label original pero la app sigue instalando: ver ArscLabelPatcher).
 *  5. Devuelve el APK sin firmar (el firmador lo firma despues).
 */
class TemplatePatcher(private val context: Context) {

    private var stubCounter = 0

    fun availableStubs(): List<String> =
        (context.assets.list("stubs")?.filter { it.endsWith(".apk") } ?: emptyList()).sorted()

    fun patch(wwwDir: File, appName: String, iconFile: File?, outApk: File): File {
        val stubs = availableStubs()
        require(stubs.isNotEmpty()) { "no-stub" }
        val stubName = stubs[stubCounter % stubs.size]
        stubCounter++

        // 1. copiar stub a temporal
        val tmp = File(context.cacheDir, "patched_${System.currentTimeMillis()}.apk")
        context.assets.open("stubs/$stubName").use { input ->
            FileOutputStream(tmp).use { input.copyTo(it) }
        }

        // 2-3. re-empaquetar ZIP reemplazando www + icono
        val tmp2 = File(context.cacheDir, "patched2_${System.currentTimeMillis()}.apk")
        ZipFile(tmp).use { zin ->
            ZipOutputStream(FileOutputStream(tmp2)).use { zout ->
                val replaced = mutableSetOf<String>()

                // a) copiar entradas originales excepto www/ e iconos (se sustituyen)
                zin.entries().asSequence().forEach { entry ->
                    val name = entry.name
                    if (name.startsWith("assets/www/")) return@forEach
                    if (isIconEntry(name) && iconFile != null) return@forEach
                    // META-INF se regenera al firmar: lo omitimos
                    if (name.startsWith("META-INF/")) return@forEach
                    zout.putNextEntry(ZipEntry(name).apply {
                        method = entry.method
                        time = entry.time
                        // Las entradas STORED (resources.arsc, .so, etc.) exigen
                        // size/crc prefijados o ZipOutputStream lanza
                        // "STORED entry missing size, compressed size, or CRC-32".
                        if (method == ZipEntry.STORED) {
                            size = entry.size
                            compressedSize = entry.compressedSize
                            crc = entry.crc
                        }
                    })
                    zin.getInputStream(entry).copyTo(zout)
                    zout.closeEntry()
                }

                // b) inyectar www/ del usuario
                wwwDir.walkTopDown().filter { it.isFile }.forEach { f ->
                    val rel = f.relativeTo(wwwDir).path.replace('\\', '/')
                    val entryName = "assets/www/$rel"
                    zout.putNextEntry(ZipEntry(entryName))
                    f.inputStream().use { it.copyTo(zout) }
                    zout.closeEntry()
                    replaced += entryName
                }

                // c) inyectar icono en densidades tipicas
                if (iconFile != null && iconFile.exists()) {
                    val bytes = iconFile.readBytes()
                    listOf(
                        "res/mipmap-hdpi-v4/ic_launcher.png",
                        "res/mipmap-mdpi-v4/ic_launcher.png",
                        "res/mipmap-xhdpi-v4/ic_launcher.png",
                        "res/mipmap-xxhdpi-v4/ic_launcher.png",
                        "res/mipmap-xxxhdpi-v4/ic_launcher.png",
                        "res/mipmap-hdpi/ic_launcher.png",
                        "res/mipmap-mdpi/ic_launcher.png",
                        "res/mipmap-xhdpi/ic_launcher.png",
                        "res/mipmap-xxhdpi/ic_launcher.png",
                        "res/mipmap-xxxhdpi/ic_launcher.png"
                    ).forEach { iconPath ->
                        try {
                            zout.putNextEntry(ZipEntry(iconPath))
                            zout.write(bytes)
                            zout.closeEntry()
                        } catch (_: Exception) { }
                    }
                }
            }
        }

        // 4. parchear label (best-effort)
        try {
            ArscLabelPatcher.tryPatchLabel(tmp2, appName)
        } catch (_: Exception) {
            // Se ignora: la app instala igual con el label de la plantilla.
        }

        tmp.delete()
        tmp2.copyTo(outApk, overwrite = true)
        tmp2.delete()
        return outApk
    }

    private fun isIconEntry(name: String): Boolean =
        name.startsWith("res/mipmap-") && (name.endsWith("ic_launcher.png") || name.endsWith("ic_launcher.webp"))
}
