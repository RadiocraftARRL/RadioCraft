package com.arrl.radiocraft.common.radio.distress;

/**
 * The nature of distress a DSC distress alert carries (2026-11-09), as the real DSC menu lists them. A radio sends
 * {@link #UNDESIGNATED} unless one is picked; a beacon always sends it (an EPIRB cannot say what happened).
 */
public enum DistressNature {
	UNDESIGNATED("UNDESIGNATED", "UNDES"),
	FIRE("FIRE", "FIRE"),
	FLOODING("FLOODING", "FLOOD"),
	COLLISION("COLLISION", "COLLIS"),
	GROUNDING("GROUNDING", "GROUND"),
	CAPSIZING("CAPSIZING", "CAPSIZE"),
	SINKING("SINKING", "SINKING"),
	ADRIFT("ADRIFT", "ADRIFT"),
	ABANDONING("ABANDONING SHIP", "ABANDON"),
	MAN_OVERBOARD("MAN OVERBOARD", "MOB");

	private final String label;
	private final String shortLabel;

	DistressNature(String label, String shortLabel) {
		this.label = label;
		this.shortLabel = shortLabel;
	}

	/** In full, for the computer and the handbook ("MAN OVERBOARD"). */
	public String label() {
		return label;
	}

	/** At most seven letters, for the radio's LED window ("MOB"). */
	public String shortLabel() {
		return shortLabel;
	}

	/** The next one on the NATURE key, round to the start. */
	public DistressNature next() {
		return values()[(ordinal() + 1) % values().length];
	}

	/** By ordinal, {@link #UNDESIGNATED} if out of range. */
	public static DistressNature byIndex(int index) {
		return index >= 0 && index < values().length ? values()[index] : UNDESIGNATED;
	}

	/** By name, {@link #UNDESIGNATED} if unknown. */
	public static DistressNature byName(String name) {
		for(DistressNature nature : values())
			if(nature.name().equals(name))
				return nature;
		return UNDESIGNATED;
	}
}
