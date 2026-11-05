package com.arrl.radiocraft.common.computer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * A radio computer's optional password (1.12 open-question fixes): its owner (who placed it) sets one from the shell,
 * and from then on everyone who opens the computer is asked for it; server operators are never asked. The password is
 * kept only as a salted SHA-256 hash, in the computer's disk ({@link ComputerFileStore}), so it follows the computer when
 * it is broken and placed again.
 */
public final class ComputerLock {

	/** Longest password, characters. */
	public static final int MAX_LENGTH = 16;

	private ComputerLock() {}

	/** The hash kept for {@code password} on computer {@code id} (the id is the salt); "" for no password. */
	public static String hash(UUID id, String password) {
		if(password == null || password.isEmpty())
			return "";
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(id.toString().getBytes(StandardCharsets.UTF_8));
			digest.update((byte)0);
			digest.update(password.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest.digest());
		}
		catch(NoSuchAlgorithmException e) {
			throw new IllegalStateException("Every Java has SHA-256", e);
		}
	}

	/** True if {@code password} matches {@code hash} on computer {@code id}; anything matches no password. */
	public static boolean matches(UUID id, String hash, String password) {
		if(hash == null || hash.isEmpty())
			return true;
		return MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8), hash(id, password == null ? "" : password).getBytes(StandardCharsets.UTF_8));
	}

	/** True if {@code password} can be set: at most {@link #MAX_LENGTH} printable ASCII characters ("" clears it). */
	public static boolean valid(String password) {
		if(password == null || password.length() > MAX_LENGTH)
			return false;
		for(int i = 0; i < password.length(); i++)
			if(password.charAt(i) < 32 || password.charAt(i) > 126)
				return false;
		return true;
	}
}
