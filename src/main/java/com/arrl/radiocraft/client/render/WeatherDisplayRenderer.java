package com.arrl.radiocraft.client.render;

import com.arrl.radiocraft.RadiocraftClientConfig;
import com.arrl.radiocraft.common.blockentities.WeatherDisplayBlockEntity;
import com.arrl.radiocraft.common.blocks.WeatherDisplayBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * The Local Weather Station Display's red LCD (2026-11-18): a small label (TEMP °F, HUMIDITY %, ...) over four
 * seven-segment digits, stepping to the next reading every {@link #PAGE_MS}; the unlit segments show faintly, as on a
 * real one. With no station in range it reads NO STATION and dashes. Drawn on the model's {@code DISPLAY} element (see
 * {@link RadioDisplays}), within the radio screens' distance and client setting.
 */
public class WeatherDisplayRenderer implements BlockEntityRenderer<WeatherDisplayBlockEntity> {

	public static final long PAGE_MS = 2500L;
	private static final int LIT = 0xFFFF2A1A, UNLIT = 0xFF3C0C0A;
	/** Segments a to g (bit 0 to 6) of the digits 0 to 9; '-' is g alone. */
	private static final int[] DIGITS = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F};
	private static final int DASH = 0x40;

	private final Font font;

	public WeatherDisplayRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.getFont();
	}

	/** One page: its label and its four cells (a segment mask each) and which cell has the decimal point after it (-1 none). */
	record Page(String label, int[] cells, int point) {}

	/** The page of {@code readings} (null: no station) to show at {@code millis}. */
	static Page page(int[] readings, long millis) {
		if(readings == null)
			return new Page("NO STATION", new int[] {DASH, DASH, DASH, DASH}, -1);
		int index = (int)((millis / PAGE_MS) % WeatherDisplayBlockEntity.COUNT);
		int value = readings[index];
		return switch(index) {
			case WeatherDisplayBlockEntity.TEMP -> new Page("TEMP °F", number(value, 0), -1);
			case WeatherDisplayBlockEntity.HUMIDITY -> new Page("HUMIDITY %", number(value, 0), -1);
			case WeatherDisplayBlockEntity.PRESSURE -> new Page("BARO inHg", number(value, 2), 1);
			case WeatherDisplayBlockEntity.WIND_FROM -> new Page("WIND FROM " + WeatherDisplayBlock.compass(value), number(value, 0), -1);
			case WeatherDisplayBlockEntity.WIND -> new Page("WIND mph", number(value, 0), -1);
			case WeatherDisplayBlockEntity.GUST -> new Page("GUST mph", number(value, 0), -1);
			default -> new Page("RAIN in/hr", number(value, 2), 1);
		};
	}

	/** {@code value} right-aligned in four cells, with at least {@code decimals} + 1 digits shown (so 4 hundredths is 0.04). */
	static int[] number(int value, int decimals) {
		int[] cells = new int[4];
		String digits = String.valueOf(Math.min(9999, Math.abs(value)));
		while(digits.length() < decimals + 1)
			digits = "0" + digits;
		if(value < 0 && digits.length() < 4)
			digits = "-" + digits;
		if(value < -999)
			digits = "-999";
		int start = 4 - digits.length();
		for(int i = 0; i < digits.length(); i++) {
			char c = digits.charAt(i);
			cells[start + i] = c == '-' ? DASH : DIGITS[c - '0'];
		}
		return cells;
	}

	@Override
	public void render(@NotNull WeatherDisplayBlockEntity display, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
		if(!RadiocraftClientConfig.SHOW_RADIO_SCREENS.get() || display.getLevel() == null)
			return;
		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		if(camera.distanceToSqr(display.getBlockPos().getCenter()) > RadioDisplayRenderer.VIEW_DISTANCE * RadioDisplayRenderer.VIEW_DISTANCE)
			return;
		BlockState state = display.getBlockState();
		RadioDisplays.Display screen = RadioDisplays.get("weather_display");
		if(screen == null || !state.hasProperty(WeatherDisplayBlock.FACING))
			return;
		Page page = page(display.getReadings(), System.currentTimeMillis());
		float w = screen.x2() - screen.x1(), h = screen.y2() - screen.y1(); // Model pixels.

		poseStack.pushPose();
		poseStack.translate(0.5D, 0.5D, 0.5D); // Turn as the blockstate turns the model (it faces north as drawn).
		poseStack.mulPose(Axis.YP.rotationDegrees(-((state.getValue(WeatherDisplayBlock.FACING).toYRot() + 180.0F) % 360.0F)));
		poseStack.translate(-0.5D, -0.5D, -0.5D);
		poseStack.translate(screen.x2() / 16.0D, screen.y2() / 16.0D, screen.front() / 16.0D - 0.002D);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F)); // +x to the viewer's right, +y up, facing out of the display.
		poseStack.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F); // Model pixels from the display's top left.

		// The label along the top.
		float labelH = h * 0.22F;
		float scale = Math.min(w * 0.9F / Math.max(1, font.width(page.label())), labelH / font.lineHeight);
		poseStack.pushPose();
		poseStack.translate((w - font.width(page.label()) * scale) / 2.0F, -h * 0.06F, 0.0F);
		poseStack.scale(scale, -scale, scale);
		font.drawInBatch(page.label(), 0.0F, 0.0F, LIT, false, poseStack.last().pose(), buffer, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
		poseStack.popPose();

		// The four digits below it.
		VertexConsumer quads = buffer.getBuffer(RenderType.textBackground());
		Matrix4f pose = poseStack.last().pose();
		float digitsTop = -h * 0.34F, ch = h * 0.58F, gap = w * 0.04F, cw = (w * 0.92F - gap * 3) / 4.0F, t = ch * 0.13F;
		float x = w * 0.04F;
		for(int i = 0; i < 4; i++) {
			segments(quads, pose, x, digitsTop, cw, ch, t, page.cells()[i]);
			if(page.point() == i) // The decimal point, low between this digit and the next.
				rect(quads, pose, x + cw + gap * 0.2F, digitsTop - ch, gap * 0.6F, t, LIT);
			x += cw + gap;
		}
		poseStack.popPose();
	}

	/** A seven-segment digit with its top left at {@code x}, {@code top} (y up), {@code mask} lit and the rest dim. */
	private static void segments(VertexConsumer quads, Matrix4f pose, float x, float top, float cw, float ch, float t, int mask) {
		float mid = top - ch / 2.0F, bottom = top - ch;
		float[][] seg = { // x, y top, width, height, for a to g.
				{x + t, top, cw - 2 * t, t},
				{x + cw - t, top - t, t, ch / 2.0F - t},
				{x + cw - t, mid, t, ch / 2.0F - t},
				{x + t, bottom + t, cw - 2 * t, t},
				{x, mid, t, ch / 2.0F - t},
				{x, top - t, t, ch / 2.0F - t},
				{x + t, mid + t / 2.0F, cw - 2 * t, t}};
		for(int s = 0; s < 7; s++)
			rect(quads, pose, seg[s][0], seg[s][1], seg[s][2], seg[s][3], (mask >> s & 1) != 0 ? LIT : UNLIT);
	}

	/** A rectangle {@code w} wide and {@code h} down from its top left at {@code x}, {@code top}, seen from either side. */
	private static void rect(VertexConsumer quads, Matrix4f pose, float x, float top, float w, float h, int colour) {
		float z = -0.01F; // A hair off the window, in front of it.
		quads.addVertex(pose, x, top, z).setColor(colour).setLight(LightTexture.FULL_BRIGHT);
		quads.addVertex(pose, x, top - h, z).setColor(colour).setLight(LightTexture.FULL_BRIGHT);
		quads.addVertex(pose, x + w, top - h, z).setColor(colour).setLight(LightTexture.FULL_BRIGHT);
		quads.addVertex(pose, x + w, top, z).setColor(colour).setLight(LightTexture.FULL_BRIGHT);
		quads.addVertex(pose, x + w, top, z).setColor(colour).setLight(LightTexture.FULL_BRIGHT);
		quads.addVertex(pose, x + w, top - h, z).setColor(colour).setLight(LightTexture.FULL_BRIGHT);
		quads.addVertex(pose, x, top - h, z).setColor(colour).setLight(LightTexture.FULL_BRIGHT);
		quads.addVertex(pose, x, top, z).setColor(colour).setLight(LightTexture.FULL_BRIGHT);
	}

	@Override
	public int getViewDistance() {
		return (int)Math.ceil(RadioDisplayRenderer.VIEW_DISTANCE) + 1;
	}
}
