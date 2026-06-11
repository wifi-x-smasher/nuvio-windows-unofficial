package com.nuvio.app.core.network

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Reorders DNS results to prefer IPv4 first. Desktop copy of the Android helper.
 *
 * On Windows, OkHttp tries resolved addresses in order and does not race IPv4/IPv6
 * (no Happy Eyeballs in 4.12.0). Hosts that advertise broken/slow AAAA records would
 * otherwise stall on the IPv6 connect attempt until it times out before falling back
 * to IPv4 — producing the "long elapsed, then empty" pattern some scrapers hit. Putting
 * IPv4 first avoids that stall. Mirrors `androidMain`'s IPv4FirstDns exactly.
 */
class IPv4FirstDns(private val delegate: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        return addresses.sortedBy { if (it is Inet4Address) 0 else 1 }
    }
}
