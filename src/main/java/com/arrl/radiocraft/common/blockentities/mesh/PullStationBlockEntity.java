package com.arrl.radiocraft.common.blockentities.mesh;

import com.arrl.radiocraft.common.blocks.mesh.PullStationBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * A fire alarm pull station or panic button (2026-11-10). It holds its zone number, which the fire alarm box it is wired
 * to gives it the first time it sees it (1, 2, 3... in the order it finds them), and its name (2026-11-11, set on the
 * box's programming panel: "KITCHEN"), which goes out with its alarms. Whether it is pulled is in its block state
 * ({@link PullStationBlock#PULLED}).
 */
public class PullStationBlockEntity extends BlockEntity {

	private int zone = 0;
	private String name = "";
	/** What its alarm is (2026-11-11): FIRE unless set otherwise on the box's panel. */
	private com.arrl.radiocraft.common.radio.firealarm.AlarmKind kind = com.arrl.radiocraft.common.radio.firealarm.AlarmKind.FIRE;

	/** Longest station name: what the box's LCD shows on a line. */
	public static final int NAME_MAX = 16;

	public PullStationBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.PULL_STATION.get(), pos, state);
	}

	public int getZone() {
		return zone;
	}

	public void setZone(int zone) {
		this.zone = zone;
		setChanged();
	}

	public String getName() {
		return name;
	}

	/** Its name, cleaned as a mesh label is and at most {@link #NAME_MAX} characters; blank for none. */
	public void setName(String name) {
		String clean = com.arrl.radiocraft.common.radio.mesh.MeshNode.clean(name);
		this.name = clean.length() > NAME_MAX ? clean.substring(0, NAME_MAX).strip() : clean;
		setChanged();
	}

	/** "03" or "03 KITCHEN", for messages; null before a box has numbered it. */
	@org.jetbrains.annotations.Nullable
	public String numberText() {
		if(zone <= 0)
			return null;
		return name.isEmpty() ? String.format(java.util.Locale.ROOT, "%02d", zone) : String.format(java.util.Locale.ROOT, "%02d %s", zone, name);
	}

	public com.arrl.radiocraft.common.radio.firealarm.AlarmKind getKind() {
		return kind;
	}

	public void setKind(com.arrl.radiocraft.common.radio.firealarm.AlarmKind kind) {
		this.kind = kind;
		setChanged();
	}

	public boolean isPulled() {
		return getBlockState().hasProperty(PullStationBlock.PULLED) && getBlockState().getValue(PullStationBlock.PULLED);
	}

	/** Back up, as the key leaves it (the box's RESET, 2026-11-11). */
	public void reset() {
		if(level == null || !isPulled())
			return;
		level.setBlock(worldPosition, getBlockState().setValue(PullStationBlock.PULLED, false), net.minecraft.world.level.block.Block.UPDATE_ALL);
		level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1.4F);
	}

	@Override
	protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("zone", zone);
		tag.putString("name", name);
		tag.putString("kind", kind.name());
	}

	@Override
	protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
		super.loadAdditional(tag, registries);
		zone = tag.getInt("zone");
		name = tag.getString("name");
		kind = com.arrl.radiocraft.common.radio.firealarm.AlarmKind.of(tag.getString("kind"));
	}
}
