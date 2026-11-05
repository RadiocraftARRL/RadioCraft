package com.arrl.radiocraft.client.screens;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.RealWorldInterfaceBlockEntity;
import com.arrl.radiocraft.common.menus.RealWorldInterfaceMenu;
import com.arrl.radiocraft.common.network.serverbound.SBlockRadioControlPacket;
import com.arrl.radiocraft.common.network.serverbound.SRealWorldInterfacePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Real-World Interface's screen (1.12 step 11): the licence warning, the interface picked from the ones the server
 * config lists (nothing about the sound card can be set here), the in-game frequency, FM or SSB, antenna or Everywhere,
 * power, and the link's status: each device found or not, PTT, VOX/COS, the time-out timers and the audio levels.
 * Plain fills, a placeholder for the art. The status comes from the server four times a second
 * ({@link com.arrl.radiocraft.common.network.clientbound.CRealWorldInterfacePacket}).
 */
public class RealWorldInterfaceScreen extends AbstractContainerScreen<RealWorldInterfaceMenu> {

	private static final int CASE = 0xFF2B2F36, PANEL = 0xFF1A1D22, WARN_BG = 0xFF4A0E0E, WARN_EDGE = 0xFFE03A2E, WARN_TEXT = 0xFFFFE9A8,
			LABEL = 0xFFD8D8D8, DIM = 0xFF8A8F98, GOOD = 0xFF4CD964, BAD = 0xFFFF5A4F, AMBER = 0xFFFFB020, LED_OFF = 0xFF3A3A3A;

	// The last status the server sent, kept here: the first one arrives before the screen opens.
	private static BlockPos statusPos = null;
	private static CompoundTag status = new CompoundTag();

	private Button previous, next, tune, mode, where, power, licence;
	private EditBox frequency;

	public RealWorldInterfaceScreen(RealWorldInterfaceMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = 256;
		imageHeight = 246;
	}

	private static Component tr(String key, Object... args) {
		return Component.translatable(Radiocraft.translationKey("screen", "real_world_interface." + key), args);
	}

	private RealWorldInterfaceBlockEntity block() {
		return menu.blockEntity;
	}

	/** A status from the server for the interface at {@code pos}. */
	public static void update(BlockPos pos, CompoundTag tag) {
		statusPos = pos;
		status = tag;
		if(Minecraft.getInstance().screen instanceof RealWorldInterfaceScreen screen && screen.block().getBlockPos().equals(pos))
			screen.refresh();
	}

	private CompoundTag status() {
		return block().getBlockPos().equals(statusPos) ? status : new CompoundTag();
	}

