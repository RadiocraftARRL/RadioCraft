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
		return true;
	}
}
