package com.arrl.radiocraft.common.blockentities.mesh;

import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.radio.mesh.MeshRole;
import com.arrl.radiocraft.common.radio.scada.Scada;
import com.arrl.radiocraft.common.radio.scada.ScadaRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * The SCADA master (2026-11-10): polls the RTUs on its frequency in turn and runs its rules on what they read
 * ({@link Scada}). Its own faces are unit 0 in the rules. The rules come from a book and quill (or a written book) used
 * on it, a rule a line; a {@code POLL n} line sets the seconds each RTU's poll takes (1 to 3). Counts as a
 * base on its license.
 */
public class ScadaMasterBlockEntity extends ScadaFacesBlockEntity implements net.minecraft.world.MenuProvider {

	public ScadaMasterBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.SCADA_MASTER.get(), pos, state);
	}

	@Override
	public MeshRole role() {
		return MeshRole.MASTER;
	}

	@Override
	protected int firstAddress() {
		return 0;
	}

	public Scada.Master master(MinecraftServer server) {
		return Scada.of(server).master(globalPos());
	}

	@Override
	protected Scada.FaceMode[] modes(Scada scada) {
		return scada.master(globalPos()).modes;
	}

	@Override
	protected int[] inputs(Scada scada) {
		return scada.master(globalPos()).inputs;
	}

	@Override
	protected int[] outputs(Scada scada) {
		return scada.master(globalPos()).outputs;
	}

	@Override
	protected void setPowered(Scada scada, boolean powered) {
		if(scada.master(globalPos()).powered != powered) {
			scada.master(globalPos()).powered = powered;
			scada.changed();
		}
	}

	@Override
	public net.minecraft.network.chat.@org.jetbrains.annotations.NotNull Component getDisplayName() {
		return getCustomName() != null ? getCustomName() : Component.translatable("block.radiocraft.scada_master");
	}

	/** The console's screen (2026-11-11): what it shows comes every 10 ticks in {@link com.arrl.radiocraft.common.network.clientbound.CMeshScreenPacket}. */
	@Override
	public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.@org.jetbrains.annotations.NotNull Inventory inventory,
	                                                                      net.minecraft.world.entity.player.@org.jetbrains.annotations.NotNull Player player) {
		if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel)
			com.arrl.radiocraft.common.network.clientbound.CMeshScreenPacket.send(serverPlayer, worldPosition, consoleStatus(serverLevel));
		return new com.arrl.radiocraft.common.menus.ScadaMasterMenu(id, this);
	}

	private net.minecraft.nbt.CompoundTag consoleStatus(ServerLevel level) {
		return com.arrl.radiocraft.common.computer.apps.ScadaHandler.status(level.getServer(), this, master(level.getServer()));
	}

	@Override
	protected void serverTick(ServerLevel level) {
		super.serverTick(level);
		sendScreen(level, com.arrl.radiocraft.common.menus.ScadaMasterMenu.class, () -> consoleStatus(level));
	}

	/** Takes {@code lines} as its rule book. */
	public ScadaRules.Parsed setRules(MinecraftServer server, List<String> lines) {
		ScadaRules.Parsed parsed = master(server).setRules(lines);
		Scada.of(server).changed();
		return parsed;
	}

	@Override
	public List<Component> statusLines() {
		List<Component> out = super.statusLines();
		if(!(level instanceof ServerLevel serverLevel))
			return out;
		Scada.Master master = master(serverLevel.getServer());
		String base = "message.radiocraft.scada.";
		out.add(Component.translatable(base + "rules", master.rules.size(), master.pollSeconds));
		for(String error : master.ruleErrors)
			out.add(Component.translatable(base + "rule_error", error).withStyle(ChatFormatting.RED));
		for(Map.Entry<Integer, Scada.Seen> entry : master.rtus.entrySet()) {
			Scada.Seen seen = entry.getValue();
			StringBuilder points = new StringBuilder();
			for(int i = 0; i < 6; i++)
				if(seen.inputs[i] >= 0)
					points.append(ScadaRules.FACES.charAt(i)).append('=').append(seen.inputs[i]).append(' ');
			out.add(seen.commFail ? Component.translatable(base + "rtu_fail", entry.getKey()).withStyle(ChatFormatting.RED)
					: Component.translatable(base + "rtu_ok", entry.getKey(), points.toString().trim(), seen.replies, seen.polls));
		}
		return out;
	}
}
