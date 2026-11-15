package com.arrl.radiocraft.common.radio;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoaxLossTest {

	@Test
	void lightOnHfMoreOnVhf() {
		assertEquals(1.0D, CoaxLoss.db(50, 144_000_000.0D, 1.0D), 1e-9, "50 blocks on 2m lose 1 dB");
		assertEquals(0.25D, CoaxLoss.db(50, 14_200_000.0D, 1.0D), 1e-9, "the same run on 20m a quarter dB");
		assertTrue(CoaxLoss.factor(50, 144_000_000.0D, 1.0D) > 0.79D && CoaxLoss.factor(50, 144_000_000.0D, 1.0D) < 0.8D);
		assertTrue(CoaxLoss.factor(10, 3_600_000.0D, 1.0D) > 0.99D, "a short 80m run barely matters");
	}

	@Test
	void configScalesItAndZeroIsLossless() {
		assertEquals(1.0D, CoaxLoss.factor(200, 146_000_000.0D, 0.0D), 1e-12);
		assertEquals(2.0D * CoaxLoss.db(30, 28_400_000.0D, 1.0D), CoaxLoss.db(30, 28_400_000.0D, 2.0D), 1e-12);
		assertEquals(1.0D, CoaxLoss.factor(0, 146_000_000.0D, 1.0D), 1e-12);
	}

	@Test
	void runLengthAlongTheGrid() {
		assertEquals(7, CoaxLoss.length(new BlockPos(0, 0, 0), new BlockPos(3, 2, -2)));
	}
}