	@Override
	protected void init() {
		super.init();
		int y = topPos + 74;
		previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> cycle(-1)).bounds(leftPos + 70, y, 16, 16).build());
		next = addRenderableWidget(Button.builder(Component.literal(">"), b -> cycle(1)).bounds(leftPos + 232, y, 16, 16).build());
		y += 22;
		frequency = new EditBox(font, leftPos + 70, y, 70, 16, tr("frequency"));
		frequency.setMaxLength(12);
		frequency.setFilter(s -> s.matches("[0-9.,]*"));
		frequency.setValue(mhz(block().getFrequency()));
		addRenderableWidget(frequency);
		tune = addRenderableWidget(Button.builder(tr("tune"), b -> sendFrequency()).bounds(leftPos + 144, y, 40, 16).build());
		mode = addRenderableWidget(Button.builder(Component.literal("FM"), b -> SRealWorldInterfacePacket.send(block().getBlockPos(),
				SRealWorldInterfacePacket.Action.MODE, block().isFM() ? "SSB" : "FM")).bounds(leftPos + 188, y, 60, 16).build());
		y += 20;
		where = addRenderableWidget(Button.builder(tr("antenna"), b -> SRealWorldInterfacePacket.send(block().getBlockPos(),
				SRealWorldInterfacePacket.Action.EVERYWHERE, block().isEverywhere() ? "0" : "1")).bounds(leftPos + 70, y, 90, 16).build());
		power = addRenderableWidget(Button.builder(tr("power"), b -> SBlockRadioControlPacket.updateServer(block().getBlockPos(),
				SBlockRadioControlPacket.Action.TOGGLE_POWER, true)).bounds(leftPos + 188, y, 60, 16).build());
		y += 20;
		// Each player's own "I hold a licence", once: until then their voice does not go out through any interface.
		licence = addRenderableWidget(Button.builder(tr("licence"), b -> SRealWorldInterfacePacket.send(block().getBlockPos(),
				SRealWorldInterfacePacket.Action.LICENCE, "")).bounds(leftPos + 8, y, imageWidth - 16, 16).build());
		licence.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("licence.tooltip")));
		refresh();
	}

	private static String mhz(float hz) {
		String text = String.format(Locale.ROOT, "%.4f", hz / 1_000_000.0D);
		return text.replaceAll("0+$", "").replaceAll("\\.$", ".0");
	}

	/** The names to pick from: none, then each configured interface. */
	private List<String> names() {
		List<String> out = new ArrayList<>();
		out.add("");
		ListTag list = status().getList("names", Tag.TAG_STRING);
		for(int i = 0; i < list.size(); i++)
			out.add(list.getString(i));
		return out;
	}

	private void cycle(int step) {
		List<String> names = names();
		int at = 0;
		for(int i = 0; i < names.size(); i++)
			if(names.get(i).equalsIgnoreCase(block().getSelected()))
				at = i;
		String pick = names.get(Math.floorMod(at + step, names.size()));
		SRealWorldInterfacePacket.send(block().getBlockPos(), SRealWorldInterfacePacket.Action.SELECT, pick);
	}

	private void sendFrequency() {
		SRealWorldInterfacePacket.send(block().getBlockPos(), SRealWorldInterfacePacket.Action.FREQUENCY, frequency.getValue());
		frequency.setFocused(false);
	}

	/** Labels and what may be pressed, from the block and the last status. */
	private void refresh() {
		if(mode == null)
			return;
		CompoundTag tag = status();
		boolean edit = !tag.contains("mayEdit") || tag.getBoolean("mayEdit");
		boolean enabled = tag.getBoolean("enabled");
		previous.active = next.active = edit && enabled;
		tune.active = mode.active = where.active = frequency.active = power.active = edit;
		boolean licensed = tag.getBoolean("licensed");
		licence.active = !licensed;
		licence.setMessage(tr(licensed ? "licence.done" : "licence"));
		frequency.setEditable(edit);
		mode.setMessage(Component.literal(block().isFM() ? "FM" : block().isLowerSideband() ? "LSB" : "USB"));
		where.setMessage(tr(block().isEverywhere() ? "everywhere" : "antenna"));
		if(!frequency.isFocused())
			frequency.setValue(mhz(block().getFrequency()));
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if(frequency.isFocused()) {
			if(keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				sendFrequency();
				return true;
			}
			if(keyCode != GLFW.GLFW_KEY_ESCAPE) {
				frequency.keyPressed(keyCode, scanCode, modifiers);
				return true; // Typing, not the inventory key.
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void containerTick() {
		super.containerTick();
		refresh(); // The block syncs its frequency, mode and lamps on its own.
	}

	@Override
	public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.fill(leftPos - 2, topPos - 2, leftPos + imageWidth + 2, topPos + imageHeight + 2, 0xFF0A0A0A);
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, CASE);
		// The licence warning, always on screen.
		graphics.fill(leftPos + 6, topPos + 17, leftPos + imageWidth - 6, topPos + 70, WARN_EDGE);
		graphics.fill(leftPos + 7, topPos + 18, leftPos + imageWidth - 7, topPos + 69, WARN_BG);
		graphics.fill(leftPos + 6, topPos + 158, leftPos + imageWidth - 6, topPos + imageHeight - 6, PANEL);
		CompoundTag tag = status();
		boolean on = block().wasPowered();
		lamp(graphics, leftPos + 12, topPos + 221, block().isPttLamp(), BAD);
		lamp(graphics, leftPos + 72, topPos + 221, block().isTxActive(), GOOD);
		lamp(graphics, leftPos + 132, topPos + 221, tag.getBoolean("txTimedOut") || tag.getBoolean("rxTimedOut"), AMBER);
		lamp(graphics, leftPos + 192, topPos + 221, on, GOOD);
		meter(graphics, leftPos + 40, topPos + 233, tag.contains("inDb") ? tag.getFloat("inDb") : -100.0F);
		meter(graphics, leftPos + 160, topPos + 233, tag.contains("outDb") ? tag.getFloat("outDb") : -100.0F);
	}

	private static void lamp(GuiGraphics graphics, int x, int y, boolean lit, int colour) {
		graphics.fill(x, y, x + 8, y + 8, lit ? colour : LED_OFF);
	}

	/** A level bar from -60 to 0 dBFS. */
	private static void meter(GuiGraphics graphics, int x, int y, float db) {
		int width = 56;
		graphics.fill(x, y, x + width, y + 6, LED_OFF);
		int lit = Math.round(width * Math.max(0.0F, Math.min(1.0F, (db + 60.0F) / 60.0F)));
		graphics.fill(x, y, x + lit, y + 6, db > -6.0F ? BAD : db > -20.0F ? AMBER : GOOD);
	}

	@Override
	protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, 8, 6, LABEL, false);
		graphics.drawWordWrap(font, tr("warning"), 11, 21, imageWidth - 22, WARN_TEXT);
		CompoundTag tag = status();
		graphics.drawString(font, tr("interface"), 8, 78, LABEL, false);
		String picked = block().getSelected();
		Component name = picked.isEmpty() ? tr("none") : Component.literal(picked);
		graphics.drawCenteredString(font, name, 159, 78, picked.isEmpty() ? DIM : 0xFFFFFFFF);
		graphics.drawString(font, tr("frequency"), 8, 100, LABEL, false);
		graphics.drawString(font, tr("air"), 8, 120, LABEL, false);
		String state = tag.getString("state");
		if(state.isEmpty())
			state = "waiting";
		int colour = switch(state) {
			case "running" -> GOOD;
			case "starting", "off", "none" -> AMBER;
			default -> BAD;
		};
		graphics.drawString(font, tr("state." + state, tag.getString("inUseAt")), 10, 162, colour, false);
		int y = 172;
		if(tag.contains("input")) {
			device(graphics, y, tr("input", tag.getString("input"), tag.getInt("rate")), tag.getString("inputState"));
			device(graphics, y + 10, tr("output", tag.getString("output")), tag.getString("outputState"));
			device(graphics, y + 20, tr("ptt_line", tag.getString("ptt")), tag.getString("serialState"));
			graphics.drawString(font, tr("trigger", tag.getString("trigger"), tag.getInt("timeout")), 10, y + 30, DIM, false);
		}
		String error = tag.getString("error");
		if(!error.isEmpty())
			graphics.drawString(font, font.plainSubstrByWidth(error, imageWidth - 20), 10, 211, BAD, false);
		graphics.drawString(font, tr("lamp.ptt"), 22, 221, LABEL, false);
		graphics.drawString(font, tr("lamp.tx"), 82, 221, LABEL, false);
		graphics.drawString(font, tr("lamp.tot"), 142, 221, LABEL, false);
		graphics.drawString(font, tr("lamp.power"), 202, 221, LABEL, false);
		graphics.drawString(font, tr("level.in"), 10, 232, DIM, false);
		graphics.drawString(font, tr("level.out"), 130, 232, DIM, false);
	}

	/** One device line: what it is, then FOUND / NOT FOUND / ... in colour. */
	private void device(GuiGraphics graphics, int y, Component what, String state) {
		graphics.drawString(font, font.plainSubstrByWidth(what.getString(), 170), 10, y, LABEL, false);
		if(state.isEmpty())
			return;
		int colour = switch(state) {
			case "open" -> GOOD;
			case "unused", "opening" -> DIM;
			default -> BAD;
		};
		Component text = tr("device." + state);
		graphics.drawString(font, text, imageWidth - 10 - font.width(text), y, colour, false);
	}
}
