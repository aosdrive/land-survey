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
import pk.gop.pulse.katchiAbadi.domain.model.CropEntity
import pk.gop.pulse.katchiAbadi.domain.model.CropTypeEntity
import pk.gop.pulse.katchiAbadi.domain.model.CropVarietyEntity
import pk.gop.pulse.katchiAbadi.domain.model.DiseaseTypeEntity
import pk.gop.pulse.katchiAbadi.domain.model.IssueTypeEntity
import pk.gop.pulse.katchiAbadi.domain.model.JKGrowerEntity
import pk.gop.pulse.katchiAbadi.domain.model.KachiAbadiEntity
import pk.gop.pulse.katchiAbadi.domain.model.MauzaBoundaryEntity
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity
import pk.gop.pulse.katchiAbadi.domain.model.NotAtHomeSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.OfficerEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelActivityLogEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.PestTypeEntity
import pk.gop.pulse.katchiAbadi.domain.model.SowingPersonEntity
import pk.gop.pulse.katchiAbadi.domain.model.StatusConverter
import pk.gop.pulse.katchiAbadi.domain.model.SurveyEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyImage
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPersonEntity
import pk.gop.pulse.katchiAbadi.domain.model.TaskEntity
import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyLogEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelDeclineEntity
import pk.gop.pulse.katchiAbadi.domain.model.ZoneEntity
import pk.gop.pulse.katchiAbadi.domain.model.DivisionEntity
import pk.gop.pulse.katchiAbadi.domain.model.SectionEntity
import pk.gop.pulse.katchiAbadi.domain.model.FarmEntity
import pk.gop.pulse.katchiAbadi.domain.model.BlockEntity
import pk.gop.pulse.katchiAbadi.domain.model.PlotEntity
import pk.gop.pulse.katchiAbadi.domain.model.LessorEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyLessorEntity
import pk.gop.pulse.katchiAbadi.domain.model.IrrigationSourceEntity
import pk.gop.pulse.katchiAbadi.domain.model.PropertyTypeEntity
import pk.gop.pulse.katchiAbadi.domain.model.AoiBoundaryEntity

@Database(
    entities = [NewSurveyNewEntity::class, SurveyPersonEntity::class, SurveyImage::class, ParcelEntity::class, KachiAbadiEntity::class, SurveyEntity::class, SurveyFormEntity::class, TempSurveyFormEntity::class, TempSurveyLogEntity::class, NotAtHomeSurveyFormEntity::class, ActiveParcelEntity::class, TaskEntity::class, CropEntity::class, CropTypeEntity::class, CropVarietyEntity::class,
        SowingPersonEntity::class,
        IssueTypeEntity::class,
        PestTypeEntity::class,
        DiseaseTypeEntity::class,
        OfficerEntity::class,
        JKGrowerEntity::class,
        ParcelDeclineEntity::class,
        MauzaBoundaryEntity::class,
        ParcelActivityLogEntity::class,
        // ---- Corporate Farms ----
        ZoneEntity::class,
        DivisionEntity::class,
        SectionEntity::class,
        FarmEntity::class,
        BlockEntity::class,
        PlotEntity::class,
        LessorEntity::class,
        SurveyLessorEntity::class,
        IrrigationSourceEntity::class,
        PropertyTypeEntity::class,
        AoiBoundaryEntity::class],
    version = 29,
    exportSchema = true
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
    abstract fun cropDao(): CropDao
    abstract fun cropTypeDao(): CropTypeDao
    abstract fun cropVarietyDao(): CropVarietyDao
    abstract fun sowingPersonDao(): SowingPersonDao
    abstract fun issueTypeDao(): IssueTypeDao
    abstract fun pestTypeDao(): PestTypeDao
    abstract fun diseaseTypeDao(): DiseaseTypeDao
    abstract fun officerDao(): OfficerDao
    abstract fun jkGrowerDao(): JKGrowerDao
    abstract fun parcelDeclineDao(): ParcelDeclineDao
    abstract fun mauzaBoundaryDao(): MauzaBoundaryDao
    abstract fun parcelActivityLogDao(): ParcelActivityLogDao

    // ---- Corporate Farms ----
    abstract fun zoneDao(): ZoneDao
    abstract fun divisionDao(): DivisionDao
    abstract fun sectionDao(): SectionDao
    abstract fun farmDao(): FarmDao
    abstract fun blockDao(): BlockDao
    abstract fun plotDao(): PlotDao
    abstract fun lessorDao(): LessorDao
    abstract fun surveyLessorDao(): SurveyLessorDao
    abstract fun irrigationSourceDao(): IrrigationSourceDao
    abstract fun propertyTypeDao(): PropertyTypeDao
    abstract fun aoiBoundaryDao(): AoiBoundaryDao

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
                    .addMigrations(migration9to10)
                    .addMigrations(migration10to11)
                    .addMigrations(migration11to12)
                    .addMigrations(migration12to13)
                    .addMigrations(migration13to14)
                    .addMigrations(migration14to15)
                    .addMigrations(migration15to16)
                    .addMigrations(migration16to17)
                    .addMigrations(migration17to18)
                    .addMigrations(migration18to19)
                    .addMigrations(migration19to20)
                    .addMigrations(migration20to21)
                    .addMigrations(migration21to22)
                    .addMigrations(migration22to23)
                    .addMigrations(migration23to24)
                    .addMigrations(migration24to25)
                    .addMigrations(migration25to26)
                    .addMigrations(migration26to27)
                    .addMigrations(migration27to28)
                    .addMigrations(migration28to29)
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
        db.execSQL(
            """
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
        """
        )
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

val migration9to10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add any migration that was supposed to be in version 10
        // If version 10 doesn't have changes, you can leave this empty
    }
}

