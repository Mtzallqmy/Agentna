package com.mtzallqmy.agentna.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class NetworkSafetyTest {
    @Test fun blocksPrivateAndLocalIpv4() {
        listOf("127.0.0.1", "10.0.0.1", "172.16.4.2", "192.168.1.10", "169.254.2.1", "100.64.0.1").forEach {
            assertTrue("Expected blocked: $it", NetworkSafety.isForbiddenAddress(InetAddress.getByName(it)))
        }
    }

    @Test fun blocksIpv6UniqueLocalAndLoopback() {
        listOf("::1", "fc00::1", "fd12:3456::1", "fe80::1").forEach {
            assertTrue("Expected blocked: $it", NetworkSafety.isForbiddenAddress(InetAddress.getByName(it)))
        }
    }

    @Test fun permitsPublicAddresses() {
        assertFalse(NetworkSafety.isForbiddenAddress(InetAddress.getByName("8.8.8.8")))
        assertFalse(NetworkSafety.isForbiddenAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }
}
