package com.l4ngel.ritim.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.l4ngel.ritim.domain.Habit
import com.l4ngel.ritim.domain.Redemption
import com.l4ngel.ritim.domain.Reward
import com.l4ngel.ritim.domain.SkyKind
import com.l4ngel.ritim.domain.SkyObject
import com.l4ngel.ritim.domain.Schedule
import com.l4ngel.ritim.domain.ScheduleType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val color: Int,
    val scheduleType: String,
    val weekdayMask: Int,
    val timesPerWeek: Int,
    val target: Int,
    val unit: String,
    /** Gece yarısından dakika; null = hatırlatma yok. */
    val reminderMinute: Int?,
    val createdEpochDay: Long,
    val archived: Boolean,
    val sortOrder: Int,
    val note: String,
    @ColumnInfo(defaultValue = "10") val points: Int = 10,
)

/** Bir alışkanlığın bir gündeki değeri. Satır yoksa değer 0; 0'a inen kayıt silinir. */
@Entity(
    tableName = "checks",
    primaryKeys = ["habitId", "epochDay"],
    foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("habitId")],
)
data class CheckEntity(val habitId: Long, val epochDay: Long, val value: Int)

@Entity(tableName = "rewards")
data class RewardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val cost: Int,
    val repeatable: Boolean,
    val sortOrder: Int,
)

/** Alınan ödül; ödüle yabancı anahtar yok ki ödül silinince geçmiş kaybolmasın. */
@Entity(tableName = "redemptions")
data class RedemptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rewardId: Long?,
    val name: String,
    val emoji: String,
    val cost: Int,
    val epochDay: Long,
    /** Aynı gün içinde sıralamak için epoch milisaniye. */
    val at: Long,
)

/** Evrene yerleştirilen gök cismi; konum gökyüzü genişliği biriminde. */
@Entity(tableName = "sky_objects")
data class SkyObjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val color: Int,
    val name: String,
    val x: Float,
    val y: Float,
    val cost: Int,
    val epochDay: Long,
)

fun SkyObjectEntity.toDomain(): SkyObject? = runCatching { SkyKind.valueOf(kind) }.getOrNull()?.let {
    SkyObject(id, it, color, name, x, y, cost, LocalDate.ofEpochDay(epochDay))
}
fun SkyObject.toEntity() = SkyObjectEntity(id, kind.name, color, name, x, y, cost, date.toEpochDay())

fun RewardEntity.toDomain() = Reward(id, name, emoji, cost, repeatable, sortOrder)
fun Reward.toEntity() = RewardEntity(id, name, emoji, cost, repeatable, sortOrder)
fun RedemptionEntity.toDomain() = Redemption(id, rewardId, name, emoji, cost, LocalDate.ofEpochDay(epochDay), at)
fun Redemption.toEntity() = RedemptionEntity(id, rewardId, name, emoji, cost, date.toEpochDay(), at)

fun HabitEntity.toDomain() = Habit(
    id = id,
    name = name,
    icon = icon,
    color = color,
    schedule = Schedule(
        type = runCatching { ScheduleType.valueOf(scheduleType) }.getOrDefault(ScheduleType.DAILY),
        days = Schedule.daysOf(weekdayMask),
        timesPerWeek = timesPerWeek,
    ),
    target = target,
    unit = unit,
    reminder = reminderMinute?.let { LocalTime.of(it / 60, it % 60) },
    createdOn = LocalDate.ofEpochDay(createdEpochDay),
    archived = archived,
    sortOrder = sortOrder,
    note = note,
    points = points,
)

