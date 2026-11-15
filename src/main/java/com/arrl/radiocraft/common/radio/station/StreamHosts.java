package com.arrl.radiocraft.common.radio.station;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

/**
 * Which links the server may fetch for an internet radio station (2026-11-14). The server downloads whatever a station's
 * owner types in, so only http and https, and (unless the server allows it) never an address on the server's own
 * machine or private network: loopback, link-local, private (10/8, 172.16/12, 192.168/16, 100.64/10, fc00::/7). Multicast
 * and the any-address are refused always. Every address the host name resolves to is checked, and each redirect again.
 * Pure but for the name lookup: unit tested with literal addresses.
 */
public final class StreamHosts {

	private StreamHosts() {}

	/** Checks {@code url}: scheme, host, and every address it resolves to. */
	public static URI check(String url, boolean allowPrivate) throws IOException {
		URI uri;
		try {
			uri = URI.create(url.strip());
		}
		catch(IllegalArgumentException e) {
			throw new IOException("not a link: " + url);
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if(!scheme.equals("http") && !scheme.equals("https"))
			throw new IOException("only http and https links can be played");
		String host = uri.getHost();
		if(host == null || host.isEmpty())
			throw new IOException("the link has no host");
		InetAddress[] addresses = InetAddress.getAllByName(host);
		for(InetAddress address : addresses)
			if(!allowed(address, allowPrivate))
				throw new IOException(host + " is a local or private network address");
		return uri;
	}

	private static boolean allZero(byte[] b, int from, int to) {
		for(int i = from; i < to; i++)
			if(b[i] != 0)
				return false;
		return true;
	}

	/** True if the server may connect to {@code address}. */
	public static boolean allowed(InetAddress address, boolean allowPrivate) {
		if(address.isMulticastAddress() || address.isAnyLocalAddress())
			return false;
		if(allowPrivate)
			return true;
		if(address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress())
			return false;
		byte[] b = address.getAddress();
		if(address instanceof Inet4Address) {
			int first = b[0] & 0xFF, second = b[1] & 0xFF;
			if(first == 100 && second >= 64 && second <= 127)
				return false; // Carrier-grade NAT, 100.64/10.
			if(first == 0)
				return false; // "This network".
		}
		if(address instanceof Inet6Address && (b[0] & 0xFE) == 0xFC)
			return false; // Unique local, fc00::/7.
		if(address instanceof Inet6Address) { // An IPv4 address carried inside IPv6 (2026-11-15): judged as that address.
			int from = -1;
			if((b[0] & 0xFF) == 0x20 && (b[1] & 0xFF) == 0x02)
				from = 2; // 6to4, 2002::/16.
			else if(b[0] == 0 && (b[1] & 0xFF) == 0x64 && (b[2] & 0xFF) == 0xFF && (b[3] & 0xFF) == 0x9B && allZero(b, 4, 12))
				from = 12; // NAT64, 64:ff9b::/96.
			else if(allZero(b, 0, 10) && ((b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF || allZero(b, 10, 12)))
				from = 12; // IPv4-mapped ::ffff:0:0/96, or IPv4-compatible ::/96.
			if(from >= 0) {
				try {
					return allowed(InetAddress.getByAddress(java.util.Arrays.copyOfRange(b, from, from + 4)), false);
				}
				catch(java.net.UnknownHostException e) {
					return false;
				}
			}
		}
		return true;
	}
}
