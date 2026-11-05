package com.arrl.radiocraft.common.radio.adsb;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What an ADS-B receiver keeps (tracks with trails, dropped when lost) and what its screens are sent (1.12 step 9). */
class AdsbTracksTest {

	private static AdsbTarget at(int icao, double x, double z, long time) {
		return new AdsbTarget(icao, icao == 1 ? "N1" : "", 1200, x, 150.0D, z, 87.0D, 90.0D, 20.0D, 0.0D, time, 0L);
	}

	@Test
	void eachReportMovesTheTrackAndLeavesATrail() {
		AdsbTracks tracks = new AdsbTracks();
		for(int s = 0; s < 5; s++)
			assertTrue(tracks.heard(at(1, s * 20.0D, 0.0D, s * 20L), s * 20L));
		AdsbTracks.Track track = tracks.get(1);
		assertNotNull(track);
		assertEquals(80.0D, track.last().x(), 1.0E-9D);
		assertEquals(4, track.trailSize(), "the four places before the latest");
		assertEquals(0.0D, track.trailX(0), 1.0E-9D, "oldest first");
		assertEquals(60.0D, track.trailX(3), 1.0E-9D);
	}

	@Test
	void aParkedCraftLeavesNoTrail() {
		AdsbTracks tracks = new AdsbTracks();
		for(int s = 0; s < 5; s++)
			tracks.heard(at(2, 5.0D, 5.0D, s * 20L), s * 20L);
		assertEquals(0, tracks.get(2).trailSize());
	}

	@Test
	void theTrailKeepsOnlyTheNewestPoints() {
		AdsbTracks tracks = new AdsbTracks();
		int reports = AdsbTracks.TRAIL + 10;
		for(int s = 0; s < reports; s++)
			tracks.heard(at(3, s, 0.0D, s * 20L), s * 20L);
		AdsbTracks.Track track = tracks.get(3);
		assertEquals(AdsbTracks.TRAIL, track.trailSize());
		assertEquals(reports - 1 - AdsbTracks.TRAIL, track.trailX(0), 1.0E-9D);
		assertEquals(reports - 2, track.trailX(AdsbTracks.TRAIL - 1), 1.0E-9D);
	}

	@Test
	void aLostTrackIsDropped() {
		AdsbTracks tracks = new AdsbTracks();
		tracks.heard(at(4, 0.0D, 0.0D, 0L), 0L);
		tracks.prune(AdsbTracks.COAST_TICKS);
		assertNotNull(tracks.get(4), "still coasting");
		tracks.prune(AdsbTracks.COAST_TICKS + 1L);
		assertNull(tracks.get(4));
		tracks.heard(at(5, 0.0D, 0.0D, 1000L), 1000L);
		tracks.prune(10L); // A new world's clock, behind the report.
		assertTrue(tracks.isEmpty());
	}

	@Test
	void theDisplayFillsUp() {
		AdsbTracks tracks = new AdsbTracks();
		for(int i = 1; i <= AdsbTracks.MAX_TRACKS; i++)
			assertTrue(tracks.heard(at(100 + i, i, 0.0D, 0L), 0L));
		assertFalse(tracks.heard(at(999, 0.0D, 0.0D, 0L), 0L));
		assertTrue(tracks.heard(at(101, 5.0D, 0.0D, 20L), 20L), "a known one still updates");
	}

	@Test
	void thePictureIsRelativeToTheReceiverNearestFirst() {
		AdsbTracks tracks = new AdsbTracks();
		tracks.heard(at(1, 1000.0D, 0.0D, 0L), 0L);
		tracks.heard(at(1, 1020.0D, 0.0D, 20L), 20L);
		tracks.heard(at(7, 110.0D, -100.0D, 20L), 20L);
		CompoundTag tag = AdsbPicture.write(true, true, true, 1000, 18000, 200, 30, 100.0D, -100.0D, tracks, 60L);
		assertTrue(tag.getBoolean("antenna"));
		assertEquals(1000, tag.getInt("range"));
		assertEquals(18000, tag.getInt("reach"));
		assertEquals(200, tag.getInt("reachLow"));
		List<AdsbPicture.Contact> contacts = AdsbPicture.contacts(tag);
		assertEquals(2, contacts.size());
		AdsbPicture.Contact near = contacts.get(0), far = contacts.get(1);
		assertEquals(7, near.icao());
		assertEquals("000007", near.label(), "no flight ID: the address");
		assertEquals(10.0F, near.dx(), 1.0E-4F);
		assertEquals(0.0F, near.dz(), 1.0E-4F);
		assertEquals("N1", far.label());
		assertEquals(920.0F, far.dx(), 1.0E-4F);
		assertEquals(100.0F, far.dz(), 1.0E-4F);
		assertEquals(2, far.age(), "40 ticks since its last report");
		assertEquals(285, far.feet());
		assertEquals(39, far.knots());
		assertEquals(90, far.track());
		assertEquals(2, far.trail().length, "one trail point, as dx and dz");
		assertEquals(900, far.trail()[0]);
		assertEquals(100, far.trail()[1]);
		assertEquals(90.0D, near.bearing(), 1.0E-6D);
		assertEquals(10.0D, near.distance(), 1.0E-6D);
	}
}
