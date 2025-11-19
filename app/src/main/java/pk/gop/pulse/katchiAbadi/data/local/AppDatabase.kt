package pk.gop.pulse.katchiAbadi.data.local


import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.data.remote.response.NewSurveyNewDao
import pk.gop.pulse.katchiAbadi.data.remote.response.SurveyImageDao
import pk.gop.pulse.katchiAbadi.data.remote.response.SurveyPersonDao
import pk.gop.pulse.katchiAbadi.domain.model.ActiveParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.KachiAbadiEntity
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity
import pk.gop.pulse.katchiAbadi.domain.model.NotAtHomeSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.StatusConverter
import pk.gop.pulse.katchiAbadi.domain.model.SurveyEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyImage
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPersonEntity
import pk.gop.pulse.katchiAbadi.domain.model.TaskEntity
import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyLogEntity

@Database(
    entities = [NewSurveyNewEntity::class, SurveyPersonEntity::class, SurveyImage::class, ParcelEntity::class, KachiAbadiEntity::class, SurveyEntity::class, SurveyFormEntity::class, TempSurveyFormEntity::class, TempSurveyLogEntity::class, NotAtHomeSurveyFormEntity::class, ActiveParcelEntity::class, TaskEntity::class],
    version = 9,
    exportSchema = false
)
@TypeConverters(StatusConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun parcelDao(): ParcelDao
    abstract fun activeParcelDao(): ActiveParcelDao
    abstract fun tempSurveyLogDao(): TempSurveyLogDao
    abstract fun kachiAbadiDao(): KachiAbadiDao
    abstract fun surveyDao(): NewSurveyDao
    abstract fun surveyFormDao(): SurveyFormDao
    abstract fun tempSurveyFormDao(): TempSurveyFormDao
    abstract fun notAtHomeSurveyFormDao(): NotAtHomeSurveyFormDao
    abstract fun newSurveyNewDao(): NewSurveyNewDao
    abstract fun personDao(): SurveyPersonDao
    abstract fun imageDao(): SurveyImageDao
    abstract fun taskDao(): TaskDao


    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    Constants.DATABASE_NAME
                )
                    .addMigrations(migration1to2)
                    .addMigrations(migration2to3)
                    .addMigrations(migration3to4)
                    .addMigrations(migration4to5)
                    .addMigrations(migration5to6)
                    .addMigrations(migration6to7)
                    .addMigrations(migration7to8)
                    .addMigrations(migration8to9)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

val migration1to2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE NotAtHomeSurveyFormEntity ADD COLUMN qrCode TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE SurveyFormEntity ADD COLUMN qrCode TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE TempSurveyFormEntity ADD COLUMN qrCode TEXT NOT NULL DEFAULT ''")
    }
}

val migration2to3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ParcelEntity ADD COLUMN parcelNo INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ParcelEntity ADD COLUMN subParcelNo TEXT NOT NULL DEFAULT '0'")
        db.execSQL("ALTER TABLE ParcelEntity ADD COLUMN newStatusId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ParcelEntity ADD COLUMN subParcelsStatusList TEXT NOT NULL DEFAULT ''")

        db.execSQL("ALTER TABLE SurveyFormEntity ADD COLUMN parcelNo INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE SurveyFormEntity ADD COLUMN subParcelNo TEXT NOT NULL DEFAULT '0'")
        db.execSQL("ALTER TABLE SurveyFormEntity ADD COLUMN newStatusId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE SurveyFormEntity ADD COLUMN subParcelsStatusList TEXT NOT NULL DEFAULT ''")

        db.execSQL("ALTER TABLE TempSurveyFormEntity ADD COLUMN parcelNo INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE TempSurveyFormEntity ADD COLUMN subParcelNo TEXT NOT NULL DEFAULT '0'")
        db.execSQL("ALTER TABLE TempSurveyFormEntity ADD COLUMN newStatusId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE TempSurveyFormEntity ADD COLUMN subParcelsStatusList TEXT NOT NULL DEFAULT ''")

        db.execSQL("ALTER TABLE NotAtHomeSurveyFormEntity ADD COLUMN parcelNo INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE NotAtHomeSurveyFormEntity ADD COLUMN subParcelNo TEXT NOT NULL DEFAULT '0'")
        db.execSQL("ALTER TABLE NotAtHomeSurveyFormEntity ADD COLUMN newStatusId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE NotAtHomeSurveyFormEntity ADD COLUMN subParcelsStatusList TEXT NOT NULL DEFAULT ''")
    }
}

val migration3to4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE SurveyFormEntity ADD COLUMN isRevisit INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE TempSurveyFormEntity ADD COLUMN isRevisit INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE NotAtHomeSurveyFormEntity ADD COLUMN isRevisit INTEGER NOT NULL DEFAULT 0")

    }
}

val migration4to5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE active_parcels ADD COLUMN isActivate INTEGER NOT NULL DEFAULT 1")
    }
}

// ✅ NEW MIGRATION: Add tasks table
val migration5to6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS tasks (
                taskId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                assignDate TEXT NOT NULL DEFAULT '',
                issueType TEXT NOT NULL DEFAULT '',
                details TEXT NOT NULL DEFAULT '',
                picData TEXT NOT NULL DEFAULT '',
                parcelId INTEGER NOT NULL DEFAULT 0,
                parcelNo TEXT NOT NULL DEFAULT '',
                mauzaId INTEGER NOT NULL DEFAULT 0,
                assignedByUserId INTEGER NOT NULL DEFAULT 0,
                assignedToUserId INTEGER NOT NULL DEFAULT 0,
                createdOn INTEGER NOT NULL DEFAULT 0,
                isSynced INTEGER NOT NULL DEFAULT 0
            )
        """)
    }
}

val migration6to7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN khewatInfo TEXT NOT NULL DEFAULT ''")
    }
}

val migration7to8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN unitId INTEGER")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN groupId INTEGER")
    }
}

val migration8to9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE tasks ADD COLUMN daysToComplete INTEGER NOT NULL DEFAULT 0")
    }
}