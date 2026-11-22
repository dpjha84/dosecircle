package com.dosecircle.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

@Dao
interface CareDao {
    @Query("SELECT * FROM recipients ORDER BY name COLLATE NOCASE")
    fun recipients(): List<RecipientEntity>

    @Upsert
    fun upsert(e: RecipientEntity)

    @Query("DELETE FROM recipients WHERE id = :id")
    fun deleteRecipient(id: String)

    @Query("DELETE FROM medications WHERE recipientId = :id")
    fun deleteMedicationsOf(id: String)

    @Query("DELETE FROM entries WHERE recipientId = :id")
    fun deleteEntriesOf(id: String)

    @Query("SELECT * FROM members ORDER BY rowid")
    fun members(): List<MemberEntity>

    @Upsert
    fun upsert(e: MemberEntity)

    @Query("DELETE FROM members WHERE id = :id")
    fun deleteMember(id: String)

    @Query("SELECT * FROM medications WHERE (:recipientId IS NULL OR recipientId = :recipientId) AND (:includeArchived = 1 OR isArchived = 0) ORDER BY name COLLATE NOCASE")
    fun medications(recipientId: String?, includeArchived: Boolean): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE id = :id")
    fun medication(id: String): MedicationEntity?

    @Upsert
    fun upsert(e: MedicationEntity)

    @Query("DELETE FROM medications WHERE id = :id")
    fun deleteMedication(id: String)

    @Query("DELETE FROM entries WHERE medicationId = :id")
    fun deleteEntriesOfMedication(id: String)

    @Query("SELECT * FROM entries WHERE recipientId = :recipientId AND dateKey >= :from AND dateKey <= :to")
    fun entries(recipientId: String, from: String, to: String): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE id = :id")
    fun entry(id: String): EntryEntity?

    @Query("SELECT * FROM entries WHERE occurrenceKey = :key LIMIT 1")
    fun entryByKey(key: String): EntryEntity?

    @Query("SELECT * FROM entries WHERE medicationId = :medicationId AND loggedAtMillis >= :sinceMillis")
    fun recentEntries(medicationId: String, sinceMillis: Long): List<EntryEntity>

    @Upsert
    fun upsert(e: EntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    fun deleteEntry(id: String)
}

@Database(
    entities = [RecipientEntity::class, MemberEntity::class, MedicationEntity::class, EntryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): CareDao
}
