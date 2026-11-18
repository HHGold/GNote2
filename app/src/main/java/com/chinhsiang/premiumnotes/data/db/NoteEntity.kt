package com.chinhsiang.premiumnotes.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.chinhsiang.premiumnotes.data.Note
import com.chinhsiang.premiumnotes.data.NoteFolder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "notes")
@TypeConverters(StringListConverter::class)
data class NoteEntity(
    @PrimaryKey val id: String,
    val folderId: String,
    val title: String,
    val content: String,
    val isLocked: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val ownerId: String?,
    val ownerEmail: String?,
    val ownerName: String?,
    val sharedWithEmails: List<String>?,
    val isSynced: Boolean
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val orderIndex: Int = 0
)

class StringListConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        if (value == null) return null
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value.isNullOrEmpty()) return null
        val type = object : TypeToken<List<String>>() {}.type
        return try {
            gson.fromJson(value, type)
        } catch (_: Exception) {
            null
        }
    }
}

// Extension functions for mapping between Entity and Domain Models
fun NoteEntity.toDomain(): Note = Note(
    id = id,
    folderId = folderId,
    title = title,
    content = NoteCrypto.decrypt(content),
    isLocked = isLocked,
    createdAt = createdAt,
    updatedAt = updatedAt,
    ownerId = ownerId,
    ownerEmail = ownerEmail,
    ownerName = ownerName,
    sharedWithEmails = sharedWithEmails,
    isSynced = isSynced
)

fun Note.toEntity(): NoteEntity = NoteEntity(
    id = id,
    folderId = folderId,
    title = title,
    content = if (isLocked) NoteCrypto.encrypt(content) else content,
    isLocked = isLocked,
    createdAt = createdAt,
    updatedAt = updatedAt,
    ownerId = ownerId,
    ownerEmail = ownerEmail,
    ownerName = ownerName,
    sharedWithEmails = sharedWithEmails,
    isSynced = isSynced
)

fun FolderEntity.toDomain(): NoteFolder = NoteFolder(
    id = id,
    name = name
)

fun NoteFolder.toEntity(orderIndex: Int = 0): FolderEntity = FolderEntity(
    id = id,
    name = name,
    orderIndex = orderIndex
)
