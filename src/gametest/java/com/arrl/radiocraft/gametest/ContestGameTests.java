package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.advancements.AdvancementData;
import com.arrl.radiocraft.common.advancements.ContactTracker;
import com.arrl.radiocraft.common.advancements.RadiocraftAdvancements;
import com.arrl.radiocraft.common.contest.ContestData;
import com.arrl.radiocraft.common.contest.ContestReport;
import com.arrl.radiocraft.common.contest.Contests;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * {@code /contest} (2026-11-09): a verified two-way contact made while a contest runs goes in its log with both names,
 * the frequency each heard, both heights, the mode each sent and the biome at each station; the report book is made
 * from it. The contest is shared by the whole server, so this looks only at its own players' contacts (others' tests
 * may make contacts meanwhile) and always stops the contest it started.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class ContestGameTests {

	private static final float HF = 14_230_000.0F;

	private static FakePlayer player(GameTestHelper helper, String name) {
		FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("contest-" + name).getBytes()), name));
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		player.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
		RadiocraftAdvancements.registerTestPlayer(player);
		return player;
	}

	/** A contact made during a contest is logged as it was made; one made with no contest running is not. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void aContactDuringAContestIsLogged(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		FakePlayer a = player(helper, "contesta");
		FakePlayer b = player(helper, "contestb");
		ContestData data = ContestData.of(server);
		helper.assertFalse(data.running(), "a contest was already running");
		try {
			helper.assertTrue(Contests.start(server, "GameTest Sprint"), "the contest did not start");
			helper.assertFalse(Contests.start(server, "Another"), "a second contest started over the first");
			BlockPos stationA = a.blockPosition();
			BlockPos stationB = new BlockPos(stationA.getX() + 600, 112, stationA.getZ());
			ContactTracker.heard(helper.getLevel(), b.getUUID(), a.getUUID(), stationB, HF, "LSB"); // B heard A, on LSB.
			ContactTracker.heard(helper.getLevel(), a.getUUID(), b.getUUID(), stationA, HF, "USB"); // A heard B, on USB.
			ContestReport.Qso qso = data.qsos().stream()
					.filter(q -> Contests.participants(List.of(q)).containsAll(List.of(a.getUUID(), b.getUUID())))
					.findFirst().orElse(null);
			helper.assertTrue(qso != null, "the contact was not logged: " + data.qsos());
			helper.assertTrue(qso.a().equals(a.getUUID()) && qso.aName().equals("contesta") && qso.bName().equals("contestb"), "names: " + qso);
			helper.assertTrue(qso.aMode().equals("LSB") && qso.bMode().equals("USB"), "modes: " + qso);
			helper.assertTrue(qso.aY() == stationA.getY() && qso.bY() == 112, "heights: " + qso);
			helper.assertTrue(qso.aHeardHz() == HF && qso.bHeardHz() == HF, "frequencies: " + qso);
			helper.assertTrue(Math.abs(qso.distance() - 600.0D) < 0.5D, "distance: " + qso);
			helper.assertTrue(qso.aBiome().contains(":") && qso.bBiome().contains(":"), "biomes: " + qso);
			String side = qso.sideA().mode();
			helper.assertTrue(side.equals("LSB/USB"), "A's end reads " + side);
		}
		finally {
			ContestReport.Report report = Contests.stop(server, null);
			helper.assertTrue(report != null && !data.running(), "the contest did not stop");
		}
		helper.assertTrue(Contests.stop(server, null) == null, "stopping twice made a second report");
		RadiocraftAdvancements.unregisterTestPlayer(a);
		RadiocraftAdvancements.unregisterTestPlayer(b);
		AdvancementData.of(server).takePending(a.getUUID());
		AdvancementData.of(server).takePending(b.getUUID());
		helper.succeed();
	}

	/** The book: a written book titled with the contest's name, by RadioCraft, whose pages are the report's. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 20)
	public static void theReportIsAWrittenBook(GameTestHelper helper) {
		ContestReport.Report report = ContestReport.build("Sprint", 0L, 3_600_000L, java.time.ZoneId.of("UTC"), List.of());
		ItemStack book = Contests.book("Sprint", report);
		helper.assertTrue(book.is(Items.WRITTEN_BOOK), "not a written book");
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		helper.assertTrue(content != null && content.title().raw().equals("Sprint") && content.author().equals("RadioCraft"), "title or author: " + content);
		helper.assertTrue(content.pages().size() == report.pages().size(), "pages: " + content.pages().size());
		helper.assertTrue(Contests.book("", report).get(DataComponents.WRITTEN_BOOK_CONTENT).title().raw().equals("Contest Report"), "an unnamed contest's title");
		helper.succeed();
	}
}
