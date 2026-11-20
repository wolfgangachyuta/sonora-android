package com.sonora.player

import android.content.Context
import androidx.room.*

@Entity(tableName = "sonora_favorites")
data class SonoraFavorite(@PrimaryKey val trackId: String, val addedAt: Long = System.currentTimeMillis())

@Entity(tableName = "sonora_playlists")
data class SonoraPlaylist(@PrimaryKey val id: String, val name: String, val createdAt: Long = System.currentTimeMillis())

@Entity(
    tableName = "sonora_playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [ForeignKey(entity = SonoraPlaylist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("playlistId")]
)
data class SonoraPlaylistTrack(val playlistId: String, val trackId: String, val position: Int)

@Entity(tableName = "sonora_play_history", indices = [Index("trackId"), Index("playedAt")])
data class SonoraPlayEvent(@PrimaryKey(autoGenerate = true) val id: Long = 0, val trackId: String, val playedAt: Long = System.currentTimeMillis())

@Entity(tableName = "sonora_queue_snapshot")
data class SonoraQueueSnapshot(@PrimaryKey val id: Int = 1, val trackIds: String, val currentIndex: Int, val positionMs: Long, val updatedAt: Long = System.currentTimeMillis())

@Dao
interface SonoraDao {
    @Query("SELECT trackId FROM sonora_favorites ORDER BY addedAt DESC")
    suspend fun favoriteIds(): List<String>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFavorite(item: SonoraFavorite)
    @Query("DELETE FROM sonora_favorites WHERE trackId = :trackId")
    suspend fun removeFavorite(trackId: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFavorites(items: List<SonoraFavorite>)

    @Query("SELECT * FROM sonora_playlists ORDER BY createdAt")
    suspend fun playlists(): List<SonoraPlaylist>
    @Query("DELETE FROM sonora_playlists")
    suspend fun clearPlaylists()
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaylist(item: SonoraPlaylist)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaylists(items: List<SonoraPlaylist>)
    @Query("DELETE FROM sonora_playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)
    @Query("SELECT trackId FROM sonora_playlist_tracks WHERE playlistId = :id ORDER BY position")
    suspend fun playlistTrackIds(id: String): List<String>
    @Query("DELETE FROM sonora_playlist_tracks WHERE playlistId = :id")
    suspend fun clearPlaylistTracks(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaylistTracks(items: List<SonoraPlaylistTrack>)
    @Insert
    suspend fun recordPlay(event: SonoraPlayEvent)
    @Query("SELECT * FROM sonora_play_history ORDER BY playedAt DESC LIMIT 500")
    suspend fun recentHistory(): List<SonoraPlayEvent>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveQueue(snapshot: SonoraQueueSnapshot)
    @Query("SELECT * FROM sonora_queue_snapshot WHERE id = 1 LIMIT 1")
    suspend fun queueSnapshot(): SonoraQueueSnapshot?
}

@Database(
    entities = [SonoraFavorite::class, SonoraPlaylist::class, SonoraPlaylistTrack::class, SonoraPlayEvent::class, SonoraQueueSnapshot::class],
    version = 1,
    exportSchema = false
)
abstract class SonoraDatabase : RoomDatabase() {
    abstract fun dao(): SonoraDao
    companion object {
        @Volatile private var instance: SonoraDatabase? = null
        fun get(context: Context): SonoraDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, SonoraDatabase::class.java, "sonora_library.db").build().also { instance = it }
        }
    }
}