fun Habit.toEntity() = HabitEntity(
    id = id,
    name = name,
    icon = icon,
    color = color,
    scheduleType = schedule.type.name,
    weekdayMask = Schedule.maskOf(schedule.days),
    timesPerWeek = schedule.timesPerWeek,
    target = target,
    unit = unit,
    reminderMinute = reminder?.let { it.hour * 60 + it.minute },
    createdEpochDay = createdOn.toEpochDay(),
    archived = archived,
    sortOrder = sortOrder,
    note = note,
    points = points,
)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY sortOrder, id")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM checks")
    fun observeChecks(): Flow<List<CheckEntity>>

    @Query("SELECT * FROM habits ORDER BY sortOrder, id")
    suspend fun allHabits(): List<HabitEntity>

    @Query("SELECT * FROM checks")
    suspend fun allChecks(): List<CheckEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun habit(id: Long): HabitEntity?

    @Query("SELECT * FROM checks WHERE habitId = :id")
    suspend fun checksFor(id: Long): List<CheckEntity>

    @Query("SELECT value FROM checks WHERE habitId = :id AND epochDay = :day")
    suspend fun value(id: Long, day: Long): Int?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM habits")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("UPDATE habits SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE habits SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)

    @Upsert
    suspend fun upsertCheck(check: CheckEntity)

    @Query("DELETE FROM checks WHERE habitId = :id AND epochDay = :day")
    suspend fun deleteCheck(id: Long, day: Long)

    @Query("DELETE FROM habits")
    suspend fun deleteAllHabits()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabits(habits: List<HabitEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecks(checks: List<CheckEntity>)

    @Query("SELECT * FROM rewards ORDER BY sortOrder, id")
    fun observeRewards(): Flow<List<RewardEntity>>

    @Query("SELECT * FROM redemptions ORDER BY at DESC")
    fun observeRedemptions(): Flow<List<RedemptionEntity>>

    @Query("SELECT * FROM rewards ORDER BY sortOrder, id")
    suspend fun allRewards(): List<RewardEntity>

    @Query("SELECT * FROM redemptions ORDER BY at DESC")
    suspend fun allRedemptions(): List<RedemptionEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM rewards")
    suspend fun nextRewardOrder(): Int

    @Upsert
    suspend fun upsertReward(reward: RewardEntity): Long

    @Query("DELETE FROM rewards WHERE id = :id")
    suspend fun deleteReward(id: Long)

    @Insert
    suspend fun insertRedemption(r: RedemptionEntity): Long

    @Query("DELETE FROM redemptions WHERE id = :id")
    suspend fun deleteRedemption(id: Long)

    @Query("DELETE FROM rewards")
    suspend fun deleteAllRewards()

    @Query("DELETE FROM redemptions")
    suspend fun deleteAllRedemptions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRewards(rewards: List<RewardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRedemptions(r: List<RedemptionEntity>)

    @Query("SELECT * FROM sky_objects ORDER BY id")
    fun observeSkyObjects(): Flow<List<SkyObjectEntity>>

    @Query("SELECT * FROM sky_objects ORDER BY id")
    suspend fun allSkyObjects(): List<SkyObjectEntity>

    @Upsert
    suspend fun upsertSkyObject(o: SkyObjectEntity): Long

    @Query("DELETE FROM sky_objects WHERE id = :id")
    suspend fun deleteSkyObject(id: Long)

    @Query("DELETE FROM sky_objects")
    suspend fun deleteAllSkyObjects()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkyObjects(o: List<SkyObjectEntity>)

    /** Yedekten geri yükleme: her şey tek işlemde değişir, yarıda kalırsa eski veri durur. */
    @Transaction
    suspend fun replaceAll(content: Backup.Content) {
        deleteAllHabits()
        deleteAllRewards()
        deleteAllRedemptions()
        deleteAllSkyObjects()
        insertHabits(content.habits)
        insertChecks(content.checks)
        insertRewards(content.rewards)
        insertRedemptions(content.redemptions)
        insertSkyObjects(content.sky)
    }

    @Transaction
    suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { i, id -> setSortOrder(id, i) }
    }
}

@Database(
    entities = [HabitEntity::class, CheckEntity::class, RewardEntity::class, RedemptionEntity::class, SkyObjectEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habits(): HabitDao

    companion object {
        /** v2: alışkanlık puanı ve ödül tabloları. Var olan alışkanlıklar "orta" zorlukla gelir. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN points INTEGER NOT NULL DEFAULT 10")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS rewards (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                        "emoji TEXT NOT NULL, cost INTEGER NOT NULL, repeatable INTEGER NOT NULL, sortOrder INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS redemptions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, rewardId INTEGER, " +
                        "name TEXT NOT NULL, emoji TEXT NOT NULL, cost INTEGER NOT NULL, epochDay INTEGER NOT NULL, at INTEGER NOT NULL)",
                )
            }
        }

        /** v3: evrene yerleştirilen gök cisimleri. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sky_objects (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, kind TEXT NOT NULL, " +
                        "color INTEGER NOT NULL, name TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, cost INTEGER NOT NULL, epochDay INTEGER NOT NULL)",
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ritim.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
