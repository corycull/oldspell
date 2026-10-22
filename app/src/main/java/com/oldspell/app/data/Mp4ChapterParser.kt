package com.oldspell.app.data

import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Minimal MP4 box reader that pulls chapter markers out of a file without loading it into memory.
 * Supports both common layouts:
 *  - a QuickTime chapter text track (referenced through tref/chap), written by most m4b tools,
 *  - the Nero "chpl" box in moov/udta.
 * Returns (startMs, title) pairs, unsorted.
 */
object Mp4ChapterParser {
    private const val MAX_LEAF = 16L * 1024 * 1024

    private class Box(val type: String, val start: Long, val end: Long) {
        val size: Long get() = end - start
    }

    private class Trak {
        var id = -1
        var chapRefs = emptyList<Int>()
        var timescale = 0L
        var stbl: Box? = null
    }

    fun parse(ch: FileChannel): List<Pair<Long, String>> {
        val top = boxes(ch, 0, ch.size())
        val moov = top.firstOrNull { it.type == "moov" } ?: return emptyList()
        val moovKids = boxes(ch, moov.start, moov.end)

        val traks = moovKids.filter { it.type == "trak" }.map { parseTrak(ch, it) }
        val chapterIds = traks.flatMap { it.chapRefs }.toSet()
        val chapterTrak = traks.firstOrNull { it.id in chapterIds && it.stbl != null && it.timescale > 0 }
        if (chapterTrak != null) {
            val qt = try { readTextTrack(ch, chapterTrak) } catch (e: Exception) { emptyList() }
            if (qt.size > 1) return qt
        }

        val udta = moovKids.firstOrNull { it.type == "udta" } ?: return emptyList()
        val chpl = boxes(ch, udta.start, udta.end).firstOrNull { it.type == "chpl" } ?: return emptyList()
        return try { readChpl(read(ch, chpl)) } catch (e: Exception) { emptyList() }
    }

    private fun parseTrak(ch: FileChannel, trak: Box): Trak {
        val t = Trak()
        for (b in boxes(ch, trak.start, trak.end)) {
            when (b.type) {
                "tkhd" -> {
                    val buf = read(ch, b)
                    val version = buf.get(0).toInt()
                    buf.position(if (version == 1) 4 + 8 + 8 else 4 + 4 + 4)
                    t.id = buf.int
                }
                "tref" -> {
                    boxes(ch, b.start, b.end).firstOrNull { it.type == "chap" }?.let { chap ->
                        val buf = read(ch, chap)
                        t.chapRefs = List(buf.remaining() / 4) { buf.int }
                    }
                }
                "mdia" -> for (m in boxes(ch, b.start, b.end)) {
                    when (m.type) {
                        "mdhd" -> {
                            val buf = read(ch, m)
                            val version = buf.get(0).toInt()
                            buf.position(if (version == 1) 4 + 8 + 8 else 4 + 4 + 4)
                            t.timescale = buf.int.toLong() and 0xffffffffL
                        }
                        "minf" -> t.stbl = boxes(ch, m.start, m.end).firstOrNull { it.type == "stbl" }
                    }
                }
            }
        }
        return t
    }

