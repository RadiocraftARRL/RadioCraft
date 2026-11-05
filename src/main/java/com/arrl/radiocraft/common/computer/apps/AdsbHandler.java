package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.blockentities.adsb.AdsbReceiverBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerReply;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The ADS-B program's server side (1.12 step 9): the computer as a second display of an ADS-B receiver cabled to it
 * (touching, or on its data wire; the first one found). It reads the receiver's tracks, as the receiver's own screen
 * does, and can switch it on; the range and tags are the program's own (client side).
 * <ul>
 * <li>{@code status}: {@code receiver} (one is cabled) and the receiver's picture
 * ({@link com.arrl.radiocraft.common.radio.adsb.AdsbPicture}).</li>
 * <li>{@code power}: switches the receiver on or off; error {@code adsb_no_receiver} without one.</li>
 * </ul>
 */
public final class AdsbHandler implements ComputerAppHandler {

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		AdsbReceiverBlockEntity receiver = receiver(context);
		switch(action) {
			case "status" -> {
				CompoundTag out = receiver == null ? new CompoundTag() : receiver.picture();
				out.putBoolean("receiver", receiver != null);
				reply.ok(out);
			}
			case "power" -> {
				if(receiver == null) {
					reply.error("adsb_no_receiver");
					return;
				}
				receiver.togglePower();
				reply.ok();
			}
			default -> reply.error("unknown_request", action);
		}
	}

	/** The ADS-B receiver cabled to the computer, or null. */
	@Nullable
	public static AdsbReceiverBlockEntity receiver(ComputerContext context) {
		List<AdsbReceiverBlockEntity> found = context.linked(AdsbReceiverBlockEntity.class);
		return found.isEmpty() ? null : found.getFirst();
	}
}
