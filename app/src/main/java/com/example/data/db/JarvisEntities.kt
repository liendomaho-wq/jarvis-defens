package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val commandText: String,
    val response: String,
    val actionBadge: String?,
    val isSuccess: Boolean
)

@Entity(tableName = "automation_rules")
data class AutomationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val triggerType: String,
    val threshold: Int,
    val actionsJson: String,
    val isEnabled: Boolean = true
)

@Entity(tableName = "saved_locations")
data class SavedLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "memory_notes")
data class MemoryNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val content: String,
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "learned_macros")
data class LearnedMacroEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val phrase: String,
    val targetAction: String,
    val macroStepsJson: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