    private fun readTextTrack(ch: FileChannel, trak: Trak): List<Pair<Long, String>> {
        val stbl = trak.stbl ?: return emptyList()
        val kids = boxes(ch, stbl.start, stbl.end).associateBy { it.type }

        // Sample durations
        val stts = read(ch, kids["stts"] ?: return emptyList())
        stts.position(4)
        val sttsCount = stts.int
        val sampleTimes = ArrayList<Long>()
        var t = 0L
        repeat(sttsCount) {
            val n = stts.int.toLong() and 0xffffffffL
            val delta = stts.int.toLong() and 0xffffffffL
            for (k in 0 until n) {
                sampleTimes += t
                t += delta
            }
        }

        // Sample sizes
        val stsz = read(ch, kids["stsz"] ?: return emptyList())
        stsz.position(4)
        val fixedSize = stsz.int
        val sampleCount = stsz.int
        val sizes = IntArray(sampleCount) { if (fixedSize != 0) fixedSize else stsz.int }

        // Chunk offsets
        val chunkOffsets: LongArray = when {
            kids["stco"] != null -> read(ch, kids["stco"]!!).let { b ->
                b.position(4); LongArray(b.int) { b.int.toLong() and 0xffffffffL }
            }
            kids["co64"] != null -> read(ch, kids["co64"]!!).let { b ->
                b.position(4); LongArray(b.int) { b.long }
            }
            else -> return emptyList()
        }

        // Samples per chunk
        val stsc = read(ch, kids["stsc"] ?: return emptyList())
        stsc.position(4)
        val stscEntries = List(stsc.int) {
            val first = stsc.int
            val perChunk = stsc.int
            stsc.int // sample description index
            first to perChunk
        }

        val offsets = LongArray(sampleCount)
        var s = 0
        for (ci in chunkOffsets.indices) {
            val chunkNumber = ci + 1
            var perChunk = 0
            for ((first, n) in stscEntries) if (first <= chunkNumber) perChunk = n else break
            var off = chunkOffsets[ci]
            repeat(perChunk) {
                if (s < sampleCount) {
                    offsets[s] = off
                    off += sizes[s]
                    s++
                }
            }
        }

        val out = ArrayList<Pair<Long, String>>()
        for (i in 0 until minOf(s, sampleTimes.size)) {
            val size = sizes[i]
            if (size < 2) continue
            val buf = ByteBuffer.allocate(minOf(size, 4096))
            readFully(ch, buf, offsets[i])
            buf.flip()
            val len = (buf.short.toInt() and 0xffff).coerceAtMost(buf.remaining())
            val bytes = ByteArray(len).also { buf.get(it) }
            val text = if (len >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                String(bytes, 2, len - 2, Charsets.UTF_16BE)
            } else String(bytes, Charsets.UTF_8)
            out += (sampleTimes[i] * 1000 / trak.timescale) to text
        }
        return out
    }

    private fun readChpl(buf: ByteBuffer): List<Pair<Long, String>> {
        val version = buf.get().toInt()
        buf.position(buf.position() + 3) // flags
        if (version != 0) buf.int // reserved
        val count = buf.get().toInt() and 0xff
        val out = ArrayList<Pair<Long, String>>()
        repeat(count) {
            if (buf.remaining() < 9) return out
            val start100ns = buf.long
            val len = buf.get().toInt() and 0xff
            if (buf.remaining() < len) return out
            val bytes = ByteArray(len).also { buf.get(it) }
            out += (start100ns / 10_000) to String(bytes, Charsets.UTF_8)
        }
        return out
    }

    private fun boxes(ch: FileChannel, from: Long, to: Long): List<Box> {
        val out = ArrayList<Box>()
        val hdr = ByteBuffer.allocate(8)
        var pos = from
        while (pos + 8 <= to) {
            hdr.clear()
            readFully(ch, hdr, pos)
            hdr.flip()
            var size = hdr.int.toLong() and 0xffffffffL
            val type = String(ByteArray(4).also { hdr.get(it) }, Charsets.ISO_8859_1)
            var headerLen = 8L
            if (size == 1L) {
                hdr.clear()
                readFully(ch, hdr, pos + 8)
                hdr.flip()
                size = hdr.long
                headerLen = 16L
            } else if (size == 0L) {
                size = to - pos
            }
            if (size < headerLen || pos + size > to) break
            out += Box(type, pos + headerLen, pos + size)
            pos += size
        }
        return out
    }

    private fun read(ch: FileChannel, box: Box): ByteBuffer {
        require(box.size in 0..MAX_LEAF) { "box too large" }
        val buf = ByteBuffer.allocate(box.size.toInt())
        readFully(ch, buf, box.start)
        buf.flip()
        return buf
    }

    private fun readFully(ch: FileChannel, buf: ByteBuffer, position: Long) {
        var p = position
        while (buf.hasRemaining()) {
            val n = ch.read(buf, p)
            if (n < 0) throw EOFException()
            p += n
        }
    }
}
