package com.arrl.radiocraft.common.blockentities.radio;

/**
 * A radio block with memory channels on its front panel: the MEMORY and VFO/MEM keys of
 * {@code HfPanelScreen} (2026-11-09 the all-band radio; 2026-11-14 the VHF base station, the maintainer: "the vhf base
 * stations are missing the MEMORY button and MEM/VFO toggle"). Channels are numbered 1 up on the panel (0 up here).
 * <ul>
 * <li>MEMORY held on the VFO, the dial picks a channel, MEMORY pressed stores the VFO there ({@link #storeMemory}).</li>
 * <li>VFO/MEM pressed: onto the memories or back to the VFO where it was ({@link #toggleMemoryMode}); on the memories the
 * dial and step keys go from stored channel to stored channel ({@link #selectMemory}).</li>
 * <li>VFO/MEM held on a memory: the VFO takes it ({@link #memoryToVfo}).</li>
 * </ul>
 * All of it server side, through {@code SBlockRadioControlPacket}'s MEM actions; a recalled frequency is tuned as any
 * other ({@code RadioBlockEntity#recallFrequency}), so a radio without a MARS mod cannot be taken out of band by one.
 */
public interface MemoryRadio {

	/** How many channels. */
	int memoryCount();

	/** On the memories (MEM) rather than the VFO. */
	boolean isMemoryMode();

	/** The channel in use, or last used. */
	int getMemoryChannel();

	/** Channel {@code channel}'s frequency, 0 if it is empty. */
	float getMemoryHz(int channel);

	/** What else channel {@code channel} holds, for the display while writing ("SSB"; "-600 T88.5"). */
	String memoryLabel(int channel);

	/** MEMORY: the VFO and its settings into channel {@code channel}. Nothing while on the memories. */
	void storeMemory(int channel);

	/** VFO/MEM: onto the memories (the last one used, or the next stored) or back to the VFO where it was. */
	void toggleMemoryMode();

	/** On the memories: to channel {@code channel}, if it holds a frequency. */
	void selectMemory(int channel);

	/** VFO/MEM held on a memory: the VFO takes the memory's frequency and settings, and the radio goes back to the VFO. */
	void memoryToVfo();

	/** The next channel after {@code from} in {@code direction} that holds a frequency (wrapping), -1 if none. */
	int nextStored(int from, int direction);
}
