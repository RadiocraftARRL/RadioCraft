package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.common.menus.QRPRadio20mMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The 20m QRP radio: the blue panel ({@link QrpPanelScreen}, 2026-11-13). */
public class QRPRadio20mScreen extends QrpPanelScreen<QRPRadio20mMenu> {

	public QRPRadio20mScreen(QRPRadio20mMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, "qrp_radio_20m", BLUE);
	}
}
