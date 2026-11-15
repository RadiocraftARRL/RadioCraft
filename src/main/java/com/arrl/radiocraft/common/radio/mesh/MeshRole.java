package com.arrl.radiocraft.common.radio.mesh;

/**
 * What a device on the radio mesh is (2026-11-10). Field devices (fire alarm boxes, RTUs) relay what they hear once;
 * the controller and the master are where messages end up, and send the acks and polls.
 */
public enum MeshRole {
	/** A radio fire alarm box: sends its pull stations' alarms, relays other boxes'. */
	BOX(MeshSystem.FIRE, true),
	/** A fire alarm controller (the DMP-style receiver): logs, prints and acks. */
	CONTROLLER(MeshSystem.FIRE, false),
	/** A SCADA remote terminal unit: answers the master's polls, relays other RTUs'. */
	RTU(MeshSystem.SCADA, true),
	/** The SCADA master: polls the RTUs and runs the rules. */
	MASTER(MeshSystem.SCADA, false);

	private final MeshSystem system;
	private final boolean relays;

	MeshRole(MeshSystem system, boolean relays) {
		this.system = system;
		this.relays = relays;
	}

	public MeshSystem system() {
		return system;
	}

	/** True if it sends on what it hears that is not for it. */
	public boolean relays() {
		return relays;
	}
}
