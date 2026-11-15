package com.arrl.radiocraft.common.network.clientbound;

import com.arrl.radiocraft.Radiocraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * An External CRT Monitor screen's picture (2026-11-12), from its master display to the players near: whether it is on, its
 * mode, its size in map pixels and its frames (one, or the last passes for LOOP), each map colour ids row by row,
 * deflated (a big screen is up to 1024 x 768 pixels a frame, mostly flat colour). See {@code CrtMonitorBlockEntity}.
 *
 * @param master the screen's master display
 * @param frames deflated frames
 */
public record CCrtMonitorPacket(BlockPos master, boolean on, int mode, int width, int height, List<byte[]> frames) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<CCrtMonitorPacket> TYPE = new CustomPacketPayload.Type<>(Radiocraft.id("c_crt_monitor"));
	/** Most frames, and most bytes in one deflated frame. */
	public static final int MAX_FRAMES = 8, MAX_BYTES = 900_000;

	public static final StreamCodec<ByteBuf, CCrtMonitorPacket> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, CCrtMonitorPacket::master,
			ByteBufCodecs.BOOL, CCrtMonitorPacket::on,
			ByteBufCodecs.VAR_INT, CCrtMonitorPacket::mode,
			ByteBufCodecs.VAR_INT, CCrtMonitorPacket::width,
			ByteBufCodecs.VAR_INT, CCrtMonitorPacket::height,
			ByteBufCodecs.byteArray(MAX_BYTES).apply(ByteBufCodecs.list(MAX_FRAMES)), CCrtMonitorPacket::frames,
			CCrtMonitorPacket::new);

	/** The packet for {@code frames} (raw map colour ids, {@code width} x {@code height} each): deflated here. */
	public static CCrtMonitorPacket of(BlockPos master, boolean on, int mode, int width, int height, List<byte[]> frames) {
		List<byte[]> packed = new ArrayList<>();
		for(byte[] frame : frames) {
			byte[] deflated = deflate(frame);
			if(packed.size() < MAX_FRAMES && deflated.length <= MAX_BYTES)
				packed.add(deflated);
		}
		return new CCrtMonitorPacket(master, on, mode, width, height, packed);
	}

	static byte[] deflate(byte[] raw) {
		Deflater deflater = new Deflater(Deflater.BEST_SPEED);
		deflater.setInput(raw);
		deflater.finish();
		ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 8));
		byte[] buffer = new byte[16_384];
		while(!deflater.finished())
			out.write(buffer, 0, deflater.deflate(buffer));
		deflater.end();
		return out.toByteArray();
	}

	/** Frame {@code packed} unpacked to {@code size} bytes, or null if it does not unpack to exactly that. */
	@Nullable
	public static byte[] inflate(byte[] packed, int size) {
		Inflater inflater = new Inflater();
		inflater.setInput(packed);
		byte[] out = new byte[size];
		try {
			int n = 0;
			while(n < size && !inflater.finished()) {
				int got = inflater.inflate(out, n, size - n);
				if(got == 0 && (inflater.needsInput() || inflater.needsDictionary()))
					break;
				n += got;
			}
			return n == size ? out : null;
		}
		catch(DataFormatException e) {
			return null;
		}
		finally {
			inflater.end();
		}
	}

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(final CCrtMonitorPacket packet, final IPayloadContext context) {
		context.enqueueWork(() -> com.arrl.radiocraft.client.render.CrtMonitorPictures.receive(packet));
	}
}
