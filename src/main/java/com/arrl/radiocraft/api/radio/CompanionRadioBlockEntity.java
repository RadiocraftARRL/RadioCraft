package com.arrl.radiocraft.api.radio;

import com.arrl.radiocraft.api.RadiocraftApi;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.client.blockentity.RadioBlockEntityClientHandler;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.radio.Band;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A radio block entity for another mod: a whole RadioCraft radio (power, coax and the bare connector, antennas, voice in
 * and out through Simple Voice Chat, static, the S-meter and comparator, hand mic and desk mic, MARS mod, splatter,
 * moderation, Sable ships) described by a {@link RadioSpec}. Pair it with a {@link CompanionRadioBlock}.
 *
 * <p>It has no screen of its own: {@link #createMenu} returns null, so right-clicking does nothing until you override
 * it with your menu. Drive it from your code through {@link com.arrl.radiocraft.api.blockentities.radio.IRadio}
 * ({@code setFrequency}, {@code toggle}, {@code setPTTDown}). Voice goes out while it is on and keyed, from players
 * near it (or holding its hand mic).</p>
 *
 * <p>To change where it may transmit, override {@link #mayTransmitOn(double)}. CW is not part of this class: an HF
 * radio with a key extends RadioCraft's {@code HFRadioBlockEntity} (internal, but stable in practice) or implements
 * {@link com.arrl.radiocraft.api.blockentities.radio.ICWReceiver} and
 * {@link com.arrl.radiocraft.api.blockentities.radio.ICWTransmitter}.</p>
 *
 * @since 1.12.0
 */
public class CompanionRadioBlockEntity extends RadioBlockEntity {

	private final RadioSpec spec;

	/**
	 * Makes the radio, on the bottom edge of its band.
	 *
	 * @param type your block entity type
	 * @param pos where it is
	 * @param state the block
	 * @param spec what it is
	 */
	public CompanionRadioBlockEntity(BlockEntityType<? extends CompanionRadioBlockEntity> type, BlockPos pos, BlockState state, RadioSpec spec) {
		super(type, pos, state, Band.getBand(spec.band()));
		this.spec = spec;
	}

	/**
	 * What this radio is.
	 *
	 * @return the spec given to the constructor
	 */
	public RadioSpec getSpec() {
		return spec;
	}

	/**
	 * FM or SSB, from the spec.
	 *
	 * @return true for FM
	 */
	@Override
	public boolean isFM() {
		return spec.fm();
	}

	/**
	 * The dial step from the spec.
	 *
	 * @return the step in Hz
	 */
	@Override
	public int getFrequencyStep() {
		return spec.stepHz();
	}

	/**
	 * From the spec.
	 *
	 * @return true if the dial goes past the band edges
	 */
	@Override
	public boolean tunesOutOfBand() {
		return spec.tunesOutOfBand();
	}

	/**
	 * FM is squelched, with no background static; SSB plays static while in voice mode.
	 *
	 * @return true to play static
	 */
	@Override
	public boolean shouldPlayStatic() {
		return !spec.fm() && super.shouldPlayStatic();
	}

	/**
	 * A radio network object with the spec's power draw.
	 *
	 * @return the object
	 */
	@Override
	public BENetworkObject createNetworkObject() {
		return RadiocraftApi.radioNetworkObject(level, worldPosition, spec.transmitUse(), spec.receiveUse());
	}

	/** Starts the static sound on the client. Override and call super to start your own sounds too. */
	@Override
	protected void setupSoundInstances() {
		super.setupSoundInstances();
		RadioBlockEntityClientHandler.startStaticSoundInstance(this);
	}

	/**
	 * The block's name.
	 *
	 * @return the name
	 */
	@Override
	public @NotNull Component getDisplayName() {
		return getBlockState().getBlock().getName();
	}

	/**
	 * No screen by default: override to open your own menu.
	 *
	 * @param id the container id
	 * @param inventory the player's inventory
	 * @param player the player
	 * @return null
	 */
	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
		return null;
	}
}
