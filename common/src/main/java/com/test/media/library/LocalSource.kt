package com.test.media.library

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import com.example.android.uamp.media.R
import com.test.media.extensions.albumArtUri
import com.test.media.extensions.displayIconUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class LocalSource(private val context: Context, private val source: Uri) : AbstractMusicSource() {

    private var catalog: List<MediaMetadataCompat> = emptyList()

    private val glide: RequestManager

    init {
        state = STATE_INITIALIZING
        glide = Glide.with(context)
    }

    override suspend fun load() {
        updateLocalCatalog()?.let { updatedCatalog ->
            catalog = updatedCatalog
            state = STATE_INITIALIZED
        } ?: run {
            catalog = emptyList()
            state = STATE_ERROR
        }
    }

    override fun iterator(): Iterator<MediaMetadataCompat> = catalog.iterator()

    private suspend fun updateLocalCatalog(): List<MediaMetadataCompat>? {
        return withContext(Dispatchers.IO) {
            val musicCat = try {
                fetchLocalMusicList()
            } catch (ioException: IOException) {
                return@withContext null
            }
            musicCat.music.map { song ->

                // Block on downloading artwork.
                val artFile = glide.applyDefaultRequestOptions(glideOptions)
                    .downloadOnly()
                    .load(song.image)
                    .submit(NOTIFICATION_LARGE_ICON_SIZE, NOTIFICATION_LARGE_ICON_SIZE)
                    .get()

                // Expose file via Local URI
                val artUri = artFile.asAlbumArtContentUri()

                MediaMetadataCompat.Builder()
                    .from(song)
                    .apply {
                        displayIconUri = artUri.toString() // Used by ExoPlayer and Notification
                        albumArtUri = artUri.toString()
                    }
                    .build()
            }.toList()
        }
    }


    fun fetchLocalMusicList(): LocalCatalog {
        // Initialize an empty mutable list of music
        val list: MutableList<LocalMusic> = mutableListOf()

        // Get the internal storage media store audio uri
        val uri: Uri = MediaStore.Audio.Media.INTERNAL_CONTENT_URI

        // IS_MUSIC : Non-zero if the audio file is music
        val selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0"

        // Sort the musics
        val sortOrder = MediaStore.Audio.Media.TITLE + " ASC"
        //val sortOrder = MediaStore.Audio.Media.TITLE + " DESC"

        // Query the external storage for music files
        val cursor: Cursor = context.contentResolver.query(
            uri, // Uri
            null, // Projection
            selection, // Selection
            null, // Selection arguments
            sortOrder // Sort order
        )

        // If query result is not empty
        if (cursor != null && cursor.moveToFirst()) {
            val id: Int = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val title: Int = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val album: Int = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            var artist: Int = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            var genre: Int = 0
            var source: Int = 0
            var trackNumber: Int = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
            var duration: Int = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)

            // Now loop through the music file
            do {
                val audioId: String = cursor.getString(id)
                val audioTitle: String = cursor.getString(title)
                val audioAlbum: String = cursor.getString(album)
                val audioArtist: String = cursor.getString(artist)
                val audioAlbumArt = ContentUris.withAppendedId(
                    Uri.parse("content://media/internal/audio/albumart"),
                    cursor.getColumnIndex(MediaStore.Audio.Media._ID).toLong() ?: -1
                )
                val audioTrackNumber: Long = cursor.getLong(trackNumber)
                val audioDuration: Long = cursor.getLong(duration)

                // Add the current music to the list
                list.add(
                    LocalMusic(
                        id = audioId,
                        title = audioTitle,
                        album = audioAlbum,
                        artist = audioArtist,
                        genre = "",
                        source = "",
                        image = audioAlbumArt,
                        trackNumber = audioTrackNumber,
                        totalTrackCount = 0,
                        duration = audioDuration,
                        site = ""
                    )
                )
            } while (cursor.moveToNext())
        }
        // Finally, return the music files list
        return LocalCatalog(list)
    }
}

class LocalCatalog(
    var music: List<LocalMusic> = ArrayList()
)


@Suppress("unused")
class LocalMusic(
    var id: String = "",
    var title: String = "",
    var album: String = "",
    var artist: String = "",
    var genre: String = "",
    var source: String = "",
    var image: Uri,
    var trackNumber: Long = 0,
    var totalTrackCount: Long = 0,
    var duration: Long = -1,
    var site: String = ""
)

private const val NOTIFICATION_LARGE_ICON_SIZE = 144 // px

private val glideOptions = RequestOptions()
    .fallback(R.drawable.default_art)
    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
