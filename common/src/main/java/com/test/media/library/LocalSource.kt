package com.test.media.library

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.android.uamp.media.R
import com.test.media.extensions.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

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


                MediaMetadataCompat.Builder()
                    .from(song)
                    .apply {
                        displayIconUri = song.image // Used by ExoPlayer and Notification
                        albumArtUri = song.image
                    }
                    .build()
            }.toList()
        }
    }


    private fun fetchLocalMusicList(): LocalCatalog {
        // Initialize an empty mutable list of music
        val list: MutableList<LocalMusic> = mutableListOf()

        // Get the external storage media store audio uri
        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

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
            var source: Int = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            var trackNumber: Int = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
            var duration: Int = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val albumArtId : Int = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)

            // Now loop through the music file
            do {
                val audioId: String = cursor.getString(id)
                val audioTitle: String = cursor.getString(title)
                val audioAlbum: String = cursor.getString(album)
                val audioArtist: String = cursor.getString(artist)
                val albumId = cursor.getLong(albumArtId)
                val audioAlbumArt = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId ?: -1
                ).toString()
                val albumSource = cursor.getString(source)
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
                        source = albumSource,
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

fun MediaMetadataCompat.Builder.from(localMusic: LocalMusic): MediaMetadataCompat.Builder {
    // The duration from the JSON is given in seconds, but the rest of the code works in
    // milliseconds. Here's where we convert to the proper units.
    val durationMs = TimeUnit.SECONDS.toMillis(localMusic.duration)

    id = localMusic.id
    title = localMusic.title
    artist = localMusic.artist
    album = localMusic.album
    duration = durationMs
    genre = localMusic.genre
    mediaUri = localMusic.source
    albumArtUri = localMusic.image
    trackNumber = localMusic.trackNumber
    trackCount = localMusic.totalTrackCount
    flag = MediaBrowserCompat.MediaItem.FLAG_PLAYABLE

    // To make things easier for *displaying* these, set the display properties as well.
    displayTitle = localMusic.title
    displaySubtitle = localMusic.artist
    displayDescription = localMusic.album
    displayIconUri = localMusic.image

    // Add downloadStatus to force the creation of an "extras" bundle in the resulting
    // MediaMetadataCompat object. This is needed to send accurate metadata to the
    // media session during updates.
    downloadStatus = MediaDescriptionCompat.STATUS_NOT_DOWNLOADED

    // Allow it to be used in the typical builder style.
    return this
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
    var image: String = "",
    var trackNumber: Long = 0,
    var totalTrackCount: Long = 0,
    var duration: Long = -1,
    var site: String = ""
)

private const val NOTIFICATION_LARGE_ICON_SIZE = 144 // px

private val glideOptions = RequestOptions()
    .fallback(R.drawable.default_art)
    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
