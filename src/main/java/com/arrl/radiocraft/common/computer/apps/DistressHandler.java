package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.be_networks.DataWire;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.ScannerBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.CommercialMobileBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.MarineRadioBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerAppState;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.radio.data.AprsMapPainter;
import com.arrl.radiocraft.common.radio.distress.DistressAlert;
import com.arrl.radiocraft.common.radio.distress.DistressLog;
import com.arrl.radiocraft.common.radio.distress.DistressMap;
import com.arrl.radiocraft.common.radio.distress.DistressNature;
import com.arrl.radiocraft.common.radio.distress.DscCall;
import com.arrl.radiocraft.common.radio.distress.Mmsi;
import com.arrl.radiocraft.common.radio.distress.PersonalSos;
import com.arrl.radiocraft.compat.sable.Sublevels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The DISTRESS program's server side (2026-11-09). It listens through every radio cabled to the computer (touching, or
 * on its data wire: {@link ComputerContext#linked}) that can hear distress traffic, with no picker: each marine radio's
 * DSC receiver (DSC calls on channel 70, {@link MarineRadioBlockEntity}) and each commercial VHF mobile, which hears
 * distress beacons while its channel is on 161.975 MHz ({@link CommercialMobileBlockEntity}). Their logs are merged
 * into one list ({@link DistressLog#merge}: one entry per alert, its state only moving on). It acknowledges DSC calls
 * through the first powered marine radio that heard them, prints a map round an alert on the scanner, and sends an SOS:
 * a MARINE SOS is the DSC distress call of a cabled marine radio (as its DISTRESS button), a PERSONAL SOS a beacon-style
 * alert on 161.975 through a cabled commercial VHF mobile, repeated every 50 s ({@link PersonalSos}) until cancelled
 * here, or until that radio loses its power or its cable.
 * <ul>
 * <li>{@code status}: {@code radio} (some radio that hears distress traffic is cabled), {@code powered} (one is on),
 * {@code marines} / {@code marinesOn} (marine radios cabled / on), {@code commercials} / {@code commercialsOn}
 * (commercial VHF mobiles cabled / on) and {@code beaconRadios} (of those, on 161.975), {@code x}/{@code z} where the
 * station is, the user's {@code mmsi}, {@code alerts} (each {@code a}, {@link DistressAlert#encode}, {@code age} and
 * {@code heard} in seconds), {@code marineWhy} / {@code personalWhy} (why that SOS cannot be sent now, empty if it
 * can), {@code outDsc} (the outgoing DSC call: {@code state}, {@code nature}, {@code ackBy}, {@code next} seconds) and
 * {@code outPersonal} ({@code state}, {@code nature}, {@code reason}, {@code next}).</li>
 * <li>{@code map}: {@code pixels}, a 128 square map round the station and every alert, and its {@code cx}, {@code cz},
 * {@code bpp}.</li>
 * <li>{@code ack} {@code id}: acknowledges DSC call {@code id} on channel 70 as the user's MMSI.</li>
 * <li>{@code print} {@code id}: a printout of the area round alert {@code id}, marked, with its details.</li>
 * <li>{@code send} {@code kind} (marine, personal), {@code nature} (a {@link DistressNature} name): sends an SOS.</li>
 * <li>{@code cancel} {@code kind}: cancels the outgoing SOS of that kind (the cancel burst goes out).</li>
 * </ul>
 */
public final class DistressHandler implements ComputerAppHandler {

	/** Ticks between the personal SOS checks (power, cable, repeat due). */
	private static final int CHECK_TICKS = 20;

	/** One computer's outgoing personal SOS. */
	public static final class State implements ComputerAppState {
		PersonalSos personal = PersonalSos.IDLE;

		public PersonalSos personal() {
			return personal;
		}

		@Override
		public void save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putString("state", personal.state().name());
			tag.putLong("id", personal.alertId());
			tag.putString("owner", personal.owner());
			tag.putString("nature", personal.nature().name());
			tag.putLong("radio", personal.radioPos());
			tag.putLong("sent", personal.sentAt());
			tag.putLong("next", personal.nextAt());
			tag.putString("reason", personal.reason());
		}

		@Override
		public void load(CompoundTag tag, HolderLookup.Provider registries) {
			PersonalSos.State state;
			try {
				state = PersonalSos.State.valueOf(tag.getString("state"));
			}
			catch(IllegalArgumentException e) {
				state = PersonalSos.State.IDLE;
			}
			personal = state == PersonalSos.State.IDLE ? PersonalSos.IDLE : new PersonalSos(state, tag.getLong("id"), tag.getString("owner"),
					DistressNature.byName(tag.getString("nature")), tag.getLong("radio"), tag.getLong("sent"), tag.getLong("next"), tag.getString("reason"));
		}
	}

	/** {@code computer}'s DISTRESS state. */
	public static State state(RadioComputerBlockEntity computer) {
		return computer.state(ComputerApps.DISTRESS, State::new);
	}

	/** The radios the program hears through: every cabled marine radio and commercial VHF mobile. */
	public record Radios(List<MarineRadioBlockEntity> marines, List<CommercialMobileBlockEntity> commercials) {

		public boolean any() {
			return !marines.isEmpty() || !commercials.isEmpty();
		}

		/** Every radio's log merged. */
		public List<DistressLog.Entry> alerts() {
			List<List<DistressLog.Entry>> logs = new ArrayList<>();
			for(MarineRadioBlockEntity radio : marines)
				logs.add(radio.getDistressLog().entries());
			for(CommercialMobileBlockEntity radio : commercials)
				logs.add(radio.getDistressLog().entries());
			return DistressLog.merge(logs);
		}

		@Nullable
		public DistressLog.Entry alert(long id) {
			for(DistressLog.Entry entry : alerts())
				if(entry.alert().id() == id)
					return entry;
			return null;
		}

		/** The first marine radio switched on (and not blown), or null. */
		@Nullable
		public MarineRadioBlockEntity marineOn() {
			for(MarineRadioBlockEntity radio : marines)
				if(radio.isPowered() && !radio.isBlown())
					return radio;
			return null;
		}

		/** The first commercial VHF mobile switched on (and not blown), or null. */
		@Nullable
		public CommercialMobileBlockEntity commercialOn() {
			for(CommercialMobileBlockEntity radio : commercials)
				if(radio.isPowered() && !radio.isBlown())
					return radio;
			return null;
		}

		/** The marine radio whose own DSC call is on (sent or acknowledged, not yet cancelled), or null. */
		@Nullable
		public MarineRadioBlockEntity calling() {
			for(MarineRadioBlockEntity radio : marines)
				if(radio.getDscCall().canCancel())
					return radio;
			return null;
		}
	}

	/** The radios cabled to the computer at {@code pos}. */
	public static Radios radios(Level level, BlockPos pos) {
		List<CommercialMobileBlockEntity> commercials = new ArrayList<>();
		for(CommercialMobileBlockEntity radio : DataWire.linked(level, pos, CommercialMobileBlockEntity.class))
			if(radio.hearsBeacons()) // A low band mobile cannot be put on 161.975.
				commercials.add(radio);
		return new Radios(DataWire.linked(level, pos, MarineRadioBlockEntity.class), commercials);
	}

	public static Radios radios(ComputerContext context) {
		return new Radios(context.linked(MarineRadioBlockEntity.class),
				context.linked(CommercialMobileBlockEntity.class).stream().filter(CommercialMobileBlockEntity::hearsBeacons).toList());
	}

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		Radios radios = radios(context);
		if(!action.equals("status") && !action.equals("cancel") && !radios.any()) {
			reply.error("distress_no_radio");
			return;
		}
		switch(action) {
			case "status" -> reply.ok(status(context, radios));
			case "map" -> reply.ok(map(context, radios));
			case "ack" -> {
				String error = acknowledge(radios, data.getLong("id"), Mmsi.of(context.player().getUUID()));
				if(error != null)
					reply.error(error);
				else
					reply.ok();
			}
			case "print" -> {
				DistressLog.Entry entry = radios.alert(data.getLong("id"));
				if(entry == null) {
					reply.error("distress_ack_unknown");
					return;
				}
				ScannerBlockEntity scanner = context.scanner();
				if(scanner == null) {
					reply.error("no_scanner");
					return;
				}
				ScannerBlockEntity.Result result = scanner.printImage(printout(context, radios, entry.alert()),
						(entry.alert().isDsc() ? "DSC " : "BEACON ") + entry.alert().owner(), context.player().getGameProfile().getName());
				if(result != ScannerBlockEntity.Result.OK) {
					reply.error(result.key());
					return;
				}
				reply.ok();
			}
			case "send" -> {
				DistressNature nature = DistressNature.byName(data.getString("nature"));
				String error = data.getString("kind").equals("personal") ? sendPersonal(context, radios, nature) : sendMarine(context, radios, nature);
				if(error != null)
					reply.error(error);
				else
					reply.ok(status(context, radios));
			}
			case "cancel" -> {
				String error = data.getString("kind").equals("personal") ? cancelPersonal(context.computer(), radios) : cancelMarine(radios);
				if(error != null)
					reply.error(error);
				else
					reply.ok(status(context, radios));
			}
			default -> reply.error("unknown_request", action);
		}
	}

	/**
	 * Acknowledges DSC call {@code id} through the first powered marine radio that heard it (an error key, or null when
	 * it went out).
	 */
	@Nullable
	static String acknowledge(Radios radios, long id, String byMmsi) {
		if(radios.marines().isEmpty())
			return "distress_no_marine";
		DistressLog.Entry merged = radios.alert(id);
		if(merged != null && merged.alert().isDsc() && merged.alert().state() != DistressAlert.State.ACTIVE)
			return "distress_ack_not_active"; // One of the radios already has it acknowledged or cancelled.
		MarineRadioBlockEntity.AckResult worst = MarineRadioBlockEntity.AckResult.UNKNOWN;
		for(MarineRadioBlockEntity radio : radios.marines()) {
			MarineRadioBlockEntity.AckResult result = radio.acknowledge(id, byMmsi);
			if(result == MarineRadioBlockEntity.AckResult.OK)
				return null;
			if(result != MarineRadioBlockEntity.AckResult.UNKNOWN && worst == MarineRadioBlockEntity.AckResult.UNKNOWN)
				worst = result;
		}
		if(worst == MarineRadioBlockEntity.AckResult.UNKNOWN) { // No marine radio has it: a beacon (heard on a commercial radio), or gone.
			DistressLog.Entry entry = radios.alert(id);
			if(entry != null && !entry.alert().isDsc())
				worst = MarineRadioBlockEntity.AckResult.NOT_DSC;
			else if(entry != null && entry.alert().state() != DistressAlert.State.ACTIVE)
				worst = MarineRadioBlockEntity.AckResult.NOT_ACTIVE;
		}
		return "distress_ack_" + worst.name().toLowerCase(Locale.ROOT);
	}

	// -------------------- SENDING AN SOS --------------------

	/** Why a marine SOS cannot go now (an error key), or null. */
	@Nullable
	static String marineWhy(Radios radios) {
		if(radios.marines().isEmpty())
			return "distress_no_marine";
		if(radios.calling() != null)
			return "distress_marine_busy";
		if(radios.marineOn() == null)
			return "distress_marine_off";
		return null;
	}

	/** Why a personal SOS cannot go now (an error key), or null. */
	@Nullable
	static String personalWhy(RadioComputerBlockEntity computer, Radios radios) {
		if(radios.commercials().isEmpty())
			return "distress_no_commercial";
		if(state(computer).personal().active())
			return "distress_personal_busy";
		if(radios.commercialOn() == null)
			return "distress_commercial_off";
		return null;
	}

	/** A DSC distress call from the first powered marine radio, as its DISTRESS button sends it. */
	@Nullable
	static String sendMarine(ComputerContext context, Radios radios, DistressNature nature) {
		String why = marineWhy(radios);
		if(why != null)
			return why;
		MarineRadioBlockEntity radio = radios.marineOn();
		if(radio == null || !radio.sendDistress(Mmsi.of(context.player().getUUID()), nature))
			return "distress_marine_off";
		return null;
	}

	/** A personal SOS through the first powered commercial VHF mobile: the first burst now, then every 50 s. */
	@Nullable
	static String sendPersonal(ComputerContext context, Radios radios, DistressNature nature) {
		RadioComputerBlockEntity computer = context.computer();
		String why = personalWhy(computer, radios);
		if(why != null)
			return why;
		CommercialMobileBlockEntity radio = radios.commercialOn();
		if(radio == null)
			return "distress_commercial_off";
		long id = context.level().getRandom().nextLong();
		PersonalSos sos = PersonalSos.start(id == 0L ? 1L : id, context.player().getGameProfile().getName(), nature, radio.getBlockPos().asLong(),
				context.level().getGameTime());
		BlockPos at = radio.worldPos();
		if(!radio.sendBeaconAlert(sos.alert(at.getX(), at.getY(), at.getZ())))
			return "distress_commercial_off";
		state(computer).personal = sos;
		computer.setChanged();
		return null;
	}

	/** Cancels the outgoing DSC call of a cabled marine radio (its cancel burst goes out). */
	@Nullable
	static String cancelMarine(Radios radios) {
		MarineRadioBlockEntity radio = radios.calling();
		if(radio == null)
			return "distress_nothing_to_cancel";
		return radio.cancelDistress() ? null : "distress_nothing_to_cancel";
	}

	/** Cancels the personal SOS: the cancel burst goes out through its radio if it still can. */
	@Nullable
	static String cancelPersonal(RadioComputerBlockEntity computer, Radios radios) {
		State state = state(computer);
		if(!state.personal.active())
			return "distress_nothing_to_cancel";
		state.personal = state.personal.cancelled();
		CommercialMobileBlockEntity radio = personalRadio(radios, state.personal);
		if(radio != null) {
			BlockPos at = radio.worldPos();
			radio.sendBeaconAlert(state.personal.alert(at.getX(), at.getY(), at.getZ()));
		}
		computer.setChanged();
		return null;
	}

	@Nullable
	private static CommercialMobileBlockEntity personalRadio(Radios radios, PersonalSos sos) {
		for(CommercialMobileBlockEntity radio : radios.commercials())
			if(radio.getBlockPos().asLong() == sos.radioPos())
				return radio;
		return null;
	}

	@Override
	public boolean ticks() {
		return true;
	}

	/** The personal SOS repeats while it is on, and stops if its radio loses its power or its cable. */
	@Override
	public void tick(RadioComputerBlockEntity computer, long gameTime) {
		if((gameTime + computer.getBlockPos().asLong()) % CHECK_TICKS != 0 || computer.getLevel() == null)
			return;
		State state = state(computer);
		if(!state.personal.active())
			return;
		CommercialMobileBlockEntity radio = personalRadio(radios(computer.getLevel(), computer.getBlockPos()), state.personal);
		if(radio == null) {
			state.personal = state.personal.stopped("unlinked");
			computer.setChanged();
			return;
		}
		if(!radio.isPowered() || radio.isBlown()) {
			state.personal = state.personal.stopped("no_power");
			computer.setChanged();
			return;
		}
		if(state.personal.repeatDue(gameTime)) {
			BlockPos at = radio.worldPos();
			radio.sendBeaconAlert(state.personal.alert(at.getX(), at.getY(), at.getZ()));
			state.personal = state.personal.repeated(gameTime);
			computer.setChanged();
		}
	}

	// -------------------- STATUS AND MAPS --------------------

	/** Where the station is: the first marine radio's place in the world (on a Sable ship, the ship's), else the first commercial radio's, else the computer's. */
	private static BlockPos home(ComputerContext context, Radios radios) {
		if(!radios.marines().isEmpty())
			return radios.marines().getFirst().worldPos();
		if(!radios.commercials().isEmpty())
			return radios.commercials().getFirst().worldPos();
		return Sublevels.world(context.level(), context.pos());
	}

	private static CompoundTag status(ComputerContext context, Radios radios) {
		CompoundTag out = new CompoundTag();
		out.putBoolean("radio", radios.any());
		out.putString("mmsi", Mmsi.of(context.player().getUUID()));
		BlockPos home = home(context, radios);
		out.putInt("x", home.getX());
		out.putInt("z", home.getZ());
		int marinesOn = 0, commercialsOn = 0, beaconRadios = 0;
		for(MarineRadioBlockEntity radio : radios.marines())
			if(radio.isPowered())
				marinesOn++;
		for(CommercialMobileBlockEntity radio : radios.commercials())
			if(radio.isPowered()) {
				commercialsOn++;
				if(radio.isOnBeaconFrequency())
					beaconRadios++;
			}
		out.putInt("marines", radios.marines().size());
		out.putInt("marinesOn", marinesOn);
		out.putInt("commercials", radios.commercials().size());
		out.putInt("commercialsOn", commercialsOn);
		out.putInt("beaconRadios", beaconRadios);
		out.putBoolean("powered", marinesOn + commercialsOn > 0);
		long now = context.level().getGameTime();
		ListTag alerts = new ListTag();
		for(DistressLog.Entry entry : radios.alerts()) {
			CompoundTag a = new CompoundTag();
			a.putString("a", entry.alert().encode());
			a.putLong("age", RadioStation.secondsSince(now, entry.alert().time()));
			a.putLong("heard", RadioStation.secondsSince(now, entry.heardAt()));
			alerts.add(a);
		}
		out.put("alerts", alerts);
		String marineWhy = marineWhy(radios), personalWhy = personalWhy(context.computer(), radios);
		out.putString("marineWhy", marineWhy == null ? "" : marineWhy);
		out.putString("personalWhy", personalWhy == null ? "" : personalWhy);
		MarineRadioBlockEntity calling = radios.calling();
		if(calling != null) {
			DscCall call = calling.getDscCall();
			CompoundTag dsc = new CompoundTag();
			dsc.putString("state", call.state().name());
			dsc.putString("nature", call.nature().name());
			dsc.putString("ackBy", call.ackBy());
			dsc.putLong("next", call.ticksToRepeat(now) / 20L);
			out.put("outDsc", dsc);
		}
		PersonalSos personal = state(context.computer()).personal();
		if(personal.state() != PersonalSos.State.IDLE) {
			CompoundTag p = new CompoundTag();
			p.putString("state", personal.state().name());
			p.putString("nature", personal.nature().name());
			p.putString("reason", personal.reason());
			p.putLong("next", personal.ticksToRepeat(now) / 20L);
			out.put("outPersonal", p);
		}
		return out;
	}

	private static CompoundTag map(ComputerContext context, Radios radios) {
		BlockPos home = home(context, radios);
		List<BlockPos> at = new ArrayList<>();
		for(DistressLog.Entry entry : radios.alerts())
			at.add(new BlockPos(entry.alert().x(), entry.alert().y(), entry.alert().z()));
		AprsMapPainter.Fit fit = AprsMapPainter.fit(home, at, AprsMapPainter.SIZE, AprsMapPainter.SIZE);
		byte[] pixels = AprsMapPainter.terrain(context.level(), fit);
		AprsMapPainter.home(pixels, fit, home.getX() + 0.5D, home.getZ() + 0.5D);
		CompoundTag out = new CompoundTag();
		out.putByteArray("pixels", pixels);
		out.putInt("cx", fit.centerX());
		out.putInt("cz", fit.centerZ());
		out.putInt("bpp", fit.blocksPerPixel());
		return out;
	}

	/** About 256 blocks round {@code alert}, the station's box if it is in the picture, the marker, and the caption. */
	private static byte[] printout(ComputerContext context, Radios radios, DistressAlert alert) {
		BlockPos centre = new BlockPos(alert.x(), alert.y(), alert.z());
		AprsMapPainter.Fit fit = AprsMapPainter.fit(centre, List.of(centre.offset(-96, 0, -96), centre.offset(96, 0, 96)), AprsMapPainter.SIZE, AprsMapPainter.SIZE);
		byte[] pixels = AprsMapPainter.terrain(context.level(), fit);
		BlockPos home = home(context, radios);
		AprsMapPainter.home(pixels, fit, home.getX() + 0.5D, home.getZ() + 0.5D);
		DistressMap.marker(pixels, fit, alert, true, false);
		DistressMap.caption(pixels, fit, DistressMap.captionLines(alert));
		return pixels;
	}
}
