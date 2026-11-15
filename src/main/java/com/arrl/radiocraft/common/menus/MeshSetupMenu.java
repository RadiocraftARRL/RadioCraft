package com.arrl.radiocraft.common.menus;

import com.arrl.radiocraft.common.blockentities.mesh.MeshDeviceBlockEntity;
import com.arrl.radiocraft.common.blockentities.mesh.ScadaFacesBlockEntity;
import com.arrl.radiocraft.common.init.RadiocraftMenuTypes;
import com.arrl.radiocraft.common.radio.mesh.MeshRole;
import com.arrl.radiocraft.common.radio.scada.Scada;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A fire alarm or SCADA device's setup screen (2026-11-11): its frequency and the callsign it goes out under (no
 * license needed), its location name, and on an RTU its six faces (OFF, IN, OUT, a click each). The text comes back
 * in an {@code SMeshSetupPacket}; the faces as menu button clicks ({@link #faceButton}), and come to the screen as
 * data slots, the mode ordinal plus 3 times the level read or put out.
 */
public final class MeshSetupMenu extends AbstractContainerMenu {

	private static final int FACE_FIRST = 100;

	public final BlockPos pos;
	public final float hz;
	public final String callsign, label;
	public final int address;
	public final MeshRole role;
	public final boolean external;
	/** A fire alarm box's stations (number and name), for its programming panel; empty for other devices. */
	public final java.util.List<com.arrl.radiocraft.common.blockentities.mesh.FireAlarmBoxBlockEntity.Station> stations;
	/** The STATUS page's snapshot (2026-11-11): relays to the nearest controller or master (-1 none, -2 not set up) and who it hears. */
	public final int hops;
	public final java.util.List<Heard> heard;

	/** A device this one hears: its address, role, location name and strength in percent. */
	public record Heard(int address, MeshRole role, String label, int percent) {}

	/** The data slot after the six faces: the LINK meter, percent. */
	public static final int LINK_SLOT = 6;
	private final ContainerData faces;
	private final MeshDeviceBlockEntity device;

	public MeshSetupMenu(int id, MeshDeviceBlockEntity device, ContainerData faces) {
		this(id, device, device.getBlockPos(), device.getFrequency(), device.getLicense(), device.label(), device.getAddress(), device.role(),
				device.hasExternalAntenna(), faces, stationsOf(device), hopsOf(device), heardBy(device));
	}

	/** The role a device's messages end up at: a box's controller, an RTU's master. */
	private static MeshRole target(MeshRole role) {
		return role.system() == com.arrl.radiocraft.common.radio.mesh.MeshSystem.FIRE ? MeshRole.CONTROLLER : MeshRole.MASTER;
	}

	/** Relays from {@code device} to its controller or master, -1 for no route, -2 off the mesh. Server side. */
	public static int hopsOf(MeshDeviceBlockEntity device) {
		if(!(device.getLevel() instanceof ServerLevel level))
			return -2;
		com.arrl.radiocraft.common.radio.mesh.RadioMesh mesh = com.arrl.radiocraft.common.radio.mesh.RadioMesh.of(level.getServer());
		if(mesh.get(device.globalPos()) == null)
			return -2;
		return mesh.hopsTo(device.globalPos(), target(device.role()), com.arrl.radiocraft.RadiocraftServerConfig.MESH_HOP_LIMIT.get());
	}

	/** Who {@code device} hears, strongest first, at most 16. Server side. */
	public static java.util.List<Heard> heardBy(MeshDeviceBlockEntity device) {
		java.util.List<Heard> out = new java.util.ArrayList<>();
		if(!(device.getLevel() instanceof ServerLevel level))
			return out;
		for(var n : com.arrl.radiocraft.common.radio.mesh.RadioMesh.of(level.getServer()).neighbours(device.globalPos())) {
			if(out.size() >= 16)
				break;
			out.add(new Heard(n.node().address(), n.node().role(), n.node().label(), (int)Math.round(n.strength() * 100.0D)));
		}
		return out;
	}

	/** The stations a fire alarm box has numbered, server side; empty for other devices. */
	public static java.util.List<com.arrl.radiocraft.common.blockentities.mesh.FireAlarmBoxBlockEntity.Station> stationsOf(MeshDeviceBlockEntity device) {
		return device instanceof com.arrl.radiocraft.common.blockentities.mesh.FireAlarmBoxBlockEntity box && device.getLevel() instanceof ServerLevel level
				? box.stationList(level) : java.util.List.of();
	}

	private MeshSetupMenu(int id, MeshDeviceBlockEntity device, BlockPos pos, float hz, String callsign, String label, int address, MeshRole role,
	                      boolean external, ContainerData faces, java.util.List<com.arrl.radiocraft.common.blockentities.mesh.FireAlarmBoxBlockEntity.Station> stations,
	                      int hops, java.util.List<Heard> heard) {
		super(RadiocraftMenuTypes.MESH_SETUP.get(), id);
		this.device = device;
		this.pos = pos;
		this.hz = hz;
		this.callsign = callsign;
		this.label = label;
		this.address = address;
		this.role = role;
		this.external = external;
		this.faces = faces;
		this.stations = stations;
		this.hops = hops;
		this.heard = heard;
		addDataSlots(faces);
	}

	public static MeshSetupMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
		BlockPos pos = buf.readBlockPos();
		float hz = buf.readFloat();
		String callsign = buf.readUtf(), label = buf.readUtf();
		int address = buf.readVarInt();
		MeshRole role = MeshRole.values()[buf.readVarInt()];
		boolean external = buf.readBoolean();
		int count = buf.readVarInt();
		java.util.List<com.arrl.radiocraft.common.blockentities.mesh.FireAlarmBoxBlockEntity.Station> stations = new java.util.ArrayList<>();
		for(int i = 0; i < count && i < com.arrl.radiocraft.common.blockentities.mesh.FireAlarmBoxBlockEntity.MAX_ZONES; i++)
			stations.add(new com.arrl.radiocraft.common.blockentities.mesh.FireAlarmBoxBlockEntity.Station(buf.readVarInt(), buf.readUtf(64),
					com.arrl.radiocraft.common.radio.firealarm.AlarmKind.of(buf.readUtf(16))));
		int hops = buf.readVarInt();
		int heardCount = buf.readVarInt();
		java.util.List<Heard> heard = new java.util.ArrayList<>();
		for(int i = 0; i < heardCount && i < 16; i++)
			heard.add(new Heard(buf.readVarInt(), MeshRole.values()[buf.readVarInt()], buf.readUtf(64), buf.readVarInt()));
		MeshDeviceBlockEntity device = inventory.player.level().getBlockEntity(pos) instanceof MeshDeviceBlockEntity d ? d : null;
		return new MeshSetupMenu(id, device, pos, hz, callsign, label, address, role, external, new SimpleContainerData(7), stations, hops, heard);
	}

	/** The faces' data slots for {@code device}: live from the mesh on the server; zeros for a device without faces. */
	public static ContainerData faceData(MeshDeviceBlockEntity device) {
		return new ContainerData() {
			@Override
			public int get(int index) {
				if(index == LINK_SLOT) // How well it hears its best neighbour, for the LINK meter.
					return device.getLevel() instanceof ServerLevel server
							? (int)Math.round(100.0D * com.arrl.radiocraft.common.radio.mesh.RadioMesh.of(server.getServer()).bestLink(device.globalPos())) : 0;
				if(!(device instanceof ScadaFacesBlockEntity faces) || !(device.getLevel() instanceof ServerLevel level))
					return 0;
				Direction face = Direction.from3DDataValue(index);
				return faces.mode(level.getServer(), face).ordinal() + 3 * Math.max(0, faces.level(level.getServer(), face));
			}

			@Override
			public void set(int index, int value) {}

			@Override
			public int getCount() {
				return 7;
			}
		};
	}

	/** True for a fire alarm box: its setup is the programming panel with the LCD (2026-11-11). */
	public boolean isFireBox() {
		return role == MeshRole.BOX;
	}

	/** How well it hears its best neighbour, 0 to 1, live. */
	public double link() {
		return faces.get(LINK_SLOT) / 100.0D;
	}

	public boolean hasFaces() {
		return role == MeshRole.RTU;
	}

	public Scada.FaceMode faceMode(int face) {
		return Scada.FaceMode.values()[Math.floorMod(faces.get(face), 3)];
	}

	public int faceLevel(int face) {
		return faces.get(face) / 3;
	}

	/** The button id that sets face {@code face} (3D data value) to {@code mode}. */
	public static int faceButton(int face, Scada.FaceMode mode) {
		return FACE_FIRST + face * 3 + mode.ordinal();
	}

	@Override
	public boolean clickMenuButton(@NotNull Player player, int id) {
		if(!(player.level() instanceof ServerLevel level) || !(device instanceof ScadaFacesBlockEntity faces) || id < FACE_FIRST || id >= FACE_FIRST + 18)
			return false;
		faces.setMode(level.getServer(), Direction.from3DDataValue((id - FACE_FIRST) / 3), Scada.FaceMode.values()[(id - FACE_FIRST) % 3]);
		return true;
	}

	@Override
	public boolean stillValid(@NotNull Player player) {
		return device != null && !device.isRemoved() && player.distanceToSqr(pos.getCenter()) <= 64.0D;
	}

	@Override
	public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
		return ItemStack.EMPTY;
	}
}
