package pk.gop.pulse.katchiAbadi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import pk.gop.pulse.katchiAbadi.domain.model.BlockEntity

// ============================================
// BLOCK DAO
// ============================================
@Dao
interface BlockDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: BlockEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocks(blocks: List<BlockEntity>)

    @Query("SELECT * FROM blocks WHERE farmId = :farmId")
    suspend fun getBlocksByFarm(farmId: Long): List<BlockEntity>

    @Query("SELECT * FROM blocks WHERE blockId = :blockId")
    suspend fun getBlockById(blockId: Long): BlockEntity?

    @Query("DELETE FROM blocks")
    suspend fun deleteAllBlocks()

    @Update
    suspend fun updateBlock(block: BlockEntity)

    @Query("SELECT * FROM blocks")
    suspend fun getAllBlocks(): List<BlockEntity>
}