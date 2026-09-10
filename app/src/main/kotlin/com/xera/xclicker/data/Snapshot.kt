package com.xera.xclicker.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import com.xera.xclicker.util.format

@Entity(
    tableName = "snapshot",
)
@Serializable
data class Snapshot(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long,

    @ColumnInfo(name = "app_id") val appId: String,
    @ColumnInfo(name = "activity_id") val activityId: String?,

    @ColumnInfo(name = "screen_height") val screenHeight: Int,
    @ColumnInfo(name = "screen_width") val screenWidth: Int,
    @ColumnInfo(name = "is_landscape") val isLandscape: Boolean,

    @ColumnInfo(name = "github_asset_id") val githubAssetId: Int? = null,

) {

    val date by lazy { id.format("MM-dd HH:mm:ss") }


    @Dao
    interface SnapshotDao {
        @Update
        suspend fun update(vararg objects: Snapshot): Int

        @Insert
        suspend fun insert(vararg users: Snapshot): List<Long>

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insertOrIgnore(vararg users: Snapshot): List<Long>

        @Query("DELETE FROM snapshot")
        suspend fun deleteAll()

        @Delete
        suspend fun delete(vararg users: Snapshot): Int

        @Query("SELECT * FROM snapshot ORDER BY id DESC")
        fun query(): Flow<List<Snapshot>>

        @Query("UPDATE snapshot SET github_asset_id=null WHERE id = :id")
        suspend fun deleteGithubAssetId(id: Long)

        @Query("SELECT COUNT(*) FROM snapshot")
        fun count(): Flow<Int>
    }
}