val migration10to11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE survey_images ADD COLUMN latitude REAL")
        database.execSQL("ALTER TABLE survey_images ADD COLUMN longitude REAL")
        database.execSQL("ALTER TABLE survey_images ADD COLUMN timestamp INTEGER")
        database.execSQL("ALTER TABLE survey_images ADD COLUMN locationAddress TEXT")
    }
}

val migration11to12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE survey_images ADD COLUMN bearing REAL")
    }
}

val migration12to13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE survey_persons ADD COLUMN address TEXT NOT NULL DEFAULT ''")
    }
}
val migration13to14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE new_surveys ADD COLUMN address TEXT NOT NULL DEFAULT ''")
    }
}

val migration14to15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS sowing_persons (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                surveyId INTEGER NOT NULL,
                name TEXT NOT NULL,
                cnic TEXT NOT NULL,
                growerCode TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())

        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_sowing_persons_surveyId ON sowing_persons(surveyId)"
        )
    }
}

val migration15to16 = object : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE new_surveys ADD COLUMN sowingStatus TEXT NOT NULL DEFAULT 'No'")
        database.execSQL("ALTER TABLE new_surveys ADD COLUMN sowingDate TEXT")
    }
}
val migration16to17 = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("     CREATE TABLE IF NOT EXISTS `officers` (\n" +
                "           `id` INTEGER NOT NULL,\n" +
                "           `fullName` TEXT,\n" +
                "           `cnic` TEXT,\n" +
                "           `roleName` TEXT,\n" +
                "           `cachedAt` INTEGER NOT NULL,\n" +
                "           PRIMARY KEY(`id`)\n" +
                "       );")
    }
}
val migration17to18 = object : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE new_surveys ADD COLUMN farmerProfilePath TEXT")
    }
}

val migration18to19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE active_parcels ADD COLUMN growerCodes TEXT")
    }
}
val migration19to20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE new_surveys ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val migration20to21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS parcel_declines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                declineGuid TEXT NOT NULL,
                mauzaId INTEGER NOT NULL,
                mauzaName TEXT NOT NULL,
                areaName TEXT NOT NULL,
                serverParcelId INTEGER,
                parcelNo TEXT NOT NULL,
                subParcelNo TEXT NOT NULL,
                parcelStatus TEXT NOT NULL,
                khewatInfo TEXT NOT NULL,
                remarks TEXT NOT NULL,
                geomWKT TEXT,
                centroid TEXT,
                calculatedArea REAL,
                latitude REAL,
                longitude REAL,
                bearing REAL,
                pic1Path TEXT NOT NULL,
                pic2Path TEXT NOT NULL,
                pic3Path TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                isUploaded INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

// Versions 22, 23 and 24 were once bumped with no schema change, so those builds
// ended their migration chain at 20to21. Field devices carry user_version = 24
// while their tables are still schema-21 content. These three no-op bridges fill
// those empty steps so the 21-to-24 path stays connected and destructive fallback never fires.
val migration21to22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // No schema change - the version was only bumped.
    }
}

val migration22to23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // No schema change - the version was only bumped.
    }
}

val migration23to24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // No schema change - the version was only bumped.
    }
}

val migration24to25 = object : Migration(24, 25) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN varietyValidationStatus TEXT")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN varietyOldLocal TEXT")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN varietyNewLocal TEXT")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN varietyValidationRemarks TEXT")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN varietyValidationLat REAL")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN varietyValidationLng REAL")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN varietyValidatedAt TEXT")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN varietyValidationUploaded INTEGER NOT NULL DEFAULT 1")
        database.execSQL("ALTER TABLE active_parcels ADD COLUMN variety TEXT")
    }
}


val migration25to26 = object : Migration(25, 26) {
      override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("""CREATE TABLE IF NOT EXISTS mauza_boundary(
              mauzaId INTEGER NOT NULL PRIMARY KEY,
              mauzaName TEXT NOT NULL,
              groupId INTEGER, unitId INTEGER, zoneId INTEGER, circleId INTEGER,
              area TEXT, geomWKT TEXT NOT NULL, geomType TEXT,
              downloadedAt INTEGER NOT NULL)""")
          db.execSQL("ALTER TABLE active_parcels ADD COLUMN isLocallyDrawn INTEGER NOT NULL DEFAULT 0")
      }
  }

