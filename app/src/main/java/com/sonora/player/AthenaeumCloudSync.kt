package com.sonora.player

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Explicit, user-configured WebDAV backup sync with encrypted local credentials and ETag conflict checks. */
object AthenaeumCloudSync {
    private const val PREFS = "athenaeum_cloud_sync"
    private const val KEY_ALIAS = "athenaeum.webdav.password.v1"

    fun endpoint(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("endpoint", "").orEmpty()
    fun username(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("username", "").orEmpty()
    fun password(context: Context): String {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("password", "").orEmpty()
        if (encoded.isBlank()) return ""
        return runCatching {
            val pieces = encoded.split(':', limit = 2)
            val iv = Base64.decode(pieces[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(pieces[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun save(context: Context, endpoint: String, username: String, password: String) {
        validateEndpoint(endpoint)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("endpoint", endpoint.trim())
            .putString("username", username.trim())
            .putString("password", encoded)
            .apply()
    }

    suspend fun uploadBackup(context: Context, dao: AthenaeumDao, endpoint: String, username: String, password: String): String = withContext(Dispatchers.IO) {
        validateEndpoint(endpoint)
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val knownEtag = preferences.getString("remote_etag", "").orEmpty()
        val remote = head(endpoint, username, password)
        if (remote.first in 200..299 && knownEtag.isBlank()) {
            error("A backup already exists at this address. Download it first before enabling overwrite protection.")
        }
        if (knownEtag.isNotBlank() && remote.second != knownEtag) {
            error("Sync conflict: the cloud backup changed elsewhere. Download and review it before uploading again.")
        }
        if (remote.first !in 200..299 && remote.first != 404) {
            error("The WebDAV server did not allow a safe pre-upload check (HTTP ${remote.first}).")
        }
        val archive = File.createTempFile("athenaeum-cloud-", ".zip", context.cacheDir)
        try {
            FileOutputStream(archive).use { AthenaeumBackup.writePortableZip(context, dao, it) }
            val connection = connection(endpoint, username, password, "PUT")
            if (knownEtag.isNotBlank()) connection.setRequestProperty("If-Match", knownEtag)
            else connection.setRequestProperty("If-None-Match", "*")
            connection.setRequestProperty("Content-Type", "application/zip")
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(archive.length())
            FileInputStream(archive).use { input -> connection.outputStream.use { output -> input.copyTo(output) } }
            val code = connection.responseCode
            connection.disconnect()
            if (code !in 200..299) error(if (code == 412) "Sync conflict: the remote backup changed during upload." else "WebDAV upload failed (HTTP $code).")
            val updated = head(endpoint, username, password)
            if (updated.first !in 200..299) error("Backup uploaded, but the server did not confirm its new version.")
            preferences.edit().putString("remote_etag", updated.second).apply()
            "Portable Athenaeum backup uploaded. The server version is now tracked for conflict protection."
        } finally {
            archive.delete()
        }
    }

    suspend fun downloadAndRestore(context: Context, dao: AthenaeumDao, endpoint: String, username: String, password: String): String = withContext(Dispatchers.IO) {
        validateEndpoint(endpoint)
        val connection = connection(endpoint, username, password, "GET")
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            error("WebDAV download failed (HTTP $code).")
        }
        val etag = connection.getHeaderField("ETag").orEmpty()
        val archive = File.createTempFile("athenaeum-cloud-restore-", ".zip", context.cacheDir)
        try {
            connection.inputStream.use { input -> FileOutputStream(archive).use { output -> input.copyTo(output) } }
            connection.disconnect()
            FileInputStream(archive).use { AthenaeumBackup.restorePortableZip(context, it, dao) }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("remote_etag", etag).apply()
            "Cloud backup downloaded and restored. Existing catalogue IDs were merged using the archive's records."
        } finally {
            archive.delete()
        }
    }

    private fun validateEndpoint(value: String) {
        val url = URL(value.trim())
        require(url.protocol.equals("https", true)) { "Use an HTTPS WebDAV URL so credentials are never sent in clear text." }
        require(url.host.isNotBlank() && url.userInfo == null) { "Enter the WebDAV file URL without embedded credentials." }
    }

    private fun connection(endpoint: String, username: String, password: String, method: String): HttpURLConnection {
        val connection = URL(endpoint.trim()).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 120_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/zip")
        if (username.isNotBlank()) {
            val token = Base64.encodeToString("$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            connection.setRequestProperty("Authorization", "Basic $token")
        }
        return connection
    }

    private fun head(endpoint: String, username: String, password: String): Pair<Int, String> {
        val connection = connection(endpoint, username, password, "HEAD")
        return try {
            connection.responseCode to connection.getHeaderField("ETag").orEmpty()
        } finally {
            connection.disconnect()
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
