package com.html2apk.converter.core

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

/** Copia el APK firmado a Download/Html2Apk/ y lanza el instalador del sistema. */
class ApkInstaller(private val context: Context) {

    fun install(signedApk: File, appName: String): File {
        val destDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Html2Apk"
        ).apply { mkdirs() }
        val safe = appName.replace(Regex("[^a-zA-Z0-9-_]+"), "_").ifBlank { "mi-app" }
        val dest = File(destDir, "$safe.apk")
        signedApk.copyTo(dest, overwrite = true)

        val uri = FileProvider.getUriForFile(
            context, "com.html2apk.converter.fileprovider", dest
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        return dest
    }
}
