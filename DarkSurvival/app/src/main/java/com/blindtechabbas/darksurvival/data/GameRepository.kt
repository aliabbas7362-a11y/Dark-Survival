package com.blindtechabbas.darksurvival.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "dark_survival")

/**
 * GameRepository — persists save state (highest unlocked chapter) with DataStore.
 */
class GameRepository(private val context: Context) {

    private val keyUnlockedChapter = intPreferencesKey("unlocked_chapter")

    val unlockedChapter: Flow<Int> = context.dataStore.data.map { it[keyUnlockedChapter] ?: 1 }

    suspend fun saveUnlockedChapter(id: Int) {
        context.dataStore.edit { it[keyUnlockedChapter] = id }
    }
}
