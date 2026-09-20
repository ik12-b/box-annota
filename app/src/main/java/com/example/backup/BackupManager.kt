package com.example.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Everything this app stores (session.json, the imported PDF, transcription
 * crops) lives in [Context.filesDir] — app-private internal storage that
 * Android unconditionally deletes when the app is uninstalled, no exceptions.
 * The only way any of that data can survive an uninstall+reinstall is to also
 * keep a copy somewhere OUTSIDE the app's private storage: a folder the user
 * picks via the system folder picker (Storage Access Framework), typically
 * somewhere under "Documents" on the phone's own shared storage.
 *
 * One caveat inherent to Android's security model, not something this class
 * can work around: the *permission grant* to that folder is tied to this
 * app's install and is revoked on uninstall along with everything else — only
 * the actual files physically survive. So after reinstalling, the app can't
 * silently find its old folder again; the user has to pick that same folder
 * once more (setBackupFolder), after which restore is automatic.
 */
class BackupManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "backup_settings"
        private const val PREF_FOLDER_URI = "backup_folder_uri"
        private const val PREF_LAST_BACKUP_AT = "last_backup_at"
        private const val BACKUP_FILE_NAME = "pdf_annotator_backup.zip"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val backupFolderUri: Uri?
        get() {
            val stored = prefs.getString(PREF_FOLDER_URI, null) ?: return null
            return try { Uri.parse(stored) } catch (_: Exception) { null }
        }

    val lastBackupAtMillis: Long
        get() = prefs.getLong(PREF_LAST_BACKUP_AT, 0L)

    val isFolderConfigured: Boolean
        get() = backupFolderUri != null

    /**
     * Call after the user grants access via ACTION_OPEN_DOCUMENT_TREE. Takes a
     * persistable permission (so access survives app/device restarts for the
     * remainder of this install) and remembers the folder for future backups.
     */
    fun setBackupFolder(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Some providers don't support persistable grants; backups will
            // still work for the rest of this app session either way.
        }
        prefs.edit().putString(PREF_FOLDER_URI, uri.toString()).apply()
    }

    fun clearBackupFolder() {
        prefs.edit().remove(PREF_FOLDER_URI).remove(PREF_LAST_BACKUP_AT).apply()
    }

    fun isFolderAccessible(): Boolean {
        val uri = backupFolderUri ?: return false
        return try {
            val doc = DocumentFile.fromTreeUri(context, uri)
            doc != null && doc.exists() && doc.canWrite()
        } catch (_: Exception) {
            false
        }
    }

    fun hasExistingBackup(): Boolean {
        val uri = backupFolderUri ?: return false
        return try {
            DocumentFile.fromTreeUri(context, uri)?.findFile(BACKUP_FILE_NAME)?.exists() == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Zips session.json + documents/ + crops/ from internal storage into the
     * configured folder. Never throws — returns false on any failure (folder
     * not configured/accessible, disk full, permission revoked, etc.) so an
     * automatic background backup can never crash the app.
     */
    suspend fun backupNow(): Boolean = withContext(Dispatchers.IO) {
        val treeUri = backupFolderUri ?: return@withContext false
        try {
            val treeDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext false
            if (!treeDoc.exists() || !treeDoc.canWrite()) return@withContext false

            // Overwrite in place: delete any previous backup first so repeated
            // backups don't pile up duplicate files in the folder.
            treeDoc.findFile(BACKUP_FILE_NAME)?.delete()
            val backupDoc = treeDoc.createFile("application/zip", BACKUP_FILE_NAME) ?: return@withContext false

            val opened = context.contentResolver.openOutputStream(backupDoc.uri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    val sessionFile = File(context.filesDir, "annotator_session.json")
                    if (sessionFile.exists()) {
                        zipAddFile(zip, sessionFile, "annotator_session.json")
                    }
                    File(context.filesDir, "documents").listFiles()?.forEach { f ->
                        if (f.isFile) zipAddFile(zip, f, "documents/${f.name}")
                    }
                    File(context.filesDir, "crops").listFiles()?.forEach { f ->
                        if (f.isFile) zipAddFile(zip, f, "crops/${f.name}")
                    }
                }
                true
            } ?: false

            if (opened) {
                prefs.edit().putLong(PREF_LAST_BACKUP_AT, System.currentTimeMillis()).apply()
            }
            opened
        } catch (_: Exception) {
            false
        }
    }

    private fun zipAddFile(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    /**
     * Extracts the backup zip (if any) from the configured folder back into
     * internal storage. Intended to run once at app start when internal
     * storage is empty (fresh install / reinstall) — callers should check
     * that themselves before calling this, so a restore never overwrites
     * in-progress work.
     */
    suspend fun restoreNow(): Boolean = withContext(Dispatchers.IO) {
        val treeUri = backupFolderUri ?: return@withContext false
        try {
            val treeDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext false
            val backupDoc = treeDoc.findFile(BACKUP_FILE_NAME) ?: return@withContext false
            if (!backupDoc.exists()) return@withContext false

            val opened = context.contentResolver.openInputStream(backupDoc.uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            // Guard against a malicious/corrupt zip entry escaping
                            // filesDir via "../" path segments.
                            val safeName = entry.name.replace("..", "")
                            val outFile = File(context.filesDir, safeName)
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out -> zip.copyTo(out) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                true
            } ?: false

            opened
        } catch (_: Exception) {
            false
        }
    }
}
