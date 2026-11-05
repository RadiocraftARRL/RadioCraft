package com.arrl.radiocraft.client.screens.adsb;

import com.arrl.radiocraft.common.menus.AdsbBeaconMenu;
import com.arrl.radiocraft.common.network.serverbound.SStationPacket;
import com.arrl.radiocraft.common.radio.adsb.AdsbMath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * The ADS-B beacon's panel (1.12 step 9), styled as a panel-mount transponder: the power switch, the flight
 * identification and the squawk (Enter sets each), and what it sends now (address, place, altitude, track, ground speed,
 * climb). Its readout arrives once a second ({@link #update}). A plain panel until it has art.
 */
public class AdsbBeaconScreen extends AbstractContainerScreen<AdsbBeaconMenu> {

	private static final int PANEL = 0xFF34383D;
	private static final int PANEL_EDGE = 0xFF16181A;
	private static final int WINDOW = 0xFF201608;
	private static final int AMBER = 0xFFF0A830;
	private static final int AMBER_DIM = 0xFF8A6420;
	private static final int LABEL = 0xFFD8DCE0;
	private static final int DIM = 0xFF8C9298;
	private static final int RED = 0xFFFF5040;

	private CompoundTag status = new CompoundTag();
	private EditBox callsignBox, squawkBox;
	private Button powerButton;
	private boolean filled = false;

	public AdsbBeaconScreen(AdsbBeaconMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = 220;
		this.imageHeight = 160;
	}

	/** The beacon's status, from the server. */
	public void update(CompoundTag data) {
		status = data;
		if(!filled && callsignBox != null) { // The boxes start with what is set, once.
			filled = true;
			callsignBox.setValue(data.getString("callsign"));
			squawkBox.setValue(AdsbMath.squawk(data.getInt("squawk")));
		}
		if(powerButton != null)
			powerButton.setMessage(powerLabel());
	}

	@Override
	protected void init() {
		super.init();
		callsignBox = new EditBox(font, leftPos + 76, topPos + 46, 70, 14, Component.translatable("screen.radiocraft.adsb_beacon.callsign"));
		callsignBox.setMaxLength(AdsbMath.CALLSIGN_MAX);
		callsignBox.setFilter(text -> AdsbMath.cleanCallsign(text).equals(text.toUpperCase(java.util.Locale.ROOT)));
		callsignBox.setTooltip(Tooltip.create(Component.translatable("screen.radiocraft.adsb_beacon.callsign_hint")));
		addRenderableWidget(callsignBox);
		squawkBox = new EditBox(font, leftPos + 76, topPos + 64, 40, 14, Component.translatable("screen.radiocraft.adsb_beacon.squawk"));
		squawkBox.setMaxLength(4);
		squawkBox.setFilter(text -> text.chars().allMatch(c -> c >= '0' && c <= '7'));
		squawkBox.setTooltip(Tooltip.create(Component.translatable("screen.radiocraft.adsb_beacon.squawk_hint")));
		addRenderableWidget(squawkBox);
		powerButton = Button.builder(powerLabel(), b -> SStationPacket.send(menu.getBlockPos(), "power"))
				.bounds(leftPos + imageWidth - 64, topPos + 22, 54, 16).build();
		addRenderableWidget(powerButton);
		if(!status.isEmpty())
			update(status);
	}

	private Component powerLabel() {
		return Component.translatable(status.getBoolean("on") ? "screen.radiocraft.adsb_beacon.power_off" : "screen.radiocraft.adsb_beacon.power_on");
	}

	private void sendCallsign() {
		CompoundTag tag = new CompoundTag();
		tag.putString("callsign", callsignBox.getValue());
		SStationPacket.send(menu.getBlockPos(), "callsign", tag);
		callsignBox.setFocused(false);
	}

	private void sendSquawk() {
		int code = AdsbMath.parseSquawk(squawkBox.getValue());
		if(code < 0)
			return;
		CompoundTag tag = new CompoundTag();
		tag.putInt("squawk", code);
		SStationPacket.send(menu.getBlockPos(), "squawk", tag);
		squawkBox.setFocused(false);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
		for(EditBox box : new EditBox[] { callsignBox, squawkBox }) {
			if(!box.isFocused())
				continue;
			if(enter) {
				if(box == callsignBox)
					sendCallsign();
				else
					sendSquawk();
				return true;
			}
			if(keyCode != GLFW.GLFW_KEY_ESCAPE) // Typing 'e' must not close the screen.
				return box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput();
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_EDGE);
		g.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, PANEL);
		g.fill(leftPos + 8, topPos + 84, leftPos + imageWidth - 8, topPos + imageHeight - 8, WINDOW); // The readout window.
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, title, 8, 7, LABEL, false);
		boolean on = status.getBoolean("on"), powered = status.getBoolean("powered");
		String state = !on ? "off" : powered ? "transmitting" : "no_power";
		g.drawString(font, Component.translatable("screen.radiocraft.adsb_beacon.state." + state), 8, 26, powered ? AMBER : on ? RED : DIM, false);
		g.drawString(font, Component.translatable("screen.radiocraft.adsb_beacon.icao", AdsbMath.hex(status.getInt("icao"))), 8, 36, DIM, false);
		g.drawString(font, Component.translatable("screen.radiocraft.adsb_beacon.callsign"), 8, 49, LABEL, false);
		g.drawString(font, Component.translatable("screen.radiocraft.adsb_beacon.squawk"), 8, 67, LABEL, false);
		String emergency = AdsbMath.emergencyTag(status.getInt("squawk"));
		if(!emergency.isEmpty())
			g.drawString(font, emergency, 122, 67, RED, false);
		int y = 89;
		String shown = status.getString("shown");
		g.drawString(font, Component.translatable("screen.radiocraft.adsb_beacon.sending", shown.isEmpty() ? AdsbMath.hex(status.getInt("icao")) : shown,
				AdsbMath.squawk(status.getInt("squawk"))), 12, y, AMBER, false);
		y += 11;
		if(status.getBoolean("onShip")) {
			String ship = status.getString("ship");
			g.drawString(font, Component.translatable("screen.radiocraft.adsb_beacon.on_ship", ship.isEmpty() ? "-" : ship), 12, y, AMBER_DIM, false);
		}
		else
			g.drawString(font, Component.translatable("screen.radiocraft.adsb_beacon.on_ground"), 12, y, AMBER_DIM, false);
		y += 11;
		if(status.getBoolean("air")) {
			g.drawString(font, Component.translatable("screen.radiocraft.adsb_beacon.position", status.getInt("x"), status.getInt("y"), status.getInt("z")), 12, y, AMBER, false);
			y += 11;
			g.drawString(font, Component.translatable("screen.radiocraft.adsb_beacon.altitude", status.getInt("alt"), status.getInt("vs")), 12, y, AMBER, false);
			y += 11;
			g.drawString(font, Component.translatable("screen.radiocraft.adsb_beacon.speed", status.getInt("gs"),
					String.format(java.util.Locale.ROOT, "%03d", status.getInt("trk"))), 12, y, AMBER, false);
		}
		else
			g.drawString(font, Component.translatable("screen.radiocraft.adsb_beacon.silent"), 12, y, AMBER_DIM, false);
	}
}
