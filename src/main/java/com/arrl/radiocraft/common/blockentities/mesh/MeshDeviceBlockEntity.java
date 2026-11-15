package com.arrl.radiocraft.common.blockentities.mesh;

import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.api.benetworks.INetworkObjectProvider;
import com.arrl.radiocraft.common.be_networks.network_objects.RadioNetworkObject;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicense;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenseRules;
import com.arrl.radiocraft.common.radio.mesh.MeshLink;
import com.arrl.radiocraft.common.radio.mesh.MeshNode;
import com.arrl.radiocraft.common.radio.mesh.MeshRole;
import com.arrl.radiocraft.common.radio.mesh.RadioMesh;
import com.arrl.radiocraft.common.radio.Band;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A device on the radio mesh (2026-11-10): a fire alarm box or controller, a SCADA RTU or master. It has no frequency
 * until a commercial license is used on it (the license's frequency, and the next free box number or RTU id on it).
 * While loaded it keeps its {@link MeshNode} up to date: its name (from an anvil) as its label, and whether an antenna
 * is on its coax (normal range) or it is using its built-in one (poor range). The mesh does the rest, loaded or not.
 */
public abstract class MeshDeviceBlockEntity extends BlockEntity implements INetworkObjectProvider {

	private float frequency = 0.0F;
	private String license = "";
	private int address = 0;
	@Nullable
	private Component customName;
	private boolean external = false;

	protected MeshDeviceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public abstract MeshRole role();

	/** The first address handed out on a frequency: box 1, RTU 1. Controllers and masters are 0. */
	protected int firstAddress() {
		return 1;
	}

	public float getFrequency() {
		return frequency;
	}

	public String getLicense() {
		return license;
	}

	public int getAddress() {
		return address;
	}

	public boolean hasExternalAntenna() {
		return external;
	}

	public GlobalPos globalPos() {
		return GlobalPos.of(level == null ? net.minecraft.world.level.Level.OVERWORLD : level.dimension(), worldPosition);
	}

	/** What it is called: its anvil name, upper case, or empty. */
	public String label() {
		return customName == null ? "" : MeshNode.clean(customName.getString());
	}

	@Nullable
	public Component getCustomName() {
		return customName;
	}

	/**
	 * On the coax, for an antenna. Fire alarm devices draw nothing from a power wire; SCADA devices draw
	 * {@link #powerUse()} a tick (2026-11-13).
	 */
	@Override
	public BENetworkObject createNetworkObject() {
		int use = powerUse();
		RadioNetworkObject object = new RadioNetworkObject(level, worldPosition, use, use);
		if(use > 0)
			object.wantsOn = true;
		return object;
	}

	/** Power drawn per tick from a power wire; 0 (the default) runs without one. */
	protected int powerUse() {
		return 0;
	}

	/** True when it has the power it needs: always without a draw, else while its power wire keeps it fed. */
	public boolean hasPower() {
		int use = powerUse();
		if(use <= 0 || level == null)
			return true;
		if(!(com.arrl.radiocraft.api.capabilities.IBENetworks.getObject(level, worldPosition) instanceof RadioNetworkObject radio))
			return false;
		if(radio.getReceiveUse() != use || !radio.wantsOn) { // Saved before it drew power, or the config changed.
			radio.setPowerUse(use, use);
			radio.setWantsOn(true);
		}
		return radio.isPowered;
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if(level instanceof ServerLevel serverLevel) {
			getNetworkObject(serverLevel, worldPosition);
			updateNode(serverLevel.getServer());
		}
	}

	/**
	 * Sets it to {@code granted}'s frequency (the output; a repeater's input is for repeaters), with the next free
	 * address on it. True if anything changed.
	 */
	public boolean setLicense(MinecraftServer server, CommercialLicense granted) {
		return setFrequency(server, granted.outputHz(), granted.callsign());
	}

	/** Callsigns a device takes: 3 to 10 letters and digits (a commercial callsign like WQAB123, or any station's). */
	public static final java.util.regex.Pattern CALLSIGN = java.util.regex.Pattern.compile("[A-Z0-9]{3,10}");

	/**
	 * Why {@code hz} and {@code callsign} cannot be set (a {@code message.radiocraft.mesh_setup.*} key), or null if they
	 * can: the frequency in the commercial VHF or low band and outside the marine block, the callsign its letters and
	 * digits. No license is needed (2026-11-11): the callsign is what the device sends as its station ID, and the FCC
	 * program shows whether it is licensed.
	 */
	@Nullable
	public static String setupProblem(float hz, String callsign) {
		if(!CommercialLicenseRules.needsLicense(hz))
			return "bad_frequency";
		if(!CALLSIGN.matcher(callsign).matches())
			return "bad_callsign";
		return null;
	}

	/**
	 * Sets it to {@code hz} under {@code callsign} (the device setup screen, or a license used on it), with the next
	 * free address on it. True if anything changed.
	 */
	public boolean setFrequency(MinecraftServer server, float hz, String callsign) {
		boolean same = license.equals(callsign) && Math.abs(frequency - hz) < MeshNode.SAME_CHANNEL_HZ;
		if(same)
			return false;
		frequency = hz;
		license = callsign;
		RadioMesh mesh = RadioMesh.of(server);
		address = firstAddress() == 0 ? 0 : mesh.freeAddress(role(), frequency, firstAddress(), globalPos());
		setChanged();
		updateNode(server);
		onFrequencySet(server);
		return true;
	}

	/** Its location name (what an anvil name sets): blank for none. */
	public void setLabel(MinecraftServer server, String label) {
		String clean = MeshNode.clean(label);
		customName = clean.isEmpty() ? null : Component.literal(clean);
		setChanged();
		updateNode(server);
	}

	/**
	 * Opens the device's setup screen (frequency, callsign, label; an RTU's faces) for {@code player}
	 * ({@link com.arrl.radiocraft.common.menus.MeshSetupMenu}, 2026-11-11).
	 */
	public void openSetup(net.minecraft.server.level.ServerPlayer player) {
		MeshDeviceBlockEntity device = this;
		player.openMenu(new net.minecraft.world.MenuProvider() {
			@Override
			public @NotNull Component getDisplayName() {
				return getBlockState().getBlock().getName();
			}

			@Override
			public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.@NotNull Inventory inventory,
			                                                                      net.minecraft.world.entity.player.@NotNull Player p) {
				return new com.arrl.radiocraft.common.menus.MeshSetupMenu(id, device, com.arrl.radiocraft.common.menus.MeshSetupMenu.faceData(device));
			}
		}, buf -> {
			buf.writeBlockPos(worldPosition);
			buf.writeFloat(frequency);
			buf.writeUtf(license);
			buf.writeUtf(label());
			buf.writeVarInt(address);
			buf.writeVarInt(role().ordinal());
			buf.writeBoolean(external);
			var stations = com.arrl.radiocraft.common.menus.MeshSetupMenu.stationsOf(device);
			buf.writeVarInt(stations.size());
			for(var station : stations) {
				buf.writeVarInt(station.zone());
				buf.writeUtf(station.name(), 64);
				buf.writeUtf(station.kind().name(), 16);
			}
			buf.writeVarInt(com.arrl.radiocraft.common.menus.MeshSetupMenu.hopsOf(device));
			var heard = com.arrl.radiocraft.common.menus.MeshSetupMenu.heardBy(device);
			buf.writeVarInt(heard.size());
			for(var h : heard) {
				buf.writeVarInt(h.address());
				buf.writeVarInt(h.role().ordinal());
				buf.writeUtf(h.label(), 64);
				buf.writeVarInt(h.percent());
			}
		});
	}

	/** Blocks within which players are sent a controller's or master's screen, for the text drawn on the block. */
	public static final double SCREEN_RANGE = 24.0D;

	/**
	 * Sends {@code status} (built only if someone needs it) to the players with {@code menu} open on this device every
	 * 10 ticks, and to the players near it every 20, for the green screen on the block (2026-11-11).
	 */
	protected void sendScreen(ServerLevel level, Class<?> menu, java.util.function.Supplier<CompoundTag> status) {
		long now = level.getGameTime();
		if(now % 10L != Math.floorMod(worldPosition.hashCode(), 10))
			return;
		boolean everyone = (now / 10L) % 2L == 0L; // Every other time: once a second.
		CompoundTag data = null;
		for(net.minecraft.server.level.ServerPlayer player : level.players()) {
			boolean viewing = menu.isInstance(player.containerMenu) && player.distanceToSqr(worldPosition.getCenter()) <= 64.0D;
			boolean near = everyone && player.distanceToSqr(worldPosition.getCenter()) <= SCREEN_RANGE * SCREEN_RANGE;
			if(!viewing && !near)
				continue;
			if(data == null)
				data = status.get();
			com.arrl.radiocraft.common.network.clientbound.CMeshScreenPacket.send(player, worldPosition, data);
		}
	}

	/** Called once its frequency is set or changed. */
	protected void onFrequencySet(MinecraftServer server) {}

	/** Puts its current setup on the mesh (nothing if it has no frequency). */
	public void updateNode(MinecraftServer server) {
		if(frequency <= 0.0F)
			return;
		RadioMesh.of(server).put(new MeshNode(globalPos(), role(), frequency, address, external, label(), license));
	}

	/** Every so often while loaded: notes whether an antenna is on its coax. */
	protected void checkAntenna(ServerLevel serverLevel) {
		boolean now = getNetworkObject(serverLevel, worldPosition) instanceof RadioNetworkObject radio && !radio.getAntennas().isEmpty();
		if(now != external) {
			external = now;
			setChanged();
			updateNode(serverLevel.getServer());
		}
	}

	/** The first lines of its status: frequency, license, address and antenna. */
	public List<Component> statusLines() {
		List<Component> out = new ArrayList<>();
		String base = "message.radiocraft.mesh.";
		if(frequency <= 0.0F) {
			out.add(Component.translatable(base + "no_frequency").withStyle(ChatFormatting.YELLOW));
			return out;
		}
		out.add(Component.translatable(base + "frequency", CommercialLicenseRules.mhz(frequency), license));
		Band band = Band.getBand(frequency);
		double reach = band == null ? 0.0D : MeshLink.range(band.losRange(), MeshLink.reach(external, RadioMesh.internalRange()), MeshLink.reach(external, RadioMesh.internalRange()));
		out.add(Component.translatable(base + (external ? "antenna_external" : "antenna_internal"), String.format(Locale.ROOT, "%.0f", reach)));
		return out;
	}

	// -------------------- SAVE --------------------

	@Override
	protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putFloat("frequency", frequency);
		tag.putString("license", license);
		tag.putInt("address", address);
		tag.putBoolean("external", external);
		if(customName != null)
			tag.putString("CustomName", Component.Serializer.toJson(customName, registries));
	}

	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.loadAdditional(tag, registries);
		frequency = tag.getFloat("frequency");
		license = tag.getString("license");
		address = tag.getInt("address");
		external = tag.getBoolean("external");
		customName = tag.contains("CustomName") ? Component.Serializer.fromJson(tag.getString("CustomName"), registries) : null;
	}

	@Override
	protected void applyImplicitComponents(BlockEntity.@NotNull DataComponentInput input) {
		super.applyImplicitComponents(input);
		customName = input.get(DataComponents.CUSTOM_NAME);
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.@NotNull Builder components) {
		super.collectImplicitComponents(components);
		components.set(DataComponents.CUSTOM_NAME, customName);
	}
}
