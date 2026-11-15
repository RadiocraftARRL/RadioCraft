package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.radio.commercial.CommercialProgramming;
import com.arrl.radiocraft.common.items.CommercialLicenseItem;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicense;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenseRules;
import com.arrl.radiocraft.common.radio.commercial.CommercialLicenses;
import net.minecraft.nbt.CompoundTag;

/**
 * The radio programmer's server side (CPS, 1.12 step 6), on {@link CommercialProgramming}: the radios the computer
 * reaches ({@link CommercialTargets}), reading and writing their channels (16, or 40 on the upgraded handheld), and a factory reset. Keys a radio marks as
 * not readable never leave the server: they come back as {@code KEY_HIDDEN} and, written back, keep the radio's key.
 * <ul>
 * <li>{@code radios}: {@code radios}, the radios on the glass and on the wire.</li>
 * <li>{@code read} ({@code radio}): the radio, its {@code channels} and {@code selected} knob position.</li>
 * <li>{@code write} ({@code radio}, {@code channels}): all or nothing; the answer is the radio read back with
 * {@code problems} (empty when written).</li>
 * <li>{@code factory} ({@code radio}): back to the factory channels; answers like {@code read}.</li>
 * <li>{@code license} ({@code channels}): whether the player must confirm they are licensed first; see
 * {@link #licenseCheck}.</li>
 * <li>{@code hand} ({@code on}, {@code plug}): the "Program radios" mode on (with the codeplug to write) or off; see
 * {@link HandProgramming}.</li>
 * </ul>
 */
public final class ProgrammerHandler implements ComputerAppHandler {

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		if(action.equals("hand")) { // The "Program radios" mode (1.12 playtest fixes 2).
			HandProgramming.request(context, data, reply);
			return;
		}
		if(action.equals("license")) { // Commercial licenses (2026-11-10): is the license in the scanner good for these channels?
			reply.ok(licenseCheck(context, CommercialTargets.channels(data)));
			return;
		}
		if(action.equals("radios")) {
			reply.ok(CommercialTargets.radios(context));
			return;
		}
		CommercialTargets.Found radio = CommercialTargets.find(context, data.getString("radio"));
		if(radio == null) {
			reply.error(data.getString("radio").isEmpty() ? "no_radio" : "radio_gone");
			return;
		}
		switch(action) {
			case "read" -> reply.ok(CommercialTargets.read(radio));
			case "write" -> reply.ok(CommercialTargets.write(radio, CommercialTargets.channels(data), data.getInt("unit"),
					data.contains("contacts") ? com.arrl.radiocraft.common.computer.Codeplug.contacts(data) : null));
			case "factory" -> {
				CommercialProgramming.factoryReset(radio.target());
				radio.saved().run();
				reply.ok(CommercialTargets.read(radio));
			}
			default -> reply.error("unknown_request", action);
		}
	}

	/**
	 * Whether writing {@code channels} needs the player to confirm they are licensed (2026-11-10): {@code licensed} is
	 * true when licensing is off, when no channel transmits on a frequency that needs a commercial license
	 * ({@link CommercialLicenseRules#needsLicense}), or when every one is covered by an original license on the scanner's
	 * glass or anywhere in the player's inventory (2026-11-11: the scanner is no longer needed, any original carried
	 * counts). Otherwise {@code unlicensed} lists those frequencies (MHz) and {@code copy} says the glass holds only a copy.
	 */
	public static net.minecraft.nbt.CompoundTag licenseCheck(ComputerContext context, java.util.List<com.arrl.radiocraft.common.radio.commercial.CommercialChannel> channels) {
		net.minecraft.nbt.CompoundTag out = new net.minecraft.nbt.CompoundTag();
		net.minecraft.nbt.ListTag unlicensed = new net.minecraft.nbt.ListTag();
		if(com.arrl.radiocraft.RadiocraftServerConfig.COMMERCIAL_LICENSING.get() != com.arrl.radiocraft.RadiocraftServerConfig.LicensingMode.OFF) {
			com.arrl.radiocraft.common.blockentities.ScannerBlockEntity scanner = context.scanner();
			net.minecraft.world.item.ItemStack bed = scanner == null ? net.minecraft.world.item.ItemStack.EMPTY : scanner.bed();
			CommercialLicense onGlass = CommercialLicenseItem.license(bed);
			CommercialLicenses registry = CommercialLicenses.of(context.server());
			java.util.List<CommercialLicense> held = new java.util.ArrayList<>();
			if(onGlass != null && onGlass.original() && registry.get(onGlass.callsign()) != null)
				held.add(registry.get(onGlass.callsign()));
			if(context.player() != null)
				for(net.minecraft.world.item.ItemStack stack : context.player().getInventory().items) {
					CommercialLicense carried = CommercialLicenseItem.license(stack);
					if(carried != null && carried.original() && registry.get(carried.callsign()) != null)
						held.add(registry.get(carried.callsign()));
				}
			if(context.player() != null) {
				CommercialLicense offhand = CommercialLicenseItem.license(context.player().getOffhandItem());
				if(offhand != null && offhand.original() && registry.get(offhand.callsign()) != null)
					held.add(registry.get(offhand.callsign()));
			}
			if(onGlass != null) {
				out.putString("callsign", onGlass.callsign());
				out.putBoolean("copy", onGlass.copy());
			}
			java.util.Set<String> seen = new java.util.LinkedHashSet<>();
			for(com.arrl.radiocraft.common.radio.commercial.CommercialChannel channel : channels) {
				float tx = channel.txHz();
				if(CommercialLicenseRules.needsLicense(tx) && held.stream().noneMatch(license -> license.covers(tx)) && seen.add(CommercialLicenseRules.mhz(tx)))
					unlicensed.add(net.minecraft.nbt.StringTag.valueOf(CommercialLicenseRules.mhz(tx)));
			}
		}
		out.putBoolean("licensed", unlicensed.isEmpty());
		out.put("unlicensed", unlicensed);
		return out;
	}
}
