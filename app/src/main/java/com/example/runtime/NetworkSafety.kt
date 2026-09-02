package com.mtzallqmy.agentna.runtime

import java.net.InetAddress

internal object NetworkSafety {
    fun isForbiddenAddress(address: InetAddress): Boolean =
        address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress || isCarrierGradeNat(address) ||
            isIpv6UniqueLocal(address) || isIpv4MappedPrivate(address)

    private fun isIpv6UniqueLocal(address: InetAddress): Boolean {
        val bytes = address.address
        return bytes.size == 16 && ((bytes[0].toInt() and 0xfe) == 0xfc)
    }

    private fun isIpv4MappedPrivate(address: InetAddress): Boolean {
        val bytes = address.address
        if (bytes.size != 16) return false
        val mapped = bytes.sliceArray(0..9).all { it.toInt() == 0 } && bytes[10].toInt() == 0xff && bytes[11].toInt() == 0xff
        if (!mapped) return false
        val v4 = InetAddress.getByAddress(bytes.copyOfRange(12, 16))
        return v4.isAnyLocalAddress || v4.isLoopbackAddress || v4.isLinkLocalAddress || v4.isSiteLocalAddress || v4.isMulticastAddress || isCarrierGradeNat(v4)
    }

    private fun isCarrierGradeNat(address: InetAddress): Boolean {
        val bytes = address.address
        if (bytes.size != 4) return false
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        return first == 100 && second in 64..127
    }
}
