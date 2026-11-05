package com.arrl.radiocraft.common.radio.bandplan;

import com.arrl.radiocraft.common.radio.Band;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The default band plan reads back whole and is true to the band table. */
class BandPlanTest {

	private static final BandPlan PLAN = BandPlan.defaults();

	@Test
	void everyDefaultEntryParses() {
		assertEquals(BandPlan.DEFAULT_SEGMENTS.size(), PLAN.segments().size(), "a default segment did not parse");
		assertEquals(BandPlan.DEFAULT_MARKERS.size(), PLAN.markers().size(), "a default marker did not parse");
	}

	@Test
	void defaultSegmentsDoNotOverlapAndStayInsideABand() {
		List<BandPlan.Segment> segments = PLAN.segments();
		for(int i = 1; i < segments.size(); i++)
			assertTrue(segments.get(i - 1).toHz() <= segments.get(i).fromHz(), segments.get(i - 1) + " overlaps " + segments.get(i));
		for(BandPlan.Segment segment : segments)
			assertTrue(Band.getAllDefaults().stream().anyMatch(band -> segment.fromHz() >= band.minFrequency() && segment.toHz() <= band.maxFrequency()),
					segment + " is outside every band");
	}

	@Test
	void everyDefaultBandIsCoveredEdgeToEdge() {
		for(BandPlan.Row row : PLAN.rows(Band.getAllDefaults())) {
			double covered = row.segments().stream().mapToDouble(segment -> segment.toHz() - segment.fromHz()).sum();
			assertEquals(row.band().maxFrequency() - row.band().minFrequency(), covered, 1.0D, row.band().name() + " has gaps");
		}
	}

	@Test
	void theUsualFrequenciesAreWhereTheyShouldBe() {
		assertEquals("Data (APRS, packet)", PLAN.useAt(144_390_000));
		assertFalse(PLAN.markersNear(144_390_000, 1).isEmpty(), "144.390 APRS marker");
		assertFalse(PLAN.markersNear(7_060_000, 1).isEmpty(), "7.060 server-wide net marker");
		assertEquals("FM simplex", PLAN.useAt(146_520_000));
		assertEquals("MURS (licence free)", PLAN.useAt(151_880_000));
		assertEquals("Military / federal", PLAN.useAt(165_000_000));
		assertEquals("Marine VHF", PLAN.useAt(156_800_000));
		assertEquals("Military / federal", PLAN.useAt(30_100_000), "low band starts with a military pocket");
		assertEquals("CW", PLAN.useAt(14_010_000));
		assertEquals("SSTV / fax", PLAN.useAt(14_230_000));
		assertNull(PLAN.useAt(100_000_000), "nothing at 100 MHz");
	}

	@Test
	void parsingRejectsNonsense() {
		assertNull(BandPlan.parseSegment("144.0-144.1|CW"), "no colour");
		assertNull(BandPlan.parseSegment("144.1-144.0|CW|cw"), "backwards");
		assertNull(BandPlan.parseSegment("abc-144.1|CW|cw"));
		assertNull(BandPlan.parseSegment("144.0-144.1|CW|tartan"), "unknown colour");
		assertNull(BandPlan.parseMarker("APRS"));
		assertNull(BandPlan.parseMarker("144.390|"));
		BandPlan.Segment custom = BandPlan.parseSegment(" 7.000 - 7.040 | Club CW | #FF8800 ");
		assertNotNull(custom);
		assertEquals(0xFF8800, custom.rgb());
		assertEquals(7_000_000.0D, custom.fromHz(), 0.0D);
		BandPlan.Marker marker = BandPlan.parseMarker("7.060|Net | with a bar");
		assertNotNull(marker);
		assertEquals("Net | with a bar", marker.label());
	}

	@Test
	void rowsClipSegmentsToTheirBand() {
		BandPlan plan = BandPlan.parse(List.of("13.900-14.100|Wide|cw"), List.of());
		Band twenty = new Band("20m", 300, 2000, 6000, 800, 1000, 14_000_000, 14_350_000);
		BandPlan.Row row = plan.rows(List.of(twenty)).getFirst();
		assertEquals(14_000_000.0D, row.segments().getFirst().fromHz(), 0.0D);
		assertEquals(List.of("cw"), plan.legendColours());
	}
}
