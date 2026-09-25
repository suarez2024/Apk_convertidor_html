package com.html2apk.converter.core

import java.io.File

/**
 * Parche best-effort del app_name en resources.arsc binario.
 *
 * Reescribir arsc al 100% en el movil es fragil, asi que esta v1 hace:
 *  - Si el nuevo nombre cabe en el hueco del string original (UTF-16),
 *    lo sobrescribe in-place (caso "Mi App HTML" -> nombres cortos).
 *  - Si no cabe, no toca nada y el instalador avisara que el label
 *    visible sera el de la plantilla (la app funciona igual).
 *
 * v2: editor arsc completo o build con servidor para package/label libres.
 */
object ArscLabelPatcher {

    fun tryPatchLabel(apkFile: File, newLabel: String): Boolean {
        // Implementacion in-place minima: busca la cadena de la plantilla
        // en el ZIP (resources.arsc) y la reemplaza si cabe.
        // Devuelve false si no pudo (no es fatal).
        return try {
            val bytes = apkFile.readBytes()
            val marker = "Mi App HTML".toByteArray(charset("UTF-16LE"))
            val idx = indexOf(bytes, marker)
            if (idx < 0) return false
            val replacement = newLabel.toByteArray(charset("UTF-16LE"))
            if (replacement.size > marker.size) return false // no cabe: no tocar
            // Sobrescribir + rellenar con ceros
            replacement.copyInto(bytes, idx)
            for (i in idx + replacement.size until idx + marker.size) bytes[i] = 0
            apkFile.writeBytes(bytes)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty()) return 0
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
