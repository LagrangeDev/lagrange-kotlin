package org.lagrange.dev.utils.ext

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

internal suspend fun InputStream.calculateMD5(): ByteArray = calculate(this@calculateMD5, MessageDigest.getInstance("MD5"))

internal suspend fun InputStream.calculateSHA1(): ByteArray = calculate(this@calculateSHA1, MessageDigest.getInstance("SHA-1"))

internal fun InputStream.seekBeginning() {
    if (this is FileInputStream) {
        channel.position(0)
    } else { 
        reset()
    }
}

private suspend fun calculate(stream: InputStream, digest: MessageDigest) = withContext(Dispatchers.IO) {
    stream.seekBeginning()
    
    val buffer = ByteArray(8192)
    var read: Int
    while (stream.read(buffer).also { read = it } > 0) {
        digest.update(buffer, 0, read)
    }
    
    stream.seekBeginning()
    digest.digest()
}