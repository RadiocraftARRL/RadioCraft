package com.arrl.radiocraft.api;

import com.arrl.radiocraft.api.antenna.SimpleAntennaType;
import com.arrl.radiocraft.api.radio.RadioBands;
import com.arrl.radiocraft.api.radio.RadioSpec;
import com.arrl.radiocraft.common.radio.Band;
import com.arrl.radiocraft.common.radio.BandEdges;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure parts of the companion mod API (1.12 step 12): registering bands, the radio spec and the simple antenna
 * type's coverage. No game bootstrap: only registered bands are looked up (a stock band would read the server config).
 */
class CompanionApiTest {

	private static final Band SEVENTY_CM = new Band("unit_70cm", 300, 0, 0, 0, 0, 420_000_000.0F, 450_000_000.0F);
	private static final Band SERVICE = new Band("unit_service", 400, 0, 0, 0, 0, 220_000_000.0F, 222_000_000.0F);

	static {
		RadioBands.register(SEVENTY_CM, true, null, "70cm");
		RadioBands.register(SERVICE, false, "2m", null);
	}

	@Test
	void registeredBandsAreFoundByName() {
		assertSame(SEVENTY_CM, RadioBands.get("unit_70cm"));
		assertTrue(RadioBands.registered().contains(SERVICE));
		assertTrue(Band.isRegistered("unit_service"));
		assertFalse(Band.isRegistered("20m"));
	}

	@Test
	void registeredBandsKeepTheirSettings() {
		assertTrue(SEVENTY_CM.isAmateur());
		assertTrue(SEVENTY_CM.isVhf(), "From 100 MHz a band is on the VHF network");
		assertEquals("70cm", SEVENTY_CM.displayName());
		assertEquals("unit_70cm", SEVENTY_CM.conditionName(), "No solarLike: its own name");
		assertFalse(SERVICE.isAmateur());
		assertEquals("2m", SERVICE.conditionName());
		assertEquals("unit_service", SERVICE.displayName());
	}

	@Test
	void amateurBandsGetTheMarsExtensionCommercialOnesDoNot() {
		assertEquals(412_500_000.0D, BandEdges.tuneMin(SEVENTY_CM), 1.0D); // A quarter of 30 MHz below.
		assertEquals(220_000_000.0D, BandEdges.tuneMin(SERVICE), 1.0D);
		assertTrue(RadioBands.canTransmit(SEVENTY_CM, 415_000_000.0D, true));
		assertFalse(RadioBands.canTransmit(SEVENTY_CM, 415_000_000.0D, false));
		assertFalse(RadioBands.canTransmit(SERVICE, 223_000_000.0D, true));
	}

	@Test
	void takenNamesAreRefused() {
		assertThrows(IllegalArgumentException.class, () -> RadioBands.register(new Band("20m", 1, 0, 0, 0, 0, 1, 2), true, null, null));
		assertThrows(IllegalArgumentException.class, () -> RadioBands.register(new Band(Band.LOW_BAND, 1, 0, 0, 0, 0, 1, 2), false, null, null));
		assertThrows(IllegalArgumentException.class, () -> RadioBands.register(SEVENTY_CM, true, null, null));
	}

	@Test
	void radioSpecsCheckTheirValues() {
		RadioSpec fm = RadioSpec.fm(RadioBands.LOW_BAND, 12_500);
		assertTrue(fm.fm());
		assertFalse(fm.tunesOutOfBand());
		assertEquals(13, fm.transmitUse());
		RadioSpec ssb = RadioSpec.ssb(RadioBands.M20).withPower(-5, 3);
		assertFalse(ssb.fm());
		assertTrue(ssb.tunesOutOfBand());
		assertEquals(0, ssb.transmitUse(), "Negative power use is taken as none");
		assertEquals(3, ssb.receiveUse());
		assertThrows(IllegalArgumentException.class, () -> RadioSpec.fm(RadioBands.LOW_BAND, 0));
	}

	@Test
	void simpleAntennaCoversItsBandsAndRanges() {
		SimpleAntennaType type = SimpleAntennaType.builder(ResourceLocation.fromNamespaceAndPath("unit", "beam"))
				.matcher((level, pos) -> false)
				.band("unit_70cm", 1.1D, 3.0D)
				.range(1_800_000.0D, 2_000_000.0D, 1.3D, -2.0D)
				.outsideSwr(8.0D)
				.lightningDamageFactor(0.5D)
				.build();
		assertEquals(1.1D, type.getSWR(null, 435_000_000.0F), 1e-9);
		assertEquals(3.0D, type.gainDbiAt(435_000_000.0D), 1e-9);
		assertEquals(1.3D, type.getSWR(null, 1_900_000.0F), 1e-9);
		assertEquals(-2.0D, type.gainDbiAt(1_900_000.0D), 1e-9);
		assertEquals(8.0D, type.getSWR(null, 100_000_000.0F), 1e-9);
		assertEquals(0.0D, type.gainDbiAt(100_000_000.0D), 1e-9);
		assertNull(type.coverageAt(100_000_000.0D));
		assertEquals(0.5D, type.lightningDamageFactor(), 1e-9);
		assertThrows(IllegalStateException.class, () -> SimpleAntennaType.builder(ResourceLocation.fromNamespaceAndPath("unit", "none")).build());
	}
}
