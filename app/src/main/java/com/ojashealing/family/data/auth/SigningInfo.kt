package com.ojashealing.family.data.auth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * SHA-1 of the certificate this APK is signed with, as "AA:BB:...". Google Sign-In and phone auth only
 * work when this exact fingerprint is registered for the app in the Firebase console.
 */
@Suppress("DEPRECATION")
fun signingSha1(context: Context): String? = runCatching {
    val pm = context.packageManager
    val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: return null
        if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
    } else {
        pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
    }
    val cert = certs?.firstOrNull() ?: return null
    MessageDigest.getInstance("SHA-1").digest(cert.toByteArray()).joinToString(":") { "%02X".format(it) }
}.getOrNull()
