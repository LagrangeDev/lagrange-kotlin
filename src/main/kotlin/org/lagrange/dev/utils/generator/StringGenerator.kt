package org.lagrange.dev.utils.generator

object StringGenerator {
    private const val HEX = "1234567890abcdef"
    
    fun generateTrace(): String {
        val sb = StringBuilder(55)
        
        sb.append(0.toString(16)) // 2 chars
        sb.append('-') // 1 char
        
        for (i in 0 until 32) sb.append(HEX.random()) // 32 chars
        sb.append('-') // 1 char
        
        for (i in 0 until 16) sb.append(HEX.random()) // 16 chars
        sb.append('-') // 1 char
        
        sb.append(1.toString(16)) // 2 chars

        return sb.toString()
    }
}