package com.example.nfcappblocker

object NfcUtils {
    fun bytesToHexString(src: ByteArray?): String {
        if (src == null || src.isEmpty()) return ""
        val stringBuilder = StringBuilder("")
        for (i in src.indices) {
            val v = src[i].toInt() and 0xFF
            val hv = Integer.toHexString(v)
            if (hv.length < 2) stringBuilder.append(0)
            stringBuilder.append(hv)
        }
        return stringBuilder.toString().uppercase()
    }
}
