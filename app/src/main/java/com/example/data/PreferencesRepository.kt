package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "openv_studio_prefs")

data class RecentItemRecord(
    val title: String,
    val path: String,
    val type: String // RPF, OIV, DIR
)

data class InstalledModRecord(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val targetArchive: String,
    val backupPath: String,
    val installedAtMillis: Long
)

data class AppSettings(
    val isDarkTheme: Boolean = true,
    val isEditMode: Boolean = false,
    val autoBackupOnEdit: Boolean = true,
    val recentItems: List<RecentItemRecord> = emptyList(),
    val installedMods: List<InstalledModRecord> = emptyList()
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val EDIT_MODE = booleanPreferencesKey("edit_mode")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val RECENT_ITEMS = stringPreferencesKey("recent_items")
        val INSTALLED_MODS = stringPreferencesKey("installed_mods")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            isDarkTheme = prefs[Keys.DARK_THEME] ?: true,
            isEditMode = prefs[Keys.EDIT_MODE] ?: false,
            autoBackupOnEdit = prefs[Keys.AUTO_BACKUP] ?: true,
            recentItems = decodeRecents(prefs[Keys.RECENT_ITEMS].orEmpty()),
            installedMods = decodeMods(prefs[Keys.INSTALLED_MODS].orEmpty())
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setEditMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EDIT_MODE] = enabled }
    }

    suspend fun setAutoBackup(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_BACKUP] = enabled }
    }

    suspend fun addRecentItem(item: RecentItemRecord) {
        context.dataStore.edit { prefs ->
            val current = decodeRecents(prefs[Keys.RECENT_ITEMS].orEmpty())
                .filterNot { it.path == item.path }
            val updated = (listOf(item) + current).take(8)
            prefs[Keys.RECENT_ITEMS] = encodeRecents(updated)
        }
    }

    suspend fun addInstalledMod(mod: InstalledModRecord) {
        context.dataStore.edit { prefs ->
            val current = decodeMods(prefs[Keys.INSTALLED_MODS].orEmpty())
                .filterNot { it.id == mod.id }
            prefs[Keys.INSTALLED_MODS] = encodeMods(listOf(mod) + current)
        }
    }

    suspend fun removeInstalledMod(modId: String) {
        context.dataStore.edit { prefs ->
            val current = decodeMods(prefs[Keys.INSTALLED_MODS].orEmpty())
                .filterNot { it.id == modId }
            prefs[Keys.INSTALLED_MODS] = encodeMods(current)
        }
    }

    private fun encodeRecents(list: List<RecentItemRecord>): String =
        list.joinToString("\n") { "${sanitize(it.title)}|${sanitize(it.path)}|${sanitize(it.type)}" }

    private fun decodeRecents(raw: String): List<RecentItemRecord> =
        raw.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size >= 3) RecentItemRecord(parts[0], parts[1], parts[2]) else null
        }

    private fun encodeMods(list: List<InstalledModRecord>): String =
        list.joinToString("\n") {
            "${sanitize(it.id)}|${sanitize(it.name)}|${sanitize(it.version)}|${sanitize(it.author)}|${sanitize(it.targetArchive)}|${sanitize(it.backupPath)}|${it.installedAtMillis}"
        }

    private fun decodeMods(raw: String): List<InstalledModRecord> =
        raw.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size >= 7) {
                InstalledModRecord(
                    id = parts[0],
                    name = parts[1],
                    version = parts[2],
                    author = parts[3],
                    targetArchive = parts[4],
                    backupPath = parts[5],
                    installedAtMillis = parts[6].toLongOrNull() ?: 0L
                )
            } else null
        }

    private fun sanitize(s: String): String = s.replace("|", "_").replace("\n", " ")
}
