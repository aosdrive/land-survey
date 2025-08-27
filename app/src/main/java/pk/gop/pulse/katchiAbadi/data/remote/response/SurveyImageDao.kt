package pk.gop.pulse.katchiAbadi.data.remote.response

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import pk.gop.pulse.katchiAbadi.domain.model.SurveyImage

@Dao
interface SurveyImageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: SurveyImage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: List<SurveyImage>)

    @Query("SELECT * FROM survey_images WHERE surveyId = :surveyId")
    suspend fun getImagesBySurvey(surveyId: Long): List<SurveyImage>

    // Alias to match repository code
    @Query("SELECT * FROM survey_images WHERE surveyId = :surveyId")
    suspend fun getImagesForSurvey(surveyId: Long): List<SurveyImage>

    @Query("DELETE FROM survey_images WHERE surveyId = :surveyId")
    suspend fun deleteImagesForSurvey(surveyId: Long)

    @Delete
    suspend fun deleteImage(image: SurveyImage)
}
