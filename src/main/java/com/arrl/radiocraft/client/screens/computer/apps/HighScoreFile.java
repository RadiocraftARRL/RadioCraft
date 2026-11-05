package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.games.HighScores;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/**
 * A game's high score table on the computer's disk (1.12 step 8): read when the game starts, written when a score makes
 * it. A disk that is full or a file that is not a table just means no table; the game plays the same.
 */
final class HighScoreFile {

	private final ComputerSession session;
	private final String name;
	private HighScores scores = new HighScores();
	private boolean loaded;

	HighScoreFile(ComputerSession session, String name) {
		this.session = session;
		this.name = name;
	}

	/** Reads the table (missing is empty). */
	void load() {
		session.files().read(name, response -> {
			loaded = true;
			if(!response.ok())
				return;
			CompoundTag tag = ComputerFileFormats.nbt(response.data().getByteArray("data"));
			scores = HighScores.fromTag(tag);
		});
	}

	HighScores scores() {
		return scores;
	}

	boolean loaded() {
		return loaded;
	}

	/** Adds the player's {@code score}; writes the table if it made it. Returns its place, or -1. */
	int submit(int score) {
		int place = scores.add(Minecraft.getInstance().getUser().getName(), score);
		if(place >= 0)
			session.files().write(name, ComputerFileFormats.nbt(scores.toTag()), response -> {});
		return place;
	}
}
