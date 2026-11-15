package com.arrl.radiocraft.common.data;

import com.arrl.radiocraft.api.capabilities.LicenseClass;
import com.arrl.radiocraft.api.capabilities.PlayerCallsignData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Each world keeps its own callsigns (2026-11-12: a static map carried one world's into the next opened). */
class PlayerCallsignSavedDataTest {

	@Test
	void twoWorldsKeepTheirOwnCallsigns() {
		UUID player = UUID.randomUUID();
		PlayerCallsignSavedData first = new PlayerCallsignSavedData();
		first.setCallsignData(player, new PlayerCallsignData(player.toString(), "Steve", "W1AW", LicenseClass.GENERAL));
		PlayerCallsignSavedData second = new PlayerCallsignSavedData(); // A new world opened in the same session.
		assertNull(second.getCallsignData(player));
		assertTrue(second.getCallsigns().isEmpty());
		assertEquals("W1AW", first.getCallsignData(player).callsign());
		CompoundTag saved = second.save(new CompoundTag(), null);
		assertTrue(saved.getCompound("player_callsign_data").isEmpty(), "the new world saved the old world's callsigns");
	}
}
