package com.arrl.radiocraft.common.computer.apps;

import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.blockentities.ScannerBlockEntity;
import com.arrl.radiocraft.common.blockentities.StationBus;
import com.arrl.radiocraft.common.computer.ComputerAppHandler;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerContext;
import com.arrl.radiocraft.common.computer.ComputerFileStore;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.PacketBbs;
import com.arrl.radiocraft.common.qsl.QslCard;
import com.arrl.radiocraft.common.qsl.QslCards;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The QSL Designer's server side (computer batch 3C): printing cards on the scanner, reading a card on its glass into
 * a {@code .QSL} file, sending a card to a Packet BBS, and keeping cards that come in addressed to this station.
 * <ul>
 * <li>{@code print} {@code name}, {@code count}: a template prints a run of {@code count} identical blank cards (one
 * paper or photo paper each, they stack); a card that was filled in (a received one) prints one copy of itself, marked
 * a copy. Answers {@code printed}.</li>
 * <li>{@code scan} {@code name}: the card on the glass into a {@code .QSL} file ("" for the next {@code QSL00001.QSL}).
 * Answers {@code name}.</li>
 * <li>{@code bbs} {@code name}, {@code bbs}: sends the card in the file to the BBS callsign as a file through the radio
 * interface; the BBS keeps it for the station the card is for (its To), who downloads it with {@code D n}.</li>
 * </ul>
 * A QSL card that arrives as a file addressed to this station (a BBS download, or one sent straight) is kept as
 * {@code QSL00001.QSL} and pushed as {@code received} {@code name}, {@code from}.
 */
public final class QslHandler implements ComputerAppHandler {

	@Override
	public void handle(ComputerContext context, String action, CompoundTag data, ComputerReply reply) {
		switch(action) {
			case "print" -> print(context, data, reply);
			case "scan" -> scan(context, data.getString("name"), reply);
			case "bbs" -> bbs(context, data, reply);
			default -> reply.error("unknown_request", action);
		}
	}

	private static QslCard read(ComputerContext context, String name, ComputerReply reply) {
		String file = ComputerStorage.normalise(name);
		QslCard card = file == null ? null : QslCards.fromFile(context.files().read(file));
		if(card == null)
			reply.error("qsl_not_a_card");
		return card;
	}

	private static void print(ComputerContext context, CompoundTag data, ComputerReply reply) {
		QslCard card = read(context, data.getString("name"), reply);
		if(card == null)
			return;
		ScannerBlockEntity scanner = context.scanner();
		if(scanner == null) {
			reply.error("no_scanner");
			return;
		}
		boolean template = card.blank();
		QslCard printing = template ? card.blankOfDesign() : card.asCopy();
		int[] printed = new int[1];
		ScannerBlockEntity.Result result = scanner.printCards(printing, template ? data.getInt("count") : 1, printed);
		if(result != ScannerBlockEntity.Result.OK) {
			reply.error(result.key());
			return;
		}
		CompoundTag out = new CompoundTag();
		out.putInt("printed", printed[0]);
		reply.ok(out);
	}

	private static void scan(ComputerContext context, String typed, ComputerReply reply) {
		ScannerBlockEntity scanner = context.scanner();
		if(scanner == null) {
			reply.error("no_scanner");
			return;
		}
		QslCard card = scanner.scanQsl();
		if(card == null) {
			reply.error("qsl_glass_no_card");
			return;
		}
		ComputerFileStore files = context.files();
		String name = typed.isBlank() ? ComputerStorage.nextName("QSL", QslCards.EXTENSION, files.storage()::exists) : ComputerStorage.suggest(typed, QslCards.EXTENSION);
		if(name == null) {
			reply.error(ComputerStorage.Problem.TOO_MANY_FILES);
			return;
		}
		ComputerStorage.Problem problem = files.write(name, QslCards.file(card));
		if(problem != null) {
			reply.error(problem);
			return;
		}
		CompoundTag out = new CompoundTag();
		out.putString("name", name);
		reply.ok(out);
	}

	private static void bbs(ComputerContext context, CompoundTag data, ComputerReply reply) {
		QslCard card = read(context, data.getString("name"), reply);
		if(card == null)
			return;
		String to = TncPacket.sanitizeCallsign(data.getString("bbs"));
		if(to.isEmpty()) {
			reply.error("qsl_need_bbs");
			return;
		}
		StationBus bus = RadioStation.bus(context.computer());
		String problem = RadioStation.cannotSend(bus);
		if(problem != null) {
			reply.error(problem);
			return;
		}
		if(!RadioStation.transmit(context.computer(), bus, TncPacket.file(RadioStation.newId(context.level()), bus.callsign(), to, QslCards.stack(card, 1)))) {
			reply.error("station_no_radio");
			return;
		}
		reply.ok(new CompoundTag());
	}

	@Override
	public void receive(RadioComputerBlockEntity computer, TncPacket packet) {
		if(packet.kind() != TncPacket.Kind.FILE || !(computer.getLevel() instanceof ServerLevel level))
			return;
		QslCard card = QslCards.card(packet.item());
		if(card == null || TncPacket.isForEveryone(packet.to()))
			return;
		StationBus bus = RadioStation.bus(computer);
		if(!bus.isFor(packet.to()))
			return;
		if(computer.isInstalled(ComputerApps.BBS) && BbsHandler.state(computer).online() && packet.to().equals(BbsHandler.bbsCall(computer)))
			return; // For the mailbox on this computer, not for us.
		ComputerFileStore files = computer.files();
		String name = ComputerStorage.nextName("QSL", QslCards.EXTENSION, files.storage()::exists);
		if(name == null || files.write(name, QslCards.file(card.asCopy())) != null)
			return;
		level.playSound(null, computer.getBlockPos(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.4F, 1.6F);
		CompoundTag event = new CompoundTag();
		event.putString("name", name);
		event.putString("from", packet.from());
		computer.push(ComputerApps.QSL, "received", event);
	}

	/** The BBS callsign a station's mailbox answers at, for the screen's default ({@link PacketBbs#bbsCall}). */
	static String defaultBbs(String stationCall) {
		return PacketBbs.bbsCall(stationCall, 1);
	}
}
