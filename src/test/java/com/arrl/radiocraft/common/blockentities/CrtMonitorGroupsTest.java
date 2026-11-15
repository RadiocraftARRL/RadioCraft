package com.arrl.radiocraft.common.blockentities;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** External CRT Monitors joined into screens, and each one's slice of the picture with the bezels between (2026-11-12). */
class CrtMonitorGroupsTest {

	private static Set<Long> rect(int a0, int b0, int w, int h) {
		Set<Long> cells = new HashSet<>();
		for(int a = a0; a < a0 + w; a++)
			for(int b = b0; b < b0 + h; b++)
				cells.add(CrtMonitorGroups.cell(a, b));
		return cells;
	}

	@Test
	void aFullRectangleIsOneScreen() {
		Set<Long> wall = rect(0, 0, 3, 2);
		List<CrtMonitorGroups.Rect> screens = CrtMonitorGroups.partition(wall);
		assertEquals(List.of(new CrtMonitorGroups.Rect(0, 0, 3, 2)), screens);
		for(int a = 0; a < 3; a++)
			for(int b = 0; b < 2; b++)
				assertEquals(new CrtMonitorGroups.Rect(0, 0, 3, 2), CrtMonitorGroups.groupOf(a, b, wall), "every member agrees");
		// Seen from another member (cells numbered from it), the same screen.
		Set<Long> shifted = rect(-2, -1, 3, 2);
		assertEquals(new CrtMonitorGroups.Rect(-2, -1, 3, 2), CrtMonitorGroups.groupOf(0, 0, shifted));
	}

	@Test
	void breakingOneSplitsTheScreen() {
		Set<Long> wall = rect(0, 0, 3, 2);
		wall.remove(CrtMonitorGroups.cell(1, 1)); // The top middle goes.
		List<CrtMonitorGroups.Rect> screens = CrtMonitorGroups.partition(wall);
		assertEquals(3, screens.size(), "split into: " + screens);
		assertEquals(new CrtMonitorGroups.Rect(0, 0, 3, 1), CrtMonitorGroups.groupOf(1, 0, wall), "the bottom row stays one screen");
		assertEquals(new CrtMonitorGroups.Rect(0, 1, 1, 1), CrtMonitorGroups.groupOf(0, 1, wall));
		assertEquals(new CrtMonitorGroups.Rect(2, 1, 1, 1), CrtMonitorGroups.groupOf(2, 1, wall));
		int covered = 0;
		for(CrtMonitorGroups.Rect r : screens)
			covered += r.w() * r.h();
		assertEquals(wall.size(), covered, "every display in exactly one screen");
	}

	@Test
	void anLShapeIsCutTheSameWayFromEveryMember() {
		Set<Long> l = rect(0, 0, 4, 1);
		l.addAll(rect(0, 1, 1, 3));
		CrtMonitorGroups.Rect first = CrtMonitorGroups.groupOf(0, 0, l);
		for(long c : l) {
			int a = (int)(c >> 32), b = (int)c;
			CrtMonitorGroups.Rect r = CrtMonitorGroups.groupOf(a, b, l);
			assertTrue(r.contains(a, b));
			if(first.contains(a, b))
				assertEquals(first, r);
		}
		assertEquals(4 * 1, first.w() * first.h(), "the bottom-left cell takes the larger piece: " + first);
	}

	@Test
	void aScreenIsAtMostEightBySix() {
		Set<Long> huge = rect(0, 0, 12, 9);
		for(CrtMonitorGroups.Rect r : CrtMonitorGroups.partition(huge))
			assertTrue(r.w() <= CrtMonitorGroups.MAX_W && r.h() <= CrtMonitorGroups.MAX_H, "too big: " + r);
		assertEquals(new CrtMonitorGroups.Rect(0, 0, 8, 6), CrtMonitorGroups.groupOf(0, 0, huge));
		assertEquals(new CrtMonitorGroups.Rect(0, 0, 1, 1), CrtMonitorGroups.groupOf(0, 0, new HashSet<>()), "alone");
	}

	@Test
	void eachBlockShowsThePictureBehindItsOwnGlass() {
		// One display: the glass's share of the block, with the bezel's margins.
		CrtMonitorGroups.Slice one = CrtMonitorGroups.slice(0, 0, 1, 1);
		assertEquals(1.25F / 16.0F, one.u0(), 1e-6);
		assertEquals(14.75F / 16.0F, one.u1(), 1e-6);
		assertEquals(1.5F / 16.0F, one.v0(), 1e-6, "the top bezel is 1.5");
		assertEquals(14.25F / 16.0F, one.v1(), 1e-6, "the chin is 1.75");
		// Two across: the left block ends where its right bezel starts, the right one starts after its left bezel.
		CrtMonitorGroups.Slice left = CrtMonitorGroups.slice(0, 0, 2, 1), right = CrtMonitorGroups.slice(1, 0, 2, 1);
		assertEquals(14.75F / 32.0F, left.u1(), 1e-6);
		assertEquals(17.25F / 32.0F, right.u0(), 1e-6);
		assertEquals(2.5F / 32.0F, right.u0() - left.u1(), 1e-6, "the gap is the two bezels: hidden, not squashed");
		assertEquals(left.u1() - left.u0(), right.u1() - right.u0(), 1e-6, "every tube shows the same width");
		// Two up: the top block's glass is above the bottom one's, v from the top.
		CrtMonitorGroups.Slice bottom = CrtMonitorGroups.slice(0, 0, 1, 2), top = CrtMonitorGroups.slice(0, 1, 1, 2);
		assertTrue(top.v1() < bottom.v0(), "the top tube is higher in the picture");
		assertEquals((1.5F + 1.75F) / 32.0F, bottom.v0() - top.v1(), 1e-6, "the gap is the chin above and the top bezel below");
	}
}
