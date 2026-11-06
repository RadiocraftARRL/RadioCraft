package com.arrl.radiocraft.gametest;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.RadioComputerBlockEntity;
import com.arrl.radiocraft.common.computer.ComputerApps;
import com.arrl.radiocraft.common.computer.ComputerReply;
import com.arrl.radiocraft.common.computer.ComputerServer;
import com.arrl.radiocraft.common.computer.license.LicensePool;
import com.arrl.radiocraft.common.computer.license.LicensePools;
import com.arrl.radiocraft.common.computer.license.LicenseProgress;
import com.arrl.radiocraft.common.computer.license.StudyDeck;
import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * License Study (computer apps 2, 2026-11-06), server side, driven as its screen drives it: the shipped pools are
 * loaded with the data packs, a study card answered right, wrong and "I don't know" lands on the player's own deck in
 * {@link LicenseProgress} (another player's stays empty), and a practice exam draws one question per group and grades
 * with the pass mark.
 */
@GameTestHolder(Radiocraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class LicenseStudyGameTests {

	private static final BlockPos AT = new BlockPos(4, 2, 4);

	private static FakePlayer player(GameTestHelper helper, String name) {
		return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("licensestudy-" + name).getBytes()), name));
	}

	private static RadioComputerBlockEntity computer(GameTestHelper helper) {
		helper.setBlock(AT, RadiocraftBlocks.RADIO_COMPUTER.get());
		helper.setBlock(AT.above(), RadiocraftBlocks.COMPUTER_MONITOR.get());
		RadioComputerBlockEntity computer = GameTestUtil.blockEntity(helper, AT, RadioComputerBlockEntity.class);
		computer.install(ComputerApps.LICENSE);
		return computer;
	}

	private static CompoundTag app(RadioComputerBlockEntity computer, FakePlayer player, String action, CompoundTag data) {
		CompoundTag wrapped = new CompoundTag();
		wrapped.putString("app", ComputerApps.LICENSE.id());
		wrapped.putString("action", action);
		wrapped.put("data", data);
		CompoundTag[] out = new CompoundTag[1];
		ComputerServer.handle(computer, player, "app", wrapped, new ComputerReply(player, 9, tag -> out[0] = tag));
		return out[0] == null ? new CompoundTag() : out[0];
	}

	private static CompoundTag ok(GameTestHelper helper, CompoundTag reply, String what) {
		helper.assertFalse(reply.contains("error"), what + " failed: " + reply);
		return reply.getCompound("data");
	}

	private static CompoundTag pool(String id) {
		CompoundTag tag = new CompoundTag();
		tag.putString("pool", id);
		return tag;
	}

	/** The three shipped pools are offered; cards answered go on the player's own deck. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void studyCardsAreKeptPerPlayer(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper);
		FakePlayer student = player(helper, "student");
		FakePlayer other = player(helper, "other");
		LicenseProgress.get(helper.getLevel().getServer()).deck(student.getUUID(), "technician").clear();
		helper.startSequence().thenExecute(() -> {
			ListTag pools = ok(helper, app(computer, student, "pools", new CompoundTag()), "pools").getList("pools", Tag.TAG_COMPOUND);
			helper.assertTrue(pools.size() >= 3, "the shipped pools are not all loaded: " + pools);
			helper.assertTrue(pools.getCompound(0).getString("id").equals("technician") && pools.getCompound(0).getInt("examSize") == 35
					&& pools.getCompound(0).getInt("passMark") == 26, "technician first, 35 questions, 26 to pass: " + pools.getCompound(0));
			LicensePool technician = LicensePools.get("technician");
			helper.assertTrue(technician != null, "no technician pool");

			CompoundTag next = pool("technician");
			next.putString("sub", "T1");
			CompoundTag card = ok(helper, app(computer, student, "next", next), "next");
			helper.assertTrue(card.getString("id").startsWith("T1") && card.getBoolean("new"), "a new T1 card: " + card);
			int right = technician.question(card.getString("id")).correct();

			CompoundTag answer = pool("technician");
			answer.putString("id", card.getString("id"));
			answer.putInt("choice", right);
			CompoundTag result = ok(helper, app(computer, student, "answer", answer), "answer");
			helper.assertTrue(result.getBoolean("right") && result.getInt("correct") == right && result.getInt("level") == 1, "a right answer: " + result);
			answer.putInt("choice", (right + 1) % 4);
			result = ok(helper, app(computer, student, "answer", answer), "wrong answer");
			helper.assertTrue(!result.getBoolean("right") && result.getInt("level") == 0 && result.getLong("again") == StudyDeck.WRONG_AGAIN, "a wrong answer: " + result);
			answer.putInt("choice", -1);
			result = ok(helper, app(computer, student, "answer", answer), "idk");
			helper.assertTrue(result.getLong("again") == StudyDeck.IDK_AGAIN, "I don't know: " + result);

			LicenseProgress progress = LicenseProgress.get(helper.getLevel().getServer());
			StudyDeck.Card stored = progress.deck(student.getUUID(), "technician").card(card.getString("id"));
			helper.assertTrue(stored != null && stored.correct() == 1 && stored.wrong() == 1 && stored.idk() == 1, "the card is not on the student's deck");
			helper.assertFalse(progress.has(other.getUUID(), "technician"), "the other player has the student's progress");
			CompoundTag mine = ok(helper, app(computer, student, "progress", pool("technician")), "progress");
			helper.assertTrue(mine.getLong("answered") == 3 && mine.getList("subs", Tag.TAG_COMPOUND).size() == 10, "progress: " + mine);

			helper.assertTrue(app(computer, student, "next", pool("nonesuch")).getString("error").equals("study_unknown_pool"), "an unknown pool");
		}).thenSucceed();
	}

	/** An exam is one question per group with no answers sent; all right passes, none right fails. */
	@GameTest(template = GameTestUtil.EMPTY, timeoutTicks = 40)
	public static void practiceExamGrades(GameTestHelper helper) {
		RadioComputerBlockEntity computer = computer(helper);
		FakePlayer student = player(helper, "examinee");
		LicenseProgress.get(helper.getLevel().getServer()).deck(student.getUUID(), "extra").clear();
		helper.startSequence().thenExecute(() -> {
			CompoundTag exam = ok(helper, app(computer, student, "exam", pool("extra")), "exam");
			ListTag questions = exam.getList("questions", Tag.TAG_COMPOUND);
			helper.assertTrue(questions.size() == 50 && exam.getInt("passMark") == 37, "extra: 50 questions, 37 to pass: " + questions.size());
			java.util.Set<String> groups = new java.util.HashSet<>();
			LicensePool extra = LicensePools.get("extra");
			ListTag ids = new ListTag();
			int[] right = new int[questions.size()];
			for(int i = 0; i < questions.size(); i++) {
				CompoundTag question = questions.getCompound(i);
				helper.assertFalse(question.contains("correct"), "the exam sent an answer");
				helper.assertTrue(groups.add(question.getString("id").substring(0, 3)), "two questions from one group");
				LicensePool.Question q = extra.question(question.getString("id"));
				helper.assertFalse(q.hasFigure(), "a figure question on the exam");
				ids.add(StringTag.valueOf(q.id()));
				right[i] = q.correct();
			}
			CompoundTag grade = pool("extra");
			grade.put("ids", ids);
			grade.putIntArray("choices", right);
			CompoundTag result = ok(helper, app(computer, student, "grade", grade), "grade");
			helper.assertTrue(result.getBoolean("passed") && result.getInt("score") == 50, "all right did not pass: " + result);
			grade.putIntArray("choices", new int[0]);
			result = ok(helper, app(computer, student, "grade", grade), "grade blank");
			helper.assertTrue(!result.getBoolean("passed") && result.getInt("score") == 0 && result.getIntArray("correct").length == 50, "a blank exam: " + result);
			StudyDeck deck = LicenseProgress.get(helper.getLevel().getServer()).deck(student.getUUID(), "extra");
			helper.assertTrue(deck.step() == 100, "the exam's answers did not count on the cards: " + deck.step());
			ok(helper, app(computer, student, "reset", pool("extra")), "reset");
			helper.assertTrue(deck.step() == 0 && deck.isEmpty(), "reset left cards");
		}).thenSucceed();
	}
}
