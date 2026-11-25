package com.example.data.db

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class JarvisRepository(context: Context) {

    private val db = JarvisDatabase.getInstance(context)
    val commandHistory: Flow<List<CommandHistoryEntity>> = db.commandHistoryDao().getRecentCommands()
    val automationRules: Flow<List<AutomationRuleEntity>> = db.automationRuleDao().getAllRules()
    val savedLocations: Flow<List<SavedLocationEntity>> = db.savedLocationDao().getAllLocations()
    val memoryNotes: Flow<List<MemoryNoteEntity>> = db.memoryNoteDao().getAllNotes()
    val learnedMacros: Flow<List<LearnedMacroEntity>> = db.learnedMacroDao().getAllMacros()

    suspend fun recordCommand(command: String, response: String, badge: String?, success: Boolean) {
        db.commandHistoryDao().insert(
            CommandHistoryEntity(
                commandText = command,
                response = response,
                actionBadge = badge,
                isSuccess = success
            )
        )
    }

    suspend fun saveLocation(label: String, lat: Double, lon: Double, address: String = ""): Long {
        return db.savedLocationDao().insert(
            SavedLocationEntity(label = label, latitude = lat, longitude = lon, address = address)
        )
    }

    suspend fun findLocation(query: String): SavedLocationEntity? {
        return db.savedLocationDao().findByLabel(query)
    }

    suspend fun saveNote(title: String, content: String): Long {
        return db.memoryNoteDao().insert(MemoryNoteEntity(title = title, content = content))
    }

    suspend fun getAllNotes(): List<MemoryNoteEntity> {
        return db.memoryNoteDao().getAllNotesList()
    }

    suspend fun clearNotes(): Int {
        return db.memoryNoteDao().clearAll()
    }

    suspend fun saveMacro(phrase: String, targetAction: String, stepsJson: String = ""): Long {
        return db.learnedMacroDao().insert(
            LearnedMacroEntity(phrase = phrase, targetAction = targetAction, macroStepsJson = stepsJson)
        )
    }

    suspend fun findMacro(phrase: String): LearnedMacroEntity? {
        return db.learnedMacroDao().findByPhrase(phrase)
    }

    suspend fun getAllMacros(): List<LearnedMacroEntity> {
        return db.learnedMacroDao().getAllMacrosList()
    }

    suspend fun exportBackupJson(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            val notes = db.memoryNoteDao().getAllNotesList()
            val notesArr = JSONArray()
            for (n in notes) {
                notesArr.put(JSONObject().apply {
                    put("title", n.title)
                    put("content", n.content)
                    put("createdAt", n.createdAt)
                })
            }
            root.put("notes", notesArr)

            val macros = db.learnedMacroDao().getAllMacrosList()
            val macrosArr = JSONArray()
            for (m in macros) {
                macrosArr.put(JSONObject().apply {
                    put("phrase", m.phrase)
                    put("action", m.targetAction)
                    put("steps", m.macroStepsJson)
                })
            }
            root.put("macros", macrosArr)

            val exportDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val backupFile = File(exportDir, "jarvis_backup.json")
            backupFile.writeText(root.toString(2))
            "Copia de seguridad guardada con éxito en ${backupFile.name} (${macros.size} macros, ${notes.size} notas)."
        } catch (e: Exception) {
            Log.e("JarvisRepository", "Error exporting backup", e)
            "Error al exportar la base de datos: ${e.localizedMessage}"
        }
    }

    suspend fun importBackupJson(jsonString: String): String = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            var notesImported = 0
            val notesArr = root.optJSONArray("notes")
            if (notesArr != null) {
                for (i in 0 until notesArr.length()) {
                    val obj = notesArr.getJSONObject(i)
                    db.memoryNoteDao().insert(
                        MemoryNoteEntity(
                            title = obj.optString("title", "Nota"),
                            content = obj.optString("content", "")
                        )
                    )
                    notesImported++
                }
            }

            var macrosImported = 0
            val macrosArr = root.optJSONArray("macros")
            if (macrosArr != null) {
                for (i in 0 until macrosArr.length()) {
                    val obj = macrosArr.getJSONObject(i)
                    db.learnedMacroDao().insert(
                        LearnedMacroEntity(
                            phrase = obj.optString("phrase"),
                            targetAction = obj.optString("action"),
                            macroStepsJson = obj.optString("steps", "")
                        )
                    )
                    macrosImported++
                }
            }
            "Restauración completada: $macrosImported macros y $notesImported notas importadas."
        } catch (e: Exception) {
            Log.e("JarvisRepository", "Error importing backup", e)
            "Error al importar archivo: ${e.localizedMessage}"
        }
    }
}
