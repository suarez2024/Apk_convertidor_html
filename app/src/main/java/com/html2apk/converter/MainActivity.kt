package com.html2apk.converter

import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.html2apk.converter.core.ApkInstaller
import com.html2apk.converter.core.ApkSignerHelper
import com.html2apk.converter.core.ProjectManager
import com.html2apk.converter.core.TemplatePatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var etAppName: EditText
    private lateinit var etHtml: EditText
    private lateinit var tvStatus: TextView
    private lateinit var webPreview: WebView
    private lateinit var projects: ProjectManager

    private var iconFile: File? = null

    private val pickIcon = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try {
            val dest = File(cacheDir, "icon.png")
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            iconFile = dest
            toast("Icono cargado")
        } catch (e: Exception) {
            showError("Error icono", e)
        }
    }

    private val pickZip = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try {
            val tmpZip = File(cacheDir, "import_${System.currentTimeMillis()}.zip")
            contentResolver.openInputStream(uri)?.use { input ->
                tmpZip.outputStream().use { input.copyTo(it) }
            }
            projects.unzipIntoWww(tmpZip)
            etHtml.setText(projects.readIndexHtml(""))
            refreshStatus()
            toast("ZIP importado: ${projects.listFiles().size} archivos")
        } catch (e: Exception) {
            showError("Error ZIP (debe contener index.html)", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projects = ProjectManager(this)
        etAppName = findViewById(R.id.etAppName)
        etHtml = findViewById(R.id.etHtml)
        tvStatus = findViewById(R.id.tvStatus)
        webPreview = findViewById(R.id.webPreview)

        with(webPreview.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
        }
        webPreview.webViewClient = WebViewClient()

        val defaultHtml = """
            <!DOCTYPE html><html lang="es"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Mi app</title></head>
            <body style="font-family:sans-serif;padding:24px">
            <h1>Mi primera app</h1><p>Edita este HTML o importa un ZIP.</p>
            <p><a href="pagina2.html">Pagina 2</a></p></body></html>
        """.trimIndent()
        etHtml.setText(projects.readIndexHtml(defaultHtml))
        refreshStatus()

        findViewById<Button>(R.id.btnPickIcon).setOnClickListener { pickIcon.launch("image/png") }
        findViewById<Button>(R.id.btnImportZip).setOnClickListener { pickZip.launch("application/zip") }
        findViewById<Button>(R.id.btnPreview).setOnClickListener {
            projects.saveIndexHtml(etHtml.text.toString())
            // Preview local: carga el index guardado
            webPreview.loadUrl("file://${projects.wwwDir}/index.html")
            refreshStatus()
        }
        findViewById<Button>(R.id.btnGenerate).setOnClickListener { generate() }
    }

    private fun refreshStatus() {
        val files = try { projects.listFiles() } catch (_: Exception) { emptyList() }
        tvStatus.text = "Archivos en www/ (${files.size}): " +
            (files.take(8).joinToString(", ").ifBlank { "solo index.html" })
    }

    private fun generate() {
        val appName = etAppName.text.toString().trim()
        if (appName.isEmpty()) {
            toast(getString(R.string.msg_need_name)); return
        }
        toast(getString(R.string.msg_generating))
        lifecycleScope.launch(Dispatchers.IO) {
            val installer = ApkInstaller(this@MainActivity)
            try {
                setPhase(getString(R.string.phase_patch))
                projects.saveIndexHtml(etHtml.text.toString())
                val patcher = TemplatePatcher(this@MainActivity)
                if (patcher.availableStubs().isEmpty()) {
                    throw IllegalStateException(getString(R.string.msg_no_stub))
                }
                val unsigned = File(cacheDir, "unsigned.apk")
                val signed = File(cacheDir, "signed.apk")
                patcher.patch(projects.wwwDir, appName, iconFile, unsigned)

                setPhase(getString(R.string.phase_sign))
                ApkSignerHelper(this@MainActivity).sign(unsigned, signed)

                if (!installer.canInstall()) {
                    withContext(Dispatchers.Main) {
                        showUnknownSourcesDialog(installer)
                    }
                    return@launch
                }

                setPhase(getString(R.string.phase_install))
                val result = installer.install(signed, appName)
                withContext(Dispatchers.Main) {
                    tvStatus.text = getString(R.string.msg_done_detail, result.fileName)
                    toast(getString(R.string.msg_done) + " " + result.fileName)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError(getString(R.string.dlg_error_title), e)
                }
            }
        }
    }

    private suspend fun setPhase(text: String) =
        withContext(Dispatchers.Main) { tvStatus.text = text }

    /** Error persistente: queda en pantalla y en dialogo (no solo Toast fugaz). */
    private fun showError(title: String, e: Exception) {
        val detail = "${e.javaClass.simpleName}: ${e.message}"
        tvStatus.text = "Error: $detail"
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Se ha producido un error:\n\n$detail\n\nCopia este texto si necesitas reportarlo.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showUnknownSourcesDialog(installer: ApkInstaller) {
        tvStatus.text = getString(R.string.msg_need_unknown_sources)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dlg_unknown_title))
            .setMessage(getString(R.string.dlg_unknown_msg))
            .setPositiveButton(getString(R.string.dlg_open_settings)) { _, _ ->
                try {
                    startActivity(installer.unknownSourcesIntent())
                } catch (e: Exception) {
                    showError("No se pudo abrir ajustes", e)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
