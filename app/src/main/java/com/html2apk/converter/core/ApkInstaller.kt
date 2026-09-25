package com.html2apk.converter.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Instala el APK firmado sin usar rutas publicas directas
 * (bloqueadas por scoped storage desde Android 10).
 *
 * - El instalador recibe el APK desde almacenamiento privado via FileProvider.
 * - Ademas se publica una copia en Descargas/Html2Apk via MediaStore
 *   (Android 10+) para que el usuario conserve el archivo.
 */
class ApkInstaller(private val context: Context) {

    /** True si el sistema permite a esta app lanzar instalaciones (Android 8+). */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** Intent a Ajustes para conceder "instalar apps desconocidas". */
    fun unknownSourcesIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun install(signedApk: File, appName: String): InstallResult {
        val safe = appName.replace(Regex("[^a-zA-Z0-9-_]+"), "_").ifBlank { "mi-app" }
        val fileName = "$safe.apk"

        // 1. Copia en Descargas (best-effort: si falla, se instala igual).
        var downloadUri: Uri? = null
        try {
            downloadUri = copyToDownloads(signedApk, fileName)
        } catch (_: Exception) {
            downloadUri = null
        }

        // 2. Copia privada para servir al instalador via FileProvider.
        val privateCopy = File(context.filesDir, "generated/$fileName").apply {
            parentFile?.mkdirs()
        }
        signedApk.copyTo(privateCopy, overwrite = true)
        val contentUri = FileProvider.getUriForFile(
            context, "com.html2apk.converter.fileprovider", privateCopy
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        return InstallResult(contentUri, downloadUri, fileName)
    }

    private fun copyToDownloads(signedApk: File, fileName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 9 o menor: escritura directa clasica.
            val destDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Html2Apk"
            ).apply { mkdirs() }
            signedApk.copyTo(File(destDir, fileName), overwrite = true)
            return null
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Html2Apk")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore no devolvio URI")
        resolver.openOutputStream(uri)?.use { out ->
            signedApk.inputStream().use { it.copyTo(out) }
        } ?: throw IllegalStateException("No se pudo escribir en Descargas")
        return uri
    }

    data class InstallResult(
        val installUri: Uri,
        val downloadUri: Uri?,
        val fileName: String
    )
}
