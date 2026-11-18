package com.arrl.radiocraft.client.screens;

import com.arrl.radiocraft.common.blockentities.HeadlessTncBlockEntity;
import com.arrl.radiocraft.common.menus.HeadlessTncMenu;
import com.arrl.radiocraft.common.network.serverbound.SStationPacket;
import com.arrl.radiocraft.common.radio.data.TncPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * The Headless APRS TNC's panels (2026-11-18), drawn in code after a PacComm TINY-2 like its model: the front, a blue
 * face with a white swoosh, the PACKET TNC logo, RC-TNC, the DCD / PTT / STA / CON / PWR lamps (live) and the ON/OFF
 * rocker; and, turned round with the tab under it, the back: the callsign label, the SSID wheel, the DIGI and BEACON
 * slide switches and the RADIO / TERMINAL / 12V DC sockets.
 */
public class HeadlessTncScreen extends AbstractContainerScreen<HeadlessTncMenu> {

	public static final int W = 260, H = 84;
	private static final int CASE = 0xFF9A9C9E, RIB_DARK = 0xFF7E8082, RIB_LIGHT = 0xFFB2B4B6, CASE_EDGE = 0xFF5E6062;
	private static final int BLUE = 0xFF2E7DC4, BLUE_EDGE = 0xFF1C5A94, WHITE = 0xFFF4F6F8, PRINT_DARK = 0xFF202224;
	/** The lamps: x on the face, label, lit and unlit colours, and which bit of {@link HeadlessTncBlockEntity#getLamps()}. */
	private static final Object[][] LAMPS = {
			{48, "DCD", 0xFF3CE04C, 0xFF1C4A22, HeadlessTncBlockEntity.DCD},
			{82, "PTT", 0xFFFF3A2E, 0xFF5A1814, HeadlessTncBlockEntity.PTT},
			{116, "STA", 0xFFFFD82E, 0xFF5A4E14, HeadlessTncBlockEntity.STA},
			{150, "CON", 0xFF3CE04C, 0xFF1C4A22, HeadlessTncBlockEntity.CON},
			{184, "PWR", 0xFFFF3A2E, 0xFF5A1814, HeadlessTncBlockEntity.PWR}};
	private static final int LAMP_Y = 56;
	/** The rocker: x, y, w, h on the panel. */
	private static final int[] ROCKER = {214, 46, 28, 14};
	/** The back's controls: SSID wheel, DIGI and BEACON switches (x, y, w, h). */
	private static final int[] SSID = {124, 24, 22, 22}, DIGI = {170, 26, 16, 22}, BEACON = {206, 26, 16, 22};
	/** The tab under the panel that turns it round. */
	private static final int[] TAB = {W / 2 - 34, H + 2, 68, 12};

	private boolean rear = false;
	private EditBox callsignBox;

	public HeadlessTncScreen(HeadlessTncMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = W;
		this.imageHeight = H + 16;
	}

	private HeadlessTncBlockEntity tnc() {
		return menu.blockEntity;
	}

	@Override
	protected void init() {
		super.init();
		callsignBox = new EditBox(font, leftPos + 18, topPos + 30, 88, 14, Component.translatable("screen.radiocraft.radio_interface.callsign"));
		callsignBox.setMaxLength(TncPacket.CALLSIGN_MAX);
		callsignBox.setValue(tnc().getCallsign());
		callsignBox.setTooltip(Tooltip.create(Component.translatable("screen.radiocraft.headless_tnc.callsign_hint")));
		callsignBox.visible = rear;
		addRenderableWidget(callsignBox);
	}

	private void send(String action, CompoundTag tag) {
		SStationPacket.send(tnc().getBlockPos(), action, tag);
	}

