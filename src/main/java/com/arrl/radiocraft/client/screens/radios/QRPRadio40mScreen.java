package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.common.menus.QRPRadio40mMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The 40m QRP radio: the red panel ({@link QrpPanelScreen}, 2026-11-13). */
public class QRPRadio40mScreen extends QrpPanelScreen<QRPRadio40mMenu> {

	public QRPRadio40mScreen(QRPRadio40mMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, "qrp_radio_40m", RED);
	}
}
