package com.arrl.radiocraft.common.radio.mesh;

/** The two systems on the radio mesh (2026-11-10): radio fire alarm boxes, and SCADA telemetry. They never hear each other. */
public enum MeshSystem {
	FIRE,
	SCADA
}
