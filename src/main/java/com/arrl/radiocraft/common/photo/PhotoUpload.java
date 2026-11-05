package com.arrl.radiocraft.common.photo;

/**
 * One photo on its way up from a client: the server opens it when the shutter goes (so a client can only send a photo
 * it was asked for), and the client sends the picture as {@link #CHUNKS} chunks of {@link #CHUNK_BYTES}, each once.
 * It gives up {@link #TIMEOUT_TICKS} after the shutter. No game classes, so it can be unit tested.
 */
public final class PhotoUpload {

	/** Bytes in a chunk: 32 rows of the picture, well under a custom payload's 32 KiB. */
	public static final int CHUNK_BYTES = 4096;
	/** Chunks in a photo. */
	public static final int CHUNKS = PhotoImage.PIXELS / CHUNK_BYTES;
	/** Server ticks a client has to send the whole photo after the shutter (10 seconds). */
	public static final int TIMEOUT_TICKS = 200;

	/** What a chunk did. */
	public enum Result {
		/** Not this upload's, a repeat, the wrong size or out of range: thrown away. */
		REJECTED,
		/** Taken; more to come. */
		PARTIAL,
		/** Taken, and that was the last: {@link #pixels()} is the photo. */
		COMPLETE,
		/** Too late: the upload is over. */
		EXPIRED
	}

	private final int nonce;
	private final boolean colour;
	private final long openedTick;
	private final byte[] pixels = new byte[PhotoImage.PIXELS];
	private int received; // A bit per chunk.

	/**
	 * @param nonce what the client must send back with every chunk
	 * @param colour colour photo paper (else black and white)
	 * @param openedTick the server tick the shutter went
	 */
	public PhotoUpload(int nonce, boolean colour, long openedTick) {
		this.nonce = nonce;
		this.colour = colour;
		this.openedTick = openedTick;
	}

	public int nonce() {
		return nonce;
	}

	public boolean colour() {
		return colour;
	}

	/** True once {@link #TIMEOUT_TICKS} have gone by since the shutter. */
	public boolean expired(long nowTick) {
		return nowTick - openedTick > TIMEOUT_TICKS;
	}

	/** Takes chunk {@code index} of upload {@code nonce}, sent at server tick {@code nowTick}. */
	public Result accept(int nonce, int index, byte[] data, long nowTick) {
		if(expired(nowTick))
			return Result.EXPIRED;
		if(nonce != this.nonce || index < 0 || index >= CHUNKS || data == null || data.length != CHUNK_BYTES || (received & (1 << index)) != 0)
			return Result.REJECTED;
		System.arraycopy(data, 0, pixels, index * CHUNK_BYTES, CHUNK_BYTES);
		received |= 1 << index;
		return isComplete() ? Result.COMPLETE : Result.PARTIAL;
	}

	public boolean isComplete() {
		return received == (1 << CHUNKS) - 1;
	}

	/** The picture as received (map colour ids; not yet checked against the palette). */
	public byte[] pixels() {
		return pixels;
	}

	/** Chunk {@code index} of {@code pixels}: what a client sends. */
	public static byte[] chunk(byte[] pixels, int index) {
		byte[] out = new byte[CHUNK_BYTES];
		System.arraycopy(pixels, index * CHUNK_BYTES, out, 0, CHUNK_BYTES);
		return out;
	}
}
