package com.arrl.radiocraft.common.blockentities.radio;

/**
 * A radio that runs from a small cell in it when nothing comes in over its power wire (the VHF receiver, 2026-11-09;
 * the FM broadcast receiver, 2026-11-13): its network object ({@code VHFReceiverNetworkObject}) pays from the wire
 * first, then counts it powered while the cell has charge; the block entity drains the cell at its own rate.
 */
public interface CellRadio {

	/** FE left in the cell (0 with none). */
	int getCellEnergy();

	/** True with a cell in it, flat or not. */
	boolean hasCell();
}
