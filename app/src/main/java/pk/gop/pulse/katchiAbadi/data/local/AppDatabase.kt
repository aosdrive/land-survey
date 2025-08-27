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
import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyLogEntity

@Database(
    entities = [NewSurveyNewEntity::class, SurveyPersonEntity::class, SurveyImage::class, ParcelEntity::class, KachiAbadiEntity::class, SurveyEntity::class, SurveyFormEntity::class, TempSurveyFormEntity::class, TempSurveyLogEntity::class, NotAtHomeSurveyFormEntity::class, ActiveParcelEntity::class],
    version = 5,
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