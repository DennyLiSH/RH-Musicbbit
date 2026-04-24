package com.rabbithole.musicbbit.domain.model

import android.os.Parcel
import android.os.Parcelable

/**
 * Domain model representing a song in the music library.
 */
data class Song(
    val id: Long,
    val path: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val dateAdded: Long,
    val coverUri: String?
) : Parcelable {
    constructor(parcel: Parcel) : this(
        id = parcel.readLong(),
        path = parcel.readString()!!,
        title = parcel.readString()!!,
        artist = parcel.readString(),
        album = parcel.readString(),
        durationMs = parcel.readLong(),
        dateAdded = parcel.readLong(),
        coverUri = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(path)
        parcel.writeString(title)
        parcel.writeString(artist)
        parcel.writeString(album)
        parcel.writeLong(durationMs)
        parcel.writeLong(dateAdded)
        parcel.writeString(coverUri)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Song> {
        override fun createFromParcel(parcel: Parcel): Song = Song(parcel)
        override fun newArray(size: Int): Array<Song?> = arrayOfNulls(size)
    }
}
