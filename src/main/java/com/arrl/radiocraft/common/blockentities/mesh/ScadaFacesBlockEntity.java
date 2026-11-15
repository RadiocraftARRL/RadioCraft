package com.arrl.radiocraft.common.blockentities.mesh;

import com.arrl.radiocraft.common.radio.scada.Scada;
import com.arrl.radiocraft.common.radio.scada.ScadaRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A SCADA device with redstone faces (2026-11-10), an RTU or the master: each face is OFF, IN (its redstone level is
 * read and reported) or OUT (it puts out what the master's rules say). The modes, readings and outputs live with the
 * mesh ({@link Scada}); the block entity reads its IN faces while loaded and gives off its OUT faces.
 */
public abstract class ScadaFacesBlockEntity extends MeshDeviceBlockEntity {

	public static final int READ_TICKS = 4;

	private final int[] emitted = new int[6]; // What it gives off now, by face (server only, for getSignal).

	protected ScadaFacesBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	protected abstract Scada.FaceMode[] modes(Scada scada);

	protected abstract int[] inputs(Scada scada);

	protected abstract int[] outputs(Scada scada);

	/** Records whether it has power, for the mesh while it is unloaded (2026-11-13). */
	protected abstract void setPowered(Scada scada, boolean powered);

	/** Draws {@code scada_power_use} a tick from a power wire. */
	@Override
	protected int powerUse() {
		return com.arrl.radiocraft.RadiocraftServerConfig.SCADA_POWER_USE.get();
	}

	/** The redstone level it gives off on {@code face}. */
	public int emitted(Direction face) {
		return emitted[face.get3DDataValue()];
	}

	/** Steps {@code face} to its next mode (OFF, IN, OUT); the new mode. */
	public Scada.FaceMode cycle(MinecraftServer server, Direction face) {
		Scada scada = Scada.of(server);
		Scada.FaceMode[] modes = modes(scada);
		Scada.FaceMode mode = modes[face.get3DDataValue()].next();
		modes[face.get3DDataValue()] = mode;
		scada.changed();
		return mode;
	}

	/** Sets {@code face} to {@code mode} (the setup screen and the master's LOCAL I/O toggles, 2026-11-11). */
	public void setMode(MinecraftServer server, Direction face, Scada.FaceMode mode) {
		Scada scada = Scada.of(server);
		modes(scada)[face.get3DDataValue()] = mode;
		scada.changed();
	}

	/** The level {@code face} reads (IN) or puts out (OUT), 0 to 15; -1 while it is OFF. */
	public int level(MinecraftServer server, Direction face) {
		Scada scada = Scada.of(server);
		int i = face.get3DDataValue();
		return switch(modes(scada)[i]) {
			case IN -> inputs(scada)[i];
			case OUT -> Math.max(0, Math.min(15, outputs(scada)[i]));
			case OFF -> -1;
		};
	}

	public Scada.FaceMode mode(MinecraftServer server, Direction face) {
		return modes(Scada.of(server))[face.get3DDataValue()];
	}

	public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T blockEntity) {
		if(level instanceof ServerLevel serverLevel && blockEntity instanceof ScadaFacesBlockEntity device)
			device.serverTick(serverLevel);
	}

	protected void serverTick(ServerLevel level) {
		long now = level.getGameTime();
		if(now % 40L == Math.floorMod(worldPosition.hashCode(), 40))
			checkAntenna(level);
		if(now % READ_TICKS != 0L)
			return;
		Scada scada = Scada.of(level.getServer());
		Scada.FaceMode[] modes = modes(scada);
		int[] inputs = inputs(scada);
		int[] outputs = outputs(scada);
		boolean powered = hasPower();
		setPowered(scada, powered);
		boolean changed = false, outChanged = false;
		for(Direction face : Direction.values()) {
			int i = face.get3DDataValue();
			if(!powered) { // Dark: it reads nothing and puts nothing out.
				if(emitted[i] != 0) {
					emitted[i] = 0;
					outChanged = true;
				}
				continue;
			}
			int in = modes[i] == Scada.FaceMode.IN ? level.getSignal(worldPosition.relative(face), face) : 0;
			if(inputs[i] != in) {
				inputs[i] = in;
				changed = true;
			}
			int out = modes[i] == Scada.FaceMode.OUT ? Math.max(0, Math.min(15, outputs[i])) : 0;
			if(emitted[i] != out) {
				emitted[i] = out;
				outChanged = true;
			}
		}
		if(changed)
			scada.changed();
		if(outChanged)
			level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
	}

	/** "N:IN 12  S:OUT 15  ..." for its faces that are not off. */
	protected Component facesLine(MinecraftServer server) {
		Scada scada = Scada.of(server);
		Scada.FaceMode[] modes = modes(scada);
		int[] inputs = inputs(scada);
		StringBuilder line = new StringBuilder();
		for(int i = 0; i < 6; i++) {
			if(modes[i] == Scada.FaceMode.OFF)
				continue;
			if(!line.isEmpty())
				line.append("  ");
			line.append(ScadaRules.FACES.charAt(i)).append(':').append(modes[i].name()).append(' ')
					.append(modes[i] == Scada.FaceMode.IN ? inputs[i] : emitted[i]);
		}
		return line.isEmpty() ? Component.translatable("message.radiocraft.scada.no_faces") : Component.translatable("message.radiocraft.scada.faces", line.toString());
	}

	@Override
	public List<Component> statusLines() {
		List<Component> out = super.statusLines();
		if(level instanceof ServerLevel serverLevel)
			out.add(facesLine(serverLevel.getServer()));
		return out;
	}
}
