package com.arrl.radiocraft.common.computer;

import com.arrl.radiocraft.common.photo.MapPalettes;
import com.arrl.radiocraft.common.photo.PhotoImage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * What the radio computer's file extensions hold, and the helpers to read and write them, on either side. Programs
 * agree on these so one program's files open in another (the scanner's pictures in SSTV, the logbook's contacts in the
 * frequency manager):
 * <ul>
 * <li>{@code .TXT}: UTF-8 text ({@link ComputerStorage#text(String)}).</li>
 * <li>{@code .IMG}: a map picture, exactly {@link PhotoImage#PIXELS} packed map colour ids, row by row (what a filled
 * map holds; {@code PhotoPrints} prints and reads them). The server checks the ids on every write.</li>
 * <li>{@code .LOG}, {@code .FRQ}, {@code .BOK} and {@code .DAT}: a compressed NBT compound ({@link #nbt(CompoundTag)} /
 * {@link #nbt(byte[])}), laid out by the program that owns the extension (logbook, frequency manager, book editor;
 * {@code .DAT} for anything else). Layouts: {@link Logbook}, {@link FrequencyList}, {@link BookDocument}.</li>
 * <li>{@code .CPG}: a commercial radio codeplug, compressed NBT ({@link Codeplug}), from the radio programmer.</li>
 * <li>{@code .ADI}: a logbook exported as ADIF text (UTF-8, ASCII in practice), for real logging programs.</li>
 * </ul>
 */
public final class ComputerFileFormats {

	public static final String TEXT = "TXT", IMAGE = "IMG", LOG = "LOG", FREQUENCIES = "FRQ", BOOK = "BOK", DATA = "DAT",
			CODEPLUG = "CPG", ADIF = "ADI";
	/** Most bytes an NBT file may unpack to (a guard against a crafted file). */
	private static final long NBT_QUOTA = 4L * 1024L * 1024L;

	private ComputerFileFormats() {}

	/** {@code tag} as file contents (compressed NBT). */
	public static byte[] nbt(CompoundTag tag) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			NbtIo.writeCompressed(tag, out);
		}
		catch(IOException e) {
			throw new IllegalStateException("Could not write a computer file", e); // A byte array cannot fail to take bytes.
		}
		return out.toByteArray();
	}

	/** An NBT file's compound, or null if the file is not one. */
	@Nullable
	public static CompoundTag nbt(byte[] data) {
		try {
			return NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.create(NBT_QUOTA));
		}
		catch(IOException | RuntimeException e) {
			return null;
		}
	}

	/** True if {@code data} is a whole map picture. */
	public static boolean isImage(byte[] data) {
		return data.length == PhotoImage.PIXELS;
	}

	/**
	 * {@code data} made fit for a file named {@code name}: a picture has ids that are no map colour turned black; other
	 * kinds are kept as they are. Null if it cannot be one ({@code .IMG} of the wrong size).
	 */
	@Nullable
	public static byte[] checked(String name, byte[] data) {
		if(IMAGE.equals(ComputerStorage.extension(name)))
			return isImage(data) ? MapPalettes.vanilla().sanitised(data) : null;
		return data;
	}
}