	private boolean in(int[] r, double x, double y) {
		return x >= leftPos + r[0] && x < leftPos + r[0] + r[2] && y >= topPos + r[1] && y < topPos + r[1] + r[3];
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		if(in(TAB, x, y)) {
			rear = !rear;
			callsignBox.visible = rear;
			callsignBox.setFocused(false);
			if(rear)
				callsignBox.setValue(tnc().getCallsign());
			return true;
		}
		if(!rear && in(ROCKER, x, y)) {
			CompoundTag tag = new CompoundTag();
			tag.putBoolean("on", !tnc().isOn());
			send("power", tag);
			return true;
		}
		if(rear) {
			if(in(SSID, x, y)) { // Top half up, bottom half down; right click down.
				boolean up = button != 1 && y < topPos + SSID[1] + SSID[3] / 2.0D;
				CompoundTag tag = new CompoundTag();
				tag.putInt("ssid", Math.floorMod(tnc().getSsid() + (up ? 1 : -1), HeadlessTncBlockEntity.SSID_MAX + 1));
				send("ssid", tag);
				return true;
			}
			if(in(DIGI, x, y)) {
				CompoundTag tag = new CompoundTag();
				tag.putBoolean("digipeat", !tnc().isDigipeatEnabled());
				send("digipeat", tag);
				return true;
			}
			if(in(BEACON, x, y)) {
				CompoundTag tag = new CompoundTag();
				tag.putBoolean("beacon", !tnc().isBeaconEnabled());
				send("beacon", tag);
				return true;
			}
		}
		return super.mouseClicked(x, y, button);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if(rear && in(SSID, x, y) && scrollY != 0.0D) {
			CompoundTag tag = new CompoundTag();
			tag.putInt("ssid", Math.floorMod(tnc().getSsid() + (scrollY > 0 ? 1 : -1), HeadlessTncBlockEntity.SSID_MAX + 1));
			send("ssid", tag);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if(callsignBox.isFocused()) {
			if(keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				CompoundTag tag = new CompoundTag();
				tag.putString("callsign", callsignBox.getValue());
				send("callsign", tag);
				callsignBox.setFocused(false);
				return true;
			}
			if(keyCode != GLFW.GLFW_KEY_ESCAPE) {
				callsignBox.keyPressed(keyCode, scanCode, modifiers);
				return true; // Typing E is not the inventory key.
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	// -------------------- DRAWING --------------------

	@Override
	protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int x0 = leftPos, y0 = topPos;
		// The case: ribbed grey top, the panel inside it.
		g.fill(x0, y0, x0 + W, y0 + H, CASE_EDGE);
		g.fill(x0 + 1, y0 + 1, x0 + W - 1, y0 + H - 1, CASE);
		for(int y = 2; y < 12; y += 2)
			g.fill(x0 + 2, y0 + y, x0 + W - 2, y0 + y + 1, y % 4 == 0 ? RIB_DARK : RIB_LIGHT);
		if(rear)
			drawRear(g, x0, y0);
		else
			drawFront(g, x0, y0);
		// The tab that turns it round.
		g.fill(x0 + TAB[0], y0 + TAB[1], x0 + TAB[0] + TAB[2], y0 + TAB[1] + TAB[3], CASE_EDGE);
		g.fill(x0 + TAB[0] + 1, y0 + TAB[1] + 1, x0 + TAB[0] + TAB[2] - 1, y0 + TAB[1] + TAB[3] - 1, CASE);
		Component turn = Component.translatable(rear ? "screen.radiocraft.headless_tnc.front" : "screen.radiocraft.headless_tnc.back");
		g.drawString(font, turn, x0 + TAB[0] + (TAB[2] - font.width(turn)) / 2, y0 + TAB[1] + 2, PRINT_DARK, false);
	}

	private void drawFront(GuiGraphics g, int x0, int y0) {
		HeadlessTncBlockEntity tnc = tnc();
		int fx0 = x0 + 6, fy0 = y0 + 14, fx1 = x0 + W - 6, fy1 = y0 + H - 4;
		g.fill(fx0 - 1, fy0 - 1, fx1 + 1, fy1 + 1, BLUE_EDGE);
		g.fill(fx0, fy0, fx1, fy1, BLUE);
		// The swoosh: a white band rising from the bottom left to the right edge.
		for(int x = fx0; x < fx1; x++) {
			double t = (x - fx0) / (double)(fx1 - fx0);
			int y = (int)Math.round(fy1 - 6 - 26 * t * t);
			g.fill(x, y, x + 1, y + 3, WHITE);
		}
		// Screws.
		screw(g, fx0 + 6, fy0 + 6);
		screw(g, fx1 - 7, fy0 + 6);
		// The logo: a round mark of white stripes, then PACKET TNC.
		int lx = fx0 + 16, ly = fy0 + 6;
		int[] widths = {6, 10, 12, 12, 10, 6};
		for(int i = 0; i < widths.length; i++)
			g.fill(lx + (12 - widths[i]) / 2, ly + i * 2, lx + (12 - widths[i]) / 2 + widths[i], ly + i * 2 + 1, WHITE);
		g.pose().pushPose();
		g.pose().translate(lx + 16, ly + 1, 0);
		g.pose().scale(1.4F, 1.4F, 1.0F);
		g.drawString(font, "PACKET TNC", 0, 0, WHITE, false);
		g.pose().popPose();
		g.pose().pushPose();
		g.pose().translate(x0 + 196, ly + 1, 0);
		g.pose().scale(1.25F, 1.25F, 1.0F);
		g.drawString(font, "RC-TNC", 0, 0, WHITE, false);
		g.pose().popPose();
		// The lamps.
		int lit = tnc.getLamps();
		for(Object[] lamp : LAMPS) {
			int cx = x0 + (int)lamp[0];
			boolean on = (lit & (int)lamp[4]) != 0;
			disc(g, cx, y0 + LAMP_Y, 4, 0xFF101418);
			disc(g, cx, y0 + LAMP_Y, 3, on ? (int)lamp[2] : (int)lamp[3]);
			if(on)
				g.fill(cx - 1, y0 + LAMP_Y - 2, cx + 1, y0 + LAMP_Y - 1, 0xFFFFFFFF); // A glint.
			String label = (String)lamp[1];
			g.drawString(font, label, cx - font.width(label) / 2, y0 + LAMP_Y + 7, WHITE, false);
		}
		// The rocker: lighter on the pressed side.
		int rx = x0 + ROCKER[0], ry = y0 + ROCKER[1];
		g.fill(rx - 1, ry - 1, rx + ROCKER[2] + 1, ry + ROCKER[3] + 1, 0xFF4A4C4E);
		g.fill(rx, ry, rx + ROCKER[2], ry + ROCKER[3], 0xFFE8E4D8);
		int half = ROCKER[2] / 2;
		g.fill(tnc.isOn() ? rx + half : rx, ry, tnc.isOn() ? rx + ROCKER[2] : rx + half, ry + ROCKER[3], 0xFFC8C2B4); // The side pushed in.
		g.drawString(font, "ON/OFF", rx + ROCKER[2] / 2 - font.width("ON/OFF") / 2, ry + ROCKER[3] + 3, WHITE, false);
	}

	private void drawRear(GuiGraphics g, int x0, int y0) {
		HeadlessTncBlockEntity tnc = tnc();
		int px0 = x0 + 6, py0 = y0 + 14, px1 = x0 + W - 6, py1 = y0 + H - 4;
		g.fill(px0 - 1, py0 - 1, px1 + 1, py1 + 1, CASE_EDGE);
		g.fill(px0, py0, px1, py1, 0xFF6E7072);
		// The callsign plate (the edit box sits on it).
		g.fill(x0 + 16, y0 + 18, x0 + 108, y0 + 46, 0xFFE8E6DC);
		g.drawString(font, "CALLSIGN", x0 + 18, y0 + 20, PRINT_DARK, false);
		// The SSID thumbwheel.
		g.drawString(font, "SSID", x0 + SSID[0] + SSID[2] / 2 - font.width("SSID") / 2, y0 + SSID[1] - 9, WHITE, false);
		g.fill(x0 + SSID[0] - 1, y0 + SSID[1] - 1, x0 + SSID[0] + SSID[2] + 1, y0 + SSID[1] + SSID[3] + 1, 0xFF202224);
		g.fill(x0 + SSID[0], y0 + SSID[1], x0 + SSID[0] + SSID[2], y0 + SSID[1] + SSID[3], 0xFF3A3C3E);
		String ssid = String.valueOf(tnc.getSsid());
		g.drawString(font, ssid, x0 + SSID[0] + SSID[2] / 2 - font.width(ssid) / 2, y0 + SSID[1] + 7, WHITE, false);
		g.drawString(font, "+", x0 + SSID[0] + SSID[2] / 2 - 2, y0 + SSID[1] - 1, 0xFF9A9C9E, false);
		g.drawString(font, "-", x0 + SSID[0] + SSID[2] / 2 - 2, y0 + SSID[1] + SSID[3] - 8, 0xFF9A9C9E, false);
		slide(g, x0, y0, DIGI, "DIGI", tnc.isDigipeatEnabled());
		slide(g, x0, y0, BEACON, "BEACON", tnc.isBeaconEnabled());
		// The sockets.
		disc(g, x0 + 42, y0 + 64, 6, 0xFF202224);
		disc(g, x0 + 42, y0 + 64, 4, 0xFF4A4C4E);
		g.drawString(font, "RADIO", x0 + 42 - font.width("RADIO") / 2, y0 + 72, WHITE, false);
		g.fill(x0 + 96, y0 + 59, x0 + 140, y0 + 69, 0xFF202224);
		g.fill(x0 + 98, y0 + 61, x0 + 138, y0 + 67, 0xFF8A8C8E);
		g.drawString(font, "TERMINAL", x0 + 118 - font.width("TERMINAL") / 2, y0 + 72, WHITE, false);
		disc(g, x0 + 200, y0 + 64, 4, 0xFF202224);
		disc(g, x0 + 200, y0 + 64, 1, 0xFF8A8C8E);
		g.drawString(font, "12V DC", x0 + 200 - font.width("12V DC") / 2, y0 + 72, WHITE, false);
	}

	/** A two-position slide switch, up for on, with its label above and ON/OFF beside the slot. */
	private void slide(GuiGraphics g, int x0, int y0, int[] r, String label, boolean on) {
		int x = x0 + r[0], y = y0 + r[1];
		g.drawString(font, label, x + r[2] / 2 - font.width(label) / 2, y - 9, WHITE, false);
		g.fill(x + r[2] / 2 - 3, y, x + r[2] / 2 + 3, y + r[3], 0xFF202224);
		int ky = on ? y + 1 : y + r[3] - 9;
		g.fill(x + r[2] / 2 - 2, ky, x + r[2] / 2 + 2, ky + 8, 0xFFE8E4D8);
		g.pose().pushPose();
		g.pose().translate(x + r[2] / 2 + 5, y + 1, 0);
		g.pose().scale(0.5F, 0.5F, 1.0F);
		g.drawString(font, "ON", 0, 0, WHITE, false);
		g.drawString(font, "OFF", 0, (r[3] - 6) * 2, WHITE, false);
		g.pose().popPose();
	}

	private static void screw(GuiGraphics g, int cx, int cy) {
		disc(g, cx, cy, 3, 0xFF8A8C8E);
		g.fill(cx - 2, cy, cx + 2, cy + 1, 0xFF4A4C4E);
	}

	/** A filled circle of radius {@code r} round {@code cx}, {@code cy}. */
	private static void disc(GuiGraphics g, int cx, int cy, int r, int colour) {
		for(int dy = -r; dy <= r; dy++) {
			int dx = (int)Math.floor(Math.sqrt(r * r - dy * dy + 0.5D));
			g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, colour);
		}
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
		// The panels carry their own words.
	}

	@Override
	public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		if(!rear && in(ROCKER, mouseX, mouseY))
			g.renderTooltip(font, Component.translatable(tnc().isOn() ? "screen.radiocraft.headless_tnc.turn_off" : "screen.radiocraft.headless_tnc.turn_on"), mouseX, mouseY);
		else if(rear && in(SSID, mouseX, mouseY))
			g.renderTooltip(font, Component.translatable("screen.radiocraft.headless_tnc.ssid_tip"), mouseX, mouseY);
		else if(rear && in(DIGI, mouseX, mouseY))
			g.renderTooltip(font, Component.translatable("screen.radiocraft.headless_tnc.digi_tip"), mouseX, mouseY);
		else if(rear && in(BEACON, mouseX, mouseY))
			g.renderTooltip(font, Component.translatable("screen.radiocraft.headless_tnc.beacon_tip"), mouseX, mouseY);
		else if(!rear) {
			for(Object[] lamp : LAMPS)
				if(Math.hypot(mouseX - (leftPos + (int)lamp[0]), mouseY - (topPos + LAMP_Y)) <= 5)
					g.renderTooltip(font, Component.translatable("screen.radiocraft.headless_tnc.lamp." + ((String)lamp[1]).toLowerCase(java.util.Locale.ROOT)), mouseX, mouseY);
		}
	}
}
