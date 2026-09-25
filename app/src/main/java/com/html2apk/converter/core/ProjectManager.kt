package com.html2apk.converter.core

import android.content.Context
import java.io.File

/**
 * Gestiona el proyecto multipagina en almacenamiento interno:
 * files/projects/default/www/index.html + resto de archivos.
 */
class ProjectManager(private val context: Context) {

    val wwwDir: File
        get() = File(context.filesDir, "projects/default/www").apply { mkdirs() }

    fun saveIndexHtml(html: String): File {
        val dir = wwwDir
        val f = File(dir, "index.html")
        f.writeText(html)
        return f
    }

    fun readIndexHtml(default: String): String {
        val f = File(wwwDir, "index.html")
        if (!f.exists()) {
            f.parentFile?.mkdirs()
            f.writeText(default)
        }
        return f.readText()
    }

    /** Descomprime un ZIP importado dentro de www/ (soporta subcarpetas, css, js, img). */
    fun unzipIntoWww(zipFile: File) {
        val dest = wwwDir
        java.util.zip.ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val out = File(dest, entry.name)
                // Proteccion Zip-Slip
                if (!out.canonicalPath.startsWith(dest.canonicalPath)) return@forEach
                if (entry.isDirectory) out.mkdirs()
                else {
                    out.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    }
                }
            }
        }
    }

    fun listFiles(): List<String> =
        wwwDir.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(wwwDir).path.replace('\\', '/') }
            .sorted()
            .toList()
}
