package de.michelinside.glucodatahandler.common.tasks.yuwell.encryption

import android.util.Base64

class Base64Tools {
    init {
        throw UnsupportedOperationException("U can't instantiate Base64")
    }

    companion object {
        fun format(data: String?): String {
            if (data == null) {
                throw NullPointerException("NULL data")
            }
            return format(data.toByteArray())
        }

        fun format(bytes: ByteArray?): String {
            if (bytes == null) {
                throw NullPointerException("NULL bytes")
            }
            return Base64.encodeToString(bytes, 0)
        }

        fun parseByte(data: String?): ByteArray {
            if (data == null) {
                throw NullPointerException("NULL data")
            }
            return Base64.decode(data, 0)
        }

        fun parse(data: String?): String {
            if (data == null) {
                throw NullPointerException("NULL data")
            }
            return String(Base64.decode(data, 0))
        }
    }
}