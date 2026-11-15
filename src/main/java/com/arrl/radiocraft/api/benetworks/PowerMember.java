package com.arrl.radiocraft.api.benetworks;

import com.arrl.radiocraft.common.capabilities.BasicEnergyStorage;

/**
 * Something a power wire's network feeds or draws from (2026-11-12). Every {@link PowerNetworkObject} is one; a
 * network object that must extend another class (the satellite tracking antenna, an antenna on the coax that also
 * runs motors) implements this to sit on a power wire too.
 */
public interface PowerMember {

	/**
	 * @return its energy store
	 */
	BasicEnergyStorage getStorage();

	/**
	 * @return true if it takes energy from batteries and charge controllers
	 */
	boolean isIndirectConsumer();

	/**
	 * @return true if it takes energy straight from solar panels
	 */
	boolean isDirectConsumer();
}