val migration26to27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS parcel_activity_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                parcelId INTEGER NOT NULL,
                parcelNo TEXT NOT NULL,
                subParcelNo TEXT NOT NULL,
                mauzaId INTEGER NOT NULL,
                areaName TEXT NOT NULL,
                action TEXT NOT NULL,
                status TEXT NOT NULL,
                details TEXT NOT NULL,
                relatedParcelIds TEXT NOT NULL,
                surveyPkId INTEGER,
                userId INTEGER NOT NULL,
                userName TEXT NOT NULL,
                lat REAL,
                lng REAL,
                appVersion TEXT NOT NULL,
                deviceInfo TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                isUploaded INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_parcel_activity_log_parcelId ON parcel_activity_log(parcelId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_parcel_activity_log_mauzaId ON parcel_activity_log(mauzaId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_parcel_activity_log_isUploaded ON parcel_activity_log(isUploaded)")
    }
}

val migration27to28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Corporate Farms data layer.
        // This DDL comes from Room's own exported schema (app/schemas/.../28.json),
        // not hand-written - otherwise TableInfo validation would fail on upgrade.

        // Farm hierarchy: zones -> divisions -> sections -> farms -> blocks -> plots.
        // Parents are created first because every child table holds an FK on them.
        db.execSQL("CREATE TABLE IF NOT EXISTS `zones` (`zoneId` INTEGER NOT NULL, `zoneName` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `lastSynced` INTEGER NOT NULL, PRIMARY KEY(`zoneId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `divisions` (`divisionId` INTEGER NOT NULL, `zoneId` INTEGER NOT NULL, `divisionName` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `lastSynced` INTEGER NOT NULL, PRIMARY KEY(`divisionId`), FOREIGN KEY(`zoneId`) REFERENCES `zones`(`zoneId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE TABLE IF NOT EXISTS `sections` (`sectionId` INTEGER NOT NULL, `divisionId` INTEGER NOT NULL, `sectionName` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `lastSynced` INTEGER NOT NULL, PRIMARY KEY(`sectionId`), FOREIGN KEY(`divisionId`) REFERENCES `divisions`(`divisionId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE TABLE IF NOT EXISTS `farms` (`farmId` INTEGER NOT NULL, `sectionId` INTEGER NOT NULL, `farmName` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `lastSynced` INTEGER NOT NULL, PRIMARY KEY(`farmId`), FOREIGN KEY(`sectionId`) REFERENCES `sections`(`sectionId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE TABLE IF NOT EXISTS `blocks` (`blockId` INTEGER NOT NULL, `farmId` INTEGER NOT NULL, `blockName` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `lastSynced` INTEGER NOT NULL, PRIMARY KEY(`blockId`), FOREIGN KEY(`farmId`) REFERENCES `farms`(`farmId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE TABLE IF NOT EXISTS `plots` (`plotId` INTEGER NOT NULL, `blockId` INTEGER NOT NULL, `plotName` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `lastSynced` INTEGER NOT NULL, PRIMARY KEY(`plotId`), FOREIGN KEY(`blockId`) REFERENCES `blocks`(`blockId`) ON UPDATE NO ACTION ON DELETE CASCADE )")

        // Lessor (lease) - master list plus the per-survey selection
        db.execSQL("CREATE TABLE IF NOT EXISTS `lessors` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `code` TEXT, `zoneId` INTEGER, `isDeleted` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `new_survey_lessors` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `surveyId` INTEGER NOT NULL, `parcelId` INTEGER NOT NULL, `parcelNo` TEXT NOT NULL, `subParcelNo` TEXT NOT NULL, `lessorId` INTEGER NOT NULL, `lessorName` TEXT NOT NULL, `lessorCode` TEXT NOT NULL, `lessorType` TEXT NOT NULL, `isSynced` INTEGER NOT NULL)")

        // Lookups
        db.execSQL("CREATE TABLE IF NOT EXISTS `irrigation_source` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `value` TEXT NOT NULL, `isSynced` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `property_types` (`value` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`value`))")

        // AOI / tehsil boundaries (these replace mauza + area in the corporate flow)
        db.execSQL("CREATE TABLE IF NOT EXISTS `aoi_boundaries` (`localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tehsil` TEXT NOT NULL, `theId` TEXT NOT NULL, `district` TEXT NOT NULL, `area` REAL NOT NULL, `geomWKT` TEXT NOT NULL, `downloadedAt` INTEGER NOT NULL)")
    }
}

val migration28to29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // The Corporate Farms form also captures hierarchy and irrigation.
        // All nullable - they stay empty for Katchi Abadi surveys.
        db.execSQL("ALTER TABLE new_surveys ADD COLUMN zone TEXT")
        db.execSQL("ALTER TABLE new_surveys ADD COLUMN division TEXT")
        db.execSQL("ALTER TABLE new_surveys ADD COLUMN section TEXT")
        db.execSQL("ALTER TABLE new_surveys ADD COLUMN farm TEXT")
        db.execSQL("ALTER TABLE new_surveys ADD COLUMN block TEXT")
        db.execSQL("ALTER TABLE new_surveys ADD COLUMN plot TEXT")
        db.execSQL("ALTER TABLE new_surveys ADD COLUMN irrigationSource TEXT")
        db.execSQL("ALTER TABLE new_surveys ADD COLUMN irrigationSourceQuantity TEXT")
    }
}
