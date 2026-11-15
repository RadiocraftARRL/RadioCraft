package com.arrl.radiocraft.common.be_networks.network_objects;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.api.benetworks.BENetwork;
import com.arrl.radiocraft.api.benetworks.BENetworkObject;
import com.arrl.radiocraft.api.benetworks.PowerNetworkObject;
import com.arrl.radiocraft.common.be_networks.ICoaxPassThrough;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.blocks.LinearAmplifierBlock;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.SWRHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The linear amplifier (2026-11-13, doc/Plan-Station.md): on the coax between an HF radio and its antenna, and on a
 * power wire. In OPERATE, powered and not blown, an HF (or CB) signal leaves it {@code amplifier_power_gain} times
 * stronger, still capped at full scale, so it is louder but the band's reach hardly moves. VHF passes through
 * unamplified. While its radio transmits it draws {@code amplifier_power_use} a tick (a trickle in OPERATE otherwise);
 * short of power, in STANDBY or blown, it is just a piece of coax. Into a bad SWR it blows at twice a radio's chance,
 * and an amplified station's RF reaches twice as far into nearby redstone. Used on CB it is illegal: the FCC program,
 * hearing that station through a direction finder, says CB AMPLIFIED ({@link #amplifiedCb}).
 */
public class LinearAmplifierNetworkObject extends PowerNetworkObject implements ICoaxPassThrough {

	public static final ResourceLocation TYPE = Radiocraft.id("linear_amplifier");

	private static final int STORAGE = 400;
	/** CB antennas transmitting through a working amplifier: antenna position to the game time it was last keyed. */
	private static final Map<net.minecraft.core.GlobalPos, Long> AMPLIFIED_CB = new ConcurrentHashMap<>();
	/** How long (ticks) after its last keyed tick an amplified CB station is still flagged. */
	public static final long CB_FLAG_TICKS = 40L;

	private volatile boolean working = false;
	private volatile boolean keyed = false;

	public LinearAmplifierNetworkObject(Level level, BlockPos pos) {
		super(level, pos, STORAGE, STORAGE, STORAGE);
	}

	@Override
	public boolean isIndirectConsumer() {
		return true;
	}

	@Override
	public boolean isDirectConsumer() {
		return false;
	}

	/** True in OPERATE, powered and not blown: it amplifies. */
	public boolean isWorking() {
		return working;
	}

	/** True while its radio transmits through it and it amplifies (the plate meter kicks). */
	public boolean isKeyed() {
		return keyed;
	}

	/** True on the bands it amplifies: HF and CB, not low band or VHF. */
	public static boolean amplifies(float frequencyHertz) {
		return frequencyHertz > 0.0F && frequencyHertz < 30_000_000.0F;
	}

	/** How much stronger a signal at {@code frequencyHertz} leaves it: the configured gain when working, else 1. */
	public double gain(float frequencyHertz) {
		return working && amplifies(frequencyHertz) ? RadiocraftServerConfig.AMPLIFIER_POWER_GAIN.get() : 1.0D;
	}

	@Override
	public void tick(Level level, BlockPos pos) {
		if(level.isClientSide || !(level instanceof ServerLevel serverLevel))
			return;
		BlockState state = level.getBlockState(pos);
		if(!(state.getBlock() instanceof LinearAmplifierBlock)) {
			working = false;
			keyed = false;
			return;
		}
		boolean operate = state.getValue(LinearAmplifierBlock.OPERATE) && !state.getValue(LinearAmplifierBlock.BLOWN);
		RadioBlockEntity radio = transmittingRadio(serverLevel);
		boolean onAir = operate && radio != null && amplifies(radio.getFrequency());
		boolean powered = operate && (!RadiocraftServerConfig.RADIO_POWER_REQUIRED.get() || tryConsumeEnergy(onAir
				? RadiocraftServerConfig.AMPLIFIER_POWER_USE.get() : RadiocraftServerConfig.AMPLIFIER_STANDBY_POWER_USE.get(), false));
		working = powered;
		keyed = powered && onAir;

		if(keyed) {
			tickBlowout(serverLevel, pos, state, radio);
			if(Band.isCbFrequency(radio.getFrequency()))
				flagCb(serverLevel);
		}
		boolean lit = working, kick = keyed;
		if(level.getBlockState(pos).getBlock() instanceof LinearAmplifierBlock
				&& (state.getValue(LinearAmplifierBlock.LIT) != lit || state.getValue(LinearAmplifierBlock.KEYED) != kick))
			level.setBlock(pos, level.getBlockState(pos).setValue(LinearAmplifierBlock.LIT, lit).setValue(LinearAmplifierBlock.KEYED, kick), Block.UPDATE_CLIENTS);
	}

	/** Into a bad SWR it may blow, at twice a radio's chance; a blown amplifier passes the signal through until mended. */
	private void tickBlowout(ServerLevel level, BlockPos pos, BlockState state, RadioBlockEntity radio) {
		double swr = radio.getAntennaSWR() <= 0.01D ? RadioBlockEntity.NO_ANTENNA_SWR : radio.getAntennaSWR();
		double perSecond = Math.min(1.0D, 2.0D * SWRHelper.blowChancePerSecond(swr, RadiocraftServerConfig.RADIO_BLOW_SWR.get(),
				RadiocraftServerConfig.RADIO_BLOW_CHANCE.get()));
		if(perSecond > 0.0D && level.random.nextDouble() < perSecond / 20.0D)
			LinearAmplifierBlock.blow(level, pos, state);
	}

	/** CB through an amplifier is illegal: marks this run's antennas for the FCC program while it transmits. */
	private void flagCb(ServerLevel level) {
		long now = level.getGameTime();
		for(BENetworkObject object : coaxObjects())
			if(object instanceof AntennaNetworkObject antenna)
				AMPLIFIED_CB.put(net.minecraft.core.GlobalPos.of(level.dimension(), antenna.getPos()), now);
		if(AMPLIFIED_CB.size() > 256) // Old marks, from stations long quiet.
			AMPLIFIED_CB.values().removeIf(at -> now - at > CB_FLAG_TICKS || at > now);
	}

	/** True if the CB antenna at {@code antennaPos} transmitted through a working amplifier in the last {@link #CB_FLAG_TICKS}. */
	public static boolean amplifiedCb(Level level, BlockPos antennaPos) {
		Long at = AMPLIFIED_CB.get(net.minecraft.core.GlobalPos.of(level.dimension(), antennaPos));
		long now = level.getGameTime();
		return at != null && now - at <= CB_FLAG_TICKS && at <= now;
	}

	/** The radio on its coax that is on the air now, null if none. */
	private RadioBlockEntity transmittingRadio(ServerLevel level) {
		for(BENetworkObject object : coaxObjects()) {
			if(object instanceof RadioNetworkObject candidate && level.isLoaded(candidate.getPos())
					&& level.getBlockEntity(candidate.getPos()) instanceof RadioBlockEntity radio && radio.isOnTheAir(level))
				return radio;
		}
		return null;
	}

	private Set<BENetworkObject> coaxObjects() {
		Set<BENetworkObject> objects = new LinkedHashSet<>();
		for(Direction side : Direction.values()) {
			BENetwork network = getNetwork(side);
			if(network != null && network.getType() == BENetwork.COAXIAL_TYPE)
				objects.addAll(network.getNetworkObjects());
		}
		return objects;
	}

	/** An amplifier placed beside a radio joins it without coax, like a tuner; coax from its other sides goes on. */
	@Override
	public boolean linksDirectlyTo(BENetworkObject other) {
		return other instanceof RadioNetworkObject;
	}

	@Override
	public ResourceLocation getType() {
		return TYPE;
	}
}
