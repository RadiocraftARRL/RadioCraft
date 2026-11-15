package com.arrl.radiocraft.client.screens;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.client.RadiocraftKeys;
import com.arrl.radiocraft.common.blockentities.radio.HFRadioBlockEntity;
import com.arrl.radiocraft.common.blocks.IambicKeyerBlock;
import com.arrl.radiocraft.common.network.serverbound.SKeyerPacket;
import com.arrl.radiocraft.common.radio.morse.IambicKeyer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The iambic keyer's screen (2026-11-12; drawn from its model 2026-11-13): the paddle seen from above, every part of
 * the block's model ({@code models/block/iambic_keyer.json}, read when the screen opens, so it follows the model) drawn
 * with its own texture, the finger pieces in this keyer's finish. The front, where the finger pieces stick out, is at
 * the bottom; the left one is dit, the right one dah. They light while pressed: hold the dit and dah keys
 * ({@link RadiocraftKeys#KEYER_DIT}, A, and {@link RadiocraftKeys#KEYER_DAH}, D) or press either half of the front with
 * the mouse. MODE, SPEED and SIDETONE are switch plates under it; the keyer's logic ({@link IambicKeyer}) keys the radio
 * beside it every tick, with the keyer's sidetone, through the radio's own CW send buffer.
 */
public class KeyerScreen extends Screen {

	private static final ResourceLocation MODEL = Radiocraft.id("models/block/iambic_keyer.json");
	private static final ResourceLocation BODY = Radiocraft.id("textures/block/iambic_keyer.png");
	private static final int W = 240, H = 236;
	private static final int PANEL = 0xFF26262A, EDGE = 0xFF5A5A60, LABEL = 0xFFD8D8D8, DIM = 0xFF8A8A8A, PLATE = 0xFF3A3A40, PLATE_LIT = 0xFF56565E;
	/** Screen pixels per model pixel. */
	private static final float S = 20.0F;

	/** One box of the model, seen from above: x and z span, its top, and its top face's texture and uv. */
	private record Part(float x0, float z0, float x1, float z1, float top, boolean paddle, float u0, float v0, float u1, float v1, float angle) {}

	private final BlockPos pos;
	private final IambicKeyer keyer = new IambicKeyer(IambicKeyer.Mode.IAMBIC_B, 2);
	private final List<Part> parts = new ArrayList<>();
	private float minX = 16, maxX = 0, minZ = 16, maxZ = 0;
	private int left, top;
	private boolean mouseDit, mouseDah, ditLit, dahLit, keyLit;

	public KeyerScreen(BlockPos pos) {
		super(Component.translatable(Radiocraft.translationKey("screen", "keyer.title")));
		this.pos = pos;
	}

	public static void open(BlockPos pos) {
		Minecraft.getInstance().setScreen(new KeyerScreen(pos));
	}

	private BlockState state() {
		return minecraft == null || minecraft.level == null ? null : minecraft.level.getBlockState(pos);
	}

	@Override
	protected void init() {
		left = (width - W) / 2;
		top = (height - H) / 2;
		if(parts.isEmpty())
			loadModel();
	}

	/** Reads the keyer's model: each element's top face, lowest first so the higher ones draw over them. */
	private void loadModel() {
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(MODEL);
		if(resource.isEmpty())
			return;
		try(Reader reader = resource.get().openAsReader()) {
			JsonObject model = JsonParser.parseReader(reader).getAsJsonObject();
			for(JsonElement e : model.getAsJsonArray("elements")) {
				JsonObject el = e.getAsJsonObject();
				JsonArray from = el.getAsJsonArray("from"), to = el.getAsJsonArray("to");
				JsonObject up = el.getAsJsonObject("faces").getAsJsonObject("up");
				if(up == null)
					continue;
				JsonArray uv = up.getAsJsonArray("uv");
				float angle = el.has("rotation") && el.getAsJsonObject("rotation").get("axis").getAsString().equals("y")
						? el.getAsJsonObject("rotation").get("angle").getAsFloat() : 0;
				Part part = new Part(from.get(0).getAsFloat(), from.get(2).getAsFloat(), to.get(0).getAsFloat(), to.get(2).getAsFloat(),
						to.get(1).getAsFloat(), up.get("texture").getAsString().equals("#paddle"),
						uv.get(0).getAsFloat(), uv.get(1).getAsFloat(), uv.get(2).getAsFloat(), uv.get(3).getAsFloat(), angle);
				parts.add(part);
				minX = Math.min(minX, part.x0());
				maxX = Math.max(maxX, part.x1());
				minZ = Math.min(minZ, part.z0());
				maxZ = Math.max(maxZ, part.z1());
			}
			parts.sort(Comparator.comparingDouble(Part::top));
		}
		catch(Exception e) {
			Radiocraft.LOGGER.warn("Could not read the iambic keyer's model for its screen", e);
		}
	}

	// From above with the front at the bottom: east (+x) is on the left, north (-z) at the bottom.
	private float sx(float x) {
		return left + W / 2.0F - (x - (minX + maxX) / 2.0F) * S;
	}

	private float sy(float z) {
		return top + 22 + (maxZ - z) * S;
	}

	/** Where the finger pieces are on screen: the front stretch of the drawing, split down the middle. */
	private int frontTop() {
		float paddleBack = minZ;
		for(Part p : parts)
			if(p.paddle())
				paddleBack = Math.max(paddleBack, p.z1());
		return (int)sy(paddleBack);
	}

	private int frontBottom() {
		return (int)sy(minZ) + 6;
	}

	private boolean onDit(double x, double y) {
		return y >= frontTop() && y < frontBottom() && x >= left + 20 && x < left + W / 2.0;
	}

	private boolean onDah(double x, double y) {
		return y >= frontTop() && y < frontBottom() && x >= left + W / 2.0 && x < left + W - 20;
	}

	// The switch plates under the drawing: MODE, SPEED, SIDETONE.
	private int platesY() {
		return top + H - 40;
	}

	private int plate(double x, double y) {
		if(y < platesY() || y >= platesY() + 18)
			return -1;
		int i = (int)((x - left - 8) / 76);
		return i >= 0 && i < 3 && x >= left + 8 + i * 76 && x < left + 8 + i * 76 + 72 ? i : -1;
	}

	/** Steps a switch round and tells the server. */
	private void change(int mode, int speed, int tone) {
		BlockState state = state();
		if(state == null || !(state.getBlock() instanceof IambicKeyerBlock))
			return;
		int m = (state.getValue(IambicKeyerBlock.MODE).ordinal() + mode) % IambicKeyerBlock.KeyerMode.values().length;
		int s = speed == 0 ? state.getValue(IambicKeyerBlock.SPEED) : state.getValue(IambicKeyerBlock.SPEED) % 4 + 1;
		int t = tone == 0 ? state.getValue(IambicKeyerBlock.SIDETONE) : (state.getValue(IambicKeyerBlock.SIDETONE) + 1) % 4;
		PacketDistributor.sendToServer(new SKeyerPacket(pos, m, s, t));
	}

	private static boolean held(KeyMapping mapping) {
		InputConstants.Key key = mapping.getKey();
		return key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue()
				&& InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key.getValue());
	}

	@Override
	public void tick() {
		BlockState state = state();
		if(state == null || !(state.getBlock() instanceof IambicKeyerBlock) || minecraft.player == null
				|| minecraft.player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
			onClose(); // Broken, or walked away from.
			return;
		}
		keyer.set(state.getValue(IambicKeyerBlock.MODE).mode, IambicKeyerBlock.unit(state.getValue(IambicKeyerBlock.SPEED)));
		ditLit = held(RadiocraftKeys.KEYER_DIT) || mouseDit;
		dahLit = held(RadiocraftKeys.KEYER_DAH) || mouseDah;
		boolean down = keyer.tick(ditLit, dahLit);
		HFRadioBlockEntity radio = IambicKeyerBlock.radioBeside(minecraft.level, pos);
		keyLit = down;
		if(down && radio != null && radio.getCWEnabled() && radio.isPowered())
			radio.keyCW(IambicKeyerBlock.sidetone(state.getValue(IambicKeyerBlock.SIDETONE)));
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		if(button == 0 && onDit(x, y)) {
			mouseDit = true;
			return true;
		}
		if(button == 0 && onDah(x, y)) {
			mouseDah = true;
			return true;
		}
		int plate = plate(x, y);
		if(button == 0 && plate >= 0) {
			change(plate == 0 ? 1 : 0, plate == 1 ? 1 : 0, plate == 2 ? 1 : 0);
			return true;
		}
		return super.mouseClicked(x, y, button);
	}

	@Override
	public boolean mouseReleased(double x, double y, int button) {
		if(button == 0) {
			mouseDit = false;
			mouseDah = false;
		}
		return super.mouseReleased(x, y, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if(RadiocraftKeys.KEYER_DIT.matches(keyCode, scanCode) || RadiocraftKeys.KEYER_DAH.matches(keyCode, scanCode))
			return true; // Read in tick(): held, not typed.
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	// -------------------- DRAWING --------------------

	@Override
	public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		g.fill(left - 1, top - 1, left + W + 1, top + H + 1, EDGE);
		g.fill(left, top, left + W, top + H, PANEL);
	}

	@Override
	public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		BlockState state = state();
		if(state == null || !(state.getBlock() instanceof IambicKeyerBlock))
			return;
		g.drawCenteredString(font, title, left + W / 2, top + 6, LABEL);
		ResourceLocation finish = Radiocraft.id("textures/block/keyer_paddle/" + state.getValue(IambicKeyerBlock.FINISH).getSerializedName() + ".png");
		RenderSystem.enableBlend();
		float middle = (minX + maxX) / 2.0F;
		for(Part p : parts) {
			int x0 = Math.round(sx(p.x1())), x1 = Math.round(sx(p.x0())), y0 = Math.round(sy(p.z1())), y1 = Math.round(sy(p.z0()));
			if(x1 <= x0)
				x1 = x0 + 1;
			if(y1 <= y0)
				y1 = y0 + 1;
			g.pose().pushPose();
			if(p.angle() != 0) { // The round pivot: its second, turned square.
				g.pose().translate((x0 + x1) / 2.0F, (y0 + y1) / 2.0F, 0);
				g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(p.angle()));
				g.pose().translate(-(x0 + x1) / 2.0F, -(y0 + y1) / 2.0F, 0);
			}
			if(p.paddle()) {
				// Thin plates, seen from above: drawn a little wider so the finish shows; lit while pressed.
				boolean lit = (p.x0() + p.x1()) / 2.0F > middle ? ditLit : dahLit;
				int grow = 3;
				g.blit(finish, x0 - grow, y0, x1 - x0 + grow * 2, y1 - y0, 1, 1, 14, 14, 16, 16);
				if(lit)
					g.fill(x0 - grow, y0, x1 + grow, y1, 0x80FFF0B0);
			}
			else
				g.blit(BODY, x0, y0, x1 - x0, y1 - y0, p.u0(), p.v0(), Math.max(1, Math.round(p.u1() - p.u0())),
						Math.max(1, Math.round(p.v1() - p.v0())), 16, 16);
			g.pose().popPose();
		}
		RenderSystem.disableBlend();

		int legend = frontBottom() + 2;
		g.drawCenteredString(font, Component.translatable(Radiocraft.translationKey("screen", "keyer.dit"), RadiocraftKeys.KEYER_DIT.getTranslatedKeyMessage()),
				left + W / 4 + 10, legend, ditLit ? 0xFFFFE080 : DIM);
		g.drawCenteredString(font, Component.translatable(Radiocraft.translationKey("screen", "keyer.dah"), RadiocraftKeys.KEYER_DAH.getTranslatedKeyMessage()),
				left + W * 3 / 4 - 10, legend, dahLit ? 0xFFFFE080 : DIM);
		// The key lamp, and whether the radio beside it will send.
		g.fill(left + 10, top + 8, left + 18, top + 16, keyLit ? 0xFFFF4040 : 0xFF402020);
		g.drawString(font, Component.translatable(Radiocraft.translationKey("screen", "keyer.key")), left + 22, top + 8, DIM);
		HFRadioBlockEntity radio = minecraft == null || minecraft.level == null ? null : IambicKeyerBlock.radioBeside(minecraft.level, pos);
		String why = radio == null ? "keyer.no_radio" : !radio.isPowered() ? "keyer.radio_off" : !radio.getCWEnabled() ? "keyer.not_cw" : "keyer.ready";
		g.drawCenteredString(font, Component.translatable(Radiocraft.translationKey("screen", why)), left + W / 2, platesY() - 14,
				why.equals("keyer.ready") ? 0xFF7CFC7C : 0xFFFF8080);
		// The switch plates.
		Component[] labels = {
				Component.translatable(Radiocraft.translationKey("screen", "keyer.mode." + state.getValue(IambicKeyerBlock.MODE).getSerializedName())),
				Component.translatable(Radiocraft.translationKey("screen", "keyer.wpm"), IambicKeyer.wpm(IambicKeyerBlock.unit(state.getValue(IambicKeyerBlock.SPEED)))),
				Component.translatable(Radiocraft.translationKey("screen", "keyer.tone." + state.getValue(IambicKeyerBlock.SIDETONE)))};
		for(int i = 0; i < 3; i++) {
			int x = left + 8 + i * 76, y = platesY();
			boolean hover = plate(mouseX, mouseY) == i;
			g.fill(x, y, x + 72, y + 18, 0xFF101012);
			g.fill(x + 1, y + 1, x + 71, y + 17, hover ? PLATE_LIT : PLATE);
			g.fill(x + 4, y + 6, x + 10, y + 12, 0xFFC4C6CA); // The toggle's bat.
			g.drawCenteredString(font, labels[i], x + 40, y + 5, LABEL);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
