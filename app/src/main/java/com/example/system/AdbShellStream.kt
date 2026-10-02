package com.example.system

import io.github.muntashirakon.adb.AdbStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class AdbShellStream(private val adbStream: AdbStream) {

    private val rawOut: OutputStream = adbStream.openOutputStream()
    private val queue = LinkedBlockingQueue<ByteArray?>()
    private val exitLatch = CountDownLatch(1)
    private val writeLock = Any()

    @Volatile
    private var exitCodeValue: Int = -1

    @Volatile
    private var closedFlag: Boolean = false

    init {
        val worker = Thread {
            try {
                val rawIn: InputStream = adbStream.openInputStream()
                val header = ByteArray(5)
                while (true) {
                    if (!readFully(rawIn, header)) break
                    val id = header[0].toInt() and 0xff
                    val length = (header[1].toInt() and 0xff) or
                        ((header[2].toInt() and 0xff) shl 8) or
                        ((header[3].toInt() and 0xff) shl 16) or
                        ((header[4].toInt() and 0xff) shl 24)
                    val payload = ByteArray(if (length > 0) length else 0)
                    if (length > 0 && !readFully(rawIn, payload)) break
                    when (id) {
                        1, 2 -> if (payload.isNotEmpty()) queue.put(payload)
                        3 -> {
                            if (payload.isNotEmpty()) exitCodeValue = payload[0].toInt() and 0xff
                            break
                        }
                    }
                }
            } catch (_: Throwable) {
            } finally {
                closedFlag = true
                exitLatch.countDown()
                runCatching { queue.put(EOF) }
            }
        }
        worker.isDaemon = true
        worker.name = "ins-adb-shell-reader"
        worker.start()
    }

    val inputStream: InputStream = object : InputStream() {
        private var current: ByteArray? = null
        private var position: Int = 0

        override fun read(): Int {
            if (!ensureChunk()) return -1
            val chunk = current ?: return -1
            return chunk[position++].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (!ensureChunk()) return -1
            val chunk = current ?: return -1
            val count = minOf(length, chunk.size - position)
            System.arraycopy(chunk, position, buffer, offset, count)
            position += count
            if (position >= chunk.size) current = null
            return count
        }

        override fun available(): Int {
            val chunk = current ?: return 0
            return chunk.size - position
        }

        private fun ensureChunk(): Boolean {
            while (current == null) {
                val chunk = try {
                    queue.take()
                } catch (_: InterruptedException) {
                    return false
                }
                if (chunk == null) return false
                if (chunk.isEmpty()) continue
                current = chunk
                position = 0
            }
            return true
        }
    }

    val outputStream: OutputStream = object : OutputStream() {
        override fun write(byte: Int) {
            write(byteArrayOf(byte.toByte()), 0, 1)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (length <= 0) return
            synchronized(writeLock) {
                if (closedFlag) return
                val header = ByteArray(5)
                header[0] = 0
                header[1] = (length and 0xff).toByte()
                header[2] = ((length shr 8) and 0xff).toByte()
                header[3] = ((length shr 16) and 0xff).toByte()
                header[4] = ((length shr 24) and 0xff).toByte()
                rawOut.write(header)
                rawOut.write(buffer, offset, length)
                rawOut.flush()
            }
        }

        override fun flush() {
            synchronized(writeLock) {
                runCatching { rawOut.flush() }
            }
        }
    }

    fun awaitExit(): Int {
        exitLatch.await()
        return exitCodeValue
    }

    fun awaitExitOrTimeout(timeoutMs: Long): Boolean =
        exitLatch.await(timeoutMs, TimeUnit.MILLISECONDS)

    fun awaitExit(timeout: Long, unit: TimeUnit): Int {
        val done = exitLatch.await(timeout, unit)
        return if (done) exitCodeValue else -1
    }

    fun exitCode(): Int = exitCodeValue

    fun isClosed(): Boolean = closedFlag

    fun close() {
        closedFlag = true
        runCatching { adbStream.close() }
        runCatching { rawOut.close() }
        exitLatch.countDown()
        runCatching { queue.put(EOF) }
    }

    companion object {
        private val EOF: ByteArray? = null

        private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
            var offset = 0
            while (offset < buffer.size) {
                val read = input.read(buffer, offset, buffer.size - offset)
                if (read < 0) return false
                offset += read
            }
            return true
        }
    }
}
