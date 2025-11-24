package pk.gop.pulse.katchiAbadi.data.local



import androidx.room.*
import pk.gop.pulse.katchiAbadi.domain.model.CropEntity
import pk.gop.pulse.katchiAbadi.domain.model.CropTypeEntity
import pk.gop.pulse.katchiAbadi.domain.model.CropVarietyEntity


@Dao
interface CropDao {
    @Query("SELECT * FROM crops ORDER BY value ASC")
    suspend fun getAllCrops(): List<CropEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrop(crop: CropEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllCrops(crops: List<CropEntity>)

    @Query("DELETE FROM crops")
    suspend fun deleteAllCrops()

    @Query("SELECT COUNT(*) FROM crops")
    suspend fun getCropCount(): Int

    @Query("SELECT * FROM crops WHERE LOWER(value) = LOWER(:value) LIMIT 1")
    suspend fun getCropByValue(value: String): CropEntity?
}

@Dao
interface CropTypeDao {
    @Query("SELECT * FROM crop_types ORDER BY value ASC")
    suspend fun getAllCropTypes(): List<CropTypeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCropType(cropType: CropTypeEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllCropTypes(cropTypes: List<CropTypeEntity>)

    @Query("DELETE FROM crop_types")
    suspend fun deleteAllCropTypes()

    @Query("SELECT COUNT(*) FROM crop_types")
    suspend fun getCropTypeCount(): Int

    @Query("SELECT * FROM crop_types WHERE LOWER(value) = LOWER(:value) LIMIT 1")
    suspend fun getCropTypeByValue(value: String): CropTypeEntity?
}

@Dao
interface CropVarietyDao {
    @Query("SELECT * FROM crop_varieties ORDER BY value ASC")
    suspend fun getAllVarieties(): List<CropVarietyEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVariety(variety: CropVarietyEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllVarieties(varieties: List<CropVarietyEntity>)

    @Query("DELETE FROM crop_varieties")
    suspend fun deleteAllVarieties()

    @Query("SELECT COUNT(*) FROM crop_varieties")
    suspend fun getVarietyCount(): Int

    @Query("SELECT * FROM crop_varieties WHERE LOWER(value) = LOWER(:value) LIMIT 1")
    suspend fun getVarietyByValue(value: String): CropVarietyEntity?

    @Query("SELECT * FROM crop_varieties WHERE isSynced = 0")
    suspend fun getUnsyncedVarieties(): List<CropVarietyEntity>

    @Update
    suspend fun updateVariety(variety: CropVarietyEntity)
}