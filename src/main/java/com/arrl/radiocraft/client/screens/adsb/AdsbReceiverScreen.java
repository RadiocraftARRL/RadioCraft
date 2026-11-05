package com.arrl.radiocraft.client.screens.adsb;

import com.arrl.radiocraft.common.menus.AdsbReceiverMenu;
import com.arrl.radiocraft.common.network.serverbound.SStationPacket;
import com.arrl.radiocraft.common.radio.adsb.AdsbMath;
import com.arrl.radiocraft.common.radio.adsb.AdsbPicture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * The ADS-B receiver's radar screen (1.12 step 9): a round green CRT, north up, with range rings (no sweep: ADS-B is
 * not a radar, the reports come in all the time), each aircraft
 * as a block with its track line, its trail of the last half minute and a data tag (flight identification over
 * altitude in hundreds of feet and ground speed in knots; an emergency squawk in red). Beside it, the receiver's state,
 * the range buttons and the target list; click a target (on the scope or in the list) for its details. The picture
 * arrives once a second ({@link #update}).
 */
public class AdsbReceiverScreen extends AbstractContainerScreen<AdsbReceiverMenu> {

	private static final int PANEL = 0xFF2C3230;
	private static final int PANEL_EDGE = 0xFF121514;
	private static final int BEZEL = 0xFF0B0D0C;
	private static final int FACE = 0xFF031408;
	private static final int RING = 0xFF0E4A18;
	private static final int RING_LABEL = 0xFF1E7A2C;
	private static final int TARGET = 0xFF8CFF96;
	private static final int TARGET_DIM = 0xFF2E9A3C;
	private static final int TRAIL = 0xFF1C6A28;
	private static final int EMERGENCY = 0xFFFF5040;
	private static final int TEXT = 0xFF7CF08A;
	private static final int TEXT_DIM = 0xFF3A9A48;
	private static final int LABEL = 0xFFD0D8D2;

	/** The scope's middle and radius, from the panel's corner. */
	private static final int CX = 106, CY = 106, R = 96;
	private static final int LIST_X = 216, LIST_Y = 92, LIST_ROWS = 7, ROW_H = 10;

	private CompoundTag picture = new CompoundTag();
	private List<AdsbPicture.Contact> contacts = List.of();
	private int selected = -1; // ICAO address of the target picked, or -1.
	private Button powerButton;

	public AdsbReceiverScreen(AdsbReceiverMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = 312;
		this.imageHeight = 212;
	}

	/** The receiver's picture, from the server. */
	public void update(CompoundTag data) {
		picture = data;
		contacts = AdsbPicture.contacts(data);
		if(powerButton != null)
			powerButton.setMessage(powerLabel());
	}

	@Override
	protected void init() {
		super.init();
		powerButton = Button.builder(powerLabel(), b -> SStationPacket.send(menu.getBlockPos(), "power"))
				.bounds(leftPos + LIST_X, topPos + 46, 44, 16).build();
		addRenderableWidget(powerButton);
		addRenderableWidget(Button.builder(Component.literal("-"), b -> range(-1)).bounds(leftPos + LIST_X + 48, topPos + 46, 20, 16).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> range(1)).bounds(leftPos + LIST_X + 72, topPos + 46, 20, 16).build());
	}

	private Component powerLabel() {
		return Component.translatable(picture.getBoolean("on") ? "screen.radiocraft.adsb_receiver.power_off" : "screen.radiocraft.adsb_receiver.power_on");
	}

	private void range(int step) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("step", step);
		SStationPacket.send(menu.getBlockPos(), "range", tag);
	}

	private int rangeBlocks() {
		return Math.max(1, picture.getInt("range"));
	}

	// -------------------- DRAWING --------------------

	@Override
	protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_EDGE);
		g.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, PANEL);
		int cx = leftPos + CX, cy = topPos + CY;
		disc(g, cx, cy, R + 4, BEZEL);
		disc(g, cx, cy, R, FACE);
		for(int ring = 1; ring <= 4; ring++)
			circle(g, cx, cy, R * ring / 4.0D, RING);
		for(int i = -R; i <= R; i += 4) { // North-south and east-west lines, dotted.
			g.fill(cx + i, cy, cx + i + 1, cy + 1, RING);
			g.fill(cx, cy + i, cx + 1, cy + i + 1, RING);
		}
		if(!picture.getBoolean("powered"))
			return;
		double scale = R / (double)rangeBlocks();
		for(AdsbPicture.Contact contact : contacts)
			drawTrail(g, cx, cy, scale, contact);
		for(AdsbPicture.Contact contact : contacts)
			drawTarget(g, cx, cy, scale, contact);
	}

	private void drawTrail(GuiGraphics g, int cx, int cy, double scale, AdsbPicture.Contact contact) {
		int[] trail = contact.trail();
		int points = trail.length / 2;
		for(int i = 0; i < points; i++) {
			double px = trail[i * 2] * scale, py = trail[i * 2 + 1] * scale;
			if(px * px + py * py > (R - 1) * (R - 1))
				continue;
			int colour = i < points / 2 ? glow(0x1C6A28, 0.6D) : TRAIL;
			g.fill(cx + (int)Math.round(px), cy + (int)Math.round(py), cx + (int)Math.round(px) + 1, cy + (int)Math.round(py) + 1, colour);
		}
	}

	private void drawTarget(GuiGraphics g, int cx, int cy, double scale, AdsbPicture.Contact contact) {
		double px = contact.dx() * scale, py = contact.dz() * scale;
		if(px * px + py * py > (R - 2) * (R - 2))
			return;
		int x = cx + (int)Math.round(px), y = cy + (int)Math.round(py);
		boolean emergency = contact.emergency();
		int colour = emergency ? EMERGENCY : TARGET;
		g.fill(x - 1, y - 1, x + 2, y + 2, colour);
		if(contact.knots() > 0) { // The track line: where it will be in about a minute, at most 14 pixels.
			double length = Math.min(14.0D, Math.max(4.0D, contact.knots() / AdsbMath.KNOTS_PER_BLOCK_PER_SECOND * 60.0D * scale));
			line(g, x, y, contact.track(), length, colour);
		}
		if(contact.icao() == selected) {
			g.fill(x - 4, y - 4, x + 5, y - 3, TEXT);
			g.fill(x - 4, y + 4, x + 5, y + 5, TEXT);
			g.fill(x - 4, y - 4, x - 3, y + 5, TEXT);
			g.fill(x + 4, y - 4, x + 5, y + 5, TEXT);
		}
		String emergencyTag = AdsbMath.emergencyTag(contact.squawk());
		small(g, emergencyTag.isEmpty() ? contact.label() : contact.label() + " " + emergencyTag, x + 5, y - 7, emergency ? EMERGENCY : colour);
		String climb = contact.feetPerMinute() > 256 ? "^" : contact.feetPerMinute() < -256 ? "v" : " ";
		small(g, AdsbMath.flightLevel(contact.feet()) + climb + contact.knots(), x + 5, y - 1, emergency ? EMERGENCY : mix(TARGET_DIM, colour, 0.7D));
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, title, LIST_X, 8, LABEL, false);
		boolean on = picture.getBoolean("on"), powered = picture.getBoolean("powered"), antenna = picture.getBoolean("antenna");
		String state = !on ? "off" : !powered ? "no_power" : !antenna ? "no_antenna" : "receiving";
		g.drawString(font, Component.translatable("screen.radiocraft.adsb_receiver.state." + state), LIST_X, 20, powered && antenna ? TEXT : EMERGENCY, false);
		g.drawString(font, Component.translatable("screen.radiocraft.adsb_receiver.range", rangeKm(rangeBlocks())), LIST_X, 32, TEXT_DIM, false);
		if(powered && antenna) {
			// Half size: "Reach 18 km high, 200 m low" does not fit the column at full size.
			small(g, Component.translatable("screen.radiocraft.adsb_receiver.reach", rangeKm(picture.getInt("reach")), rangeKm(picture.getInt("reachLow"))).getString(), LIST_X, 68, TEXT_DIM);
			g.drawString(font, Component.translatable("screen.radiocraft.adsb_receiver.count", contacts.size()), LIST_X, 78, TEXT_DIM, false);
		}
		// Ring labels: the distance of each ring, along the north line.
		if(powered)
			for(int ring = 1; ring <= 4; ring++)
				small(g, rangeKm(rangeBlocks() * ring / 4), CX + 2, CY - R * ring / 4 + 1, RING_LABEL);
		small(g, "N", CX - 1, CY - R + 2, RING_LABEL);
		if(!powered || !antenna) {
			Component message = Component.translatable("screen.radiocraft.adsb_receiver.state." + state);
			g.drawString(font, message, CX - font.width(message) / 2, CY + 20, powered ? TEXT_DIM : 0xFF1A4A22, false);
		}
		else if(contacts.isEmpty()) {
			Component message = Component.translatable("screen.radiocraft.adsb_receiver.no_targets");
			g.drawString(font, message, CX - font.width(message) / 2, CY + 20, TEXT_DIM, false);
		}
		renderList(g);
		renderDetails(g);
	}

	private void renderList(GuiGraphics g) {
		for(int i = 0; i < Math.min(LIST_ROWS, contacts.size()); i++) {
			AdsbPicture.Contact c = contacts.get(i);
			int y = LIST_Y + i * ROW_H;
			if(c.icao() == selected)
				g.fill(LIST_X - 2, y - 1, imageWidth - 6, y + ROW_H - 2, 0xFF0E3A16);
			String line = String.format(Locale.ROOT, "%-8s %s %4.1f", c.label(), AdsbMath.flightLevel(c.feet()), c.distance() / 1000.0D);
			small(g, line, LIST_X, y + 1, c.emergency() ? EMERGENCY : TEXT);
		}
		if(contacts.size() > LIST_ROWS)
			small(g, "+" + (contacts.size() - LIST_ROWS), LIST_X, LIST_Y + LIST_ROWS * ROW_H, TEXT_DIM);
	}

	private void renderDetails(GuiGraphics g) {
		AdsbPicture.Contact c = selectedContact();
		if(c == null)
			return;
		int y = 168;
		int colour = c.emergency() ? EMERGENCY : TEXT;
		small(g, c.label() + "  " + AdsbMath.hex(c.icao()) + "  SQ " + AdsbMath.squawk(c.squawk()), LIST_X, y, colour);
		small(g, String.format(Locale.ROOT, "ALT %d FT  VS %+d", c.feet(), c.feetPerMinute()), LIST_X, y + 8, colour);
		small(g, String.format(Locale.ROOT, "GS %d KT  TRK %03d", c.knots(), c.track()), LIST_X, y + 16, colour);
		small(g, String.format(Locale.ROOT, "BRG %03d  %.2f KM", Math.round(c.bearing()) % 360, c.distance() / 1000.0D), LIST_X, y + 24, colour);
		if(c.age() > 2)
			small(g, Component.translatable("screen.radiocraft.adsb_receiver.lost", c.age()).getString(), LIST_X, y + 32, TEXT_DIM);
	}

	private AdsbPicture.Contact selectedContact() {
		for(AdsbPicture.Contact c : contacts)
			if(c.icao() == selected)
				return c;
		return null;
	}

	// -------------------- INPUT --------------------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		double x = mouseX - leftPos, y = mouseY - topPos;
		if(x >= LIST_X - 2 && x < imageWidth - 4 && y >= LIST_Y - 1 && y < LIST_Y + LIST_ROWS * ROW_H) {
			int row = (int)((y - LIST_Y + 1) / ROW_H);
			if(row >= 0 && row < contacts.size()) {
				selected = contacts.get(row).icao();
				return true;
			}
		}
		double dx = x - CX, dy = y - CY;
		if(dx * dx + dy * dy <= R * R) { // The nearest target within a few pixels of the click.
			double scale = R / (double)rangeBlocks(), best = 49.0D;
			int pick = -1;
			for(AdsbPicture.Contact c : contacts) {
				double ex = c.dx() * scale - dx, ey = c.dz() * scale - dy, d = ex * ex + ey * ey;
				if(d < best) {
					best = d;
					pick = c.icao();
				}
			}
			selected = pick;
			if(pick >= 0)
				return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if(scrollY != 0.0D) {
			range(scrollY > 0.0D ? -1 : 1); // Scroll up zooms in.
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	// -------------------- HELPERS --------------------

	/** "250 m" or "2 km". */
	private static String rangeKm(int blocks) {
		if(blocks < 1000)
			return blocks + " m";
		return blocks % 1000 == 0 ? blocks / 1000 + " km" : String.format(Locale.ROOT, "%.1f km", blocks / 1000.0D);
	}

	/** Half-size text. */
	private void small(GuiGraphics g, String text, int x, int y, int colour) {
		g.pose().pushPose();
		g.pose().translate(x, y, 0.0F);
		g.pose().scale(0.5F, 0.5F, 1.0F);
		g.drawString(font, text, 0, 0, colour, false);
		g.pose().popPose();
	}

	/** A filled circle, row by row. */
	private static void disc(GuiGraphics g, int cx, int cy, int r, int colour) {
		for(int dy = -r; dy <= r; dy++) {
			int half = (int)Math.round(Math.sqrt((double)r * r - (double)dy * dy));
			g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, colour);
		}
	}

	/** A circle outline, a pixel every pixel or so. */
	private static void circle(GuiGraphics g, int cx, int cy, double r, int colour) {
		int steps = Math.max(24, (int)(r * 2.0D * Math.PI / 1.5D));
		for(int i = 0; i < steps; i++) {
			double a = i * 2.0D * Math.PI / steps;
			int x = cx + (int)Math.round(Math.sin(a) * r), y = cy - (int)Math.round(Math.cos(a) * r);
			g.fill(x, y, x + 1, y + 1, colour);
		}
	}

	/** A line {@code length} pixels long from {@code x}, {@code y} towards compass {@code degrees}. */
	private static void line(GuiGraphics g, int x, int y, double degrees, double length, int colour) {
		double a = Math.toRadians(degrees), sx = Math.sin(a), sy = -Math.cos(a);
		for(int i = 1; i <= (int)length; i++) {
			int px = x + (int)Math.round(sx * i), py = y + (int)Math.round(sy * i);
			g.fill(px, py, px + 1, py + 1, colour);
		}
	}

	/** {@code rgb} at {@code alpha} (0 to 1). */
	private static int glow(int rgb, double alpha) {
		int a = (int)Math.round(Math.max(0.0D, Math.min(1.0D, alpha)) * 255.0D);
		return a << 24 | rgb & 0xFFFFFF;
	}

	/** Between {@code from} and {@code to} by {@code t}, opaque. */
	private static int mix(int from, int to, double t) {
		t = Math.max(0.0D, Math.min(1.0D, t));
		int r = (int)Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int gr = (int)Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = (int)Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return 0xFF000000 | r << 16 | gr << 8 | b;
	}
}
