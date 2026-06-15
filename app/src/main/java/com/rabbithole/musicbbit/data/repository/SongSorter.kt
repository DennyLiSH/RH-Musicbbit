package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.domain.model.Song
import java.math.BigInteger
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SongSorter @Inject constructor() {

    private val baseCollator: Collator = Collator.getInstance(Locale.getDefault()).apply {
        strength = Collator.PRIMARY
    }

    fun sort(songs: List<Song>): List<Song> {
        val collator = baseCollator.clone() as Collator
        return songs.sortedWith(buildComparator(collator))
    }

    private fun buildComparator(collator: Collator): Comparator<Song> =
        Comparator<Song> { s1, s2 -> compareNatural(sortKey(s1), sortKey(s2), collator) }
            .thenComparingLong { it.id }

    private fun sortKey(song: Song): String {
        val title = song.title.trim()
        return if (title.isNotEmpty()) title else fileNameWithoutExtension(song.path)
    }

    private fun fileNameWithoutExtension(path: String): String {
        val name = path.substringAfterLast('/')
        return name.substringBeforeLast('.', name)
    }

    private fun compareNatural(a: String, b: String, collator: Collator): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val aDigit = a[i].isDigit()
            val bDigit = b[j].isDigit()
            when {
                aDigit && bDigit -> {
                    val aStart = i
                    while (i < a.length && a[i].isDigit()) i++
                    val bStart = j
                    while (j < b.length && b[j].isDigit()) j++
                    val cmp = BigInteger(a.substring(aStart, i))
                        .compareTo(BigInteger(b.substring(bStart, j)))
                    if (cmp != 0) return cmp
                }
                aDigit != bDigit -> {
                    val cmp = collator.compare(a[i].toString(), b[j].toString())
                    if (cmp != 0) return cmp
                    i++
                    j++
                }
                else -> {
                    val aStart = i
                    while (i < a.length && !a[i].isDigit()) i++
                    val bStart = j
                    while (j < b.length && !b[j].isDigit()) j++
                    val cmp = collator.compare(a.substring(aStart, i), b.substring(bStart, j))
                    if (cmp != 0) return cmp
                }
            }
        }
        return a.length.compareTo(b.length)
    }
}
