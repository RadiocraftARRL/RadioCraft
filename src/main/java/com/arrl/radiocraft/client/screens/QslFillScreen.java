package com.arrl.radiocraft.client.screens;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.client.qsl.QslTextures;
import com.arrl.radiocraft.common.network.serverbound.SQslCardPacket;
import com.arrl.radiocraft.common.qsl.QslArt;
import com.arrl.radiocraft.common.qsl.QslCard;
import com.arrl.radiocraft.common.qsl.QslCards;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Filling in a QSL card by hand (computer batch 3C), like a book and quill: the card's back as it will look, and fields
 * for the station it is for, band, frequency, mode, signal report and notes (and the sender's callsign when the
 * template left it blank). The date and time are put in by the server when the card is saved. "Done" keeps the
 * writing unsigned; "Sign" locks the card for good ({@link SQslCardPacket}).
 */
public class QslFillScreen extends Screen {

	private static final String KEY = "screen.radiocraft.qsl_fill.";

	private final InteractionHand hand;
	private final QslCard card;
	private final List<EditBox> boxes = new ArrayList<>();
	private EditBox from, to, band, frequency, mode, report, notes;
	private DynamicTexture preview;
	private ResourceLocation previewId;
	private QslCard.Fill shown;
	private int previewX, previewY, previewScale;

	public QslFillScreen(InteractionHand hand, QslCard card) {
		super(Component.translatable(KEY + "title"));
		this.hand = hand;
		this.card = card;
	}

	public static void open(InteractionHand hand) {
		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null)
			return;
		QslCard card = QslCards.card(mc.player.getItemInHand(hand));
		if(card != null && !card.signed())
			mc.setScreen(new QslFillScreen(hand, card));
	}

	@Override
	protected void init() {
		boxes.clear();
		previewScale = width >= 470 ? 2 : 1;
		int formWidth = 150;
		int total = QslArt.WIDTH * previewScale + 12 + formWidth;
		previewX = (width - total) / 2;
		previewY = Math.max(24, (height - QslArt.HEIGHT * previewScale) / 2 - 10);
		int x = previewX + QslArt.WIDTH * previewScale + 12;
		int y = Math.max(20, height / 2 - 100);
		QslCard.Fill f = card.fill();
		if(card.design().call().isEmpty())
			from = box(x, y, formWidth, f.from(), QslCard.CALL_MAX, "from");
		else
			from = null;
		y += from == null ? 0 : 24;
		to = box(x, y, formWidth, f.to(), QslCard.TO_MAX, "to");
		band = box(x, y += 24, 70, f.band(), QslCard.BAND_MAX, "band");
		frequency = box(x + 80, y, 70, f.frequency(), QslCard.FREQ_MAX, "freq");
		mode = box(x, y += 24, 70, f.mode(), QslCard.MODE_MAX, "mode");
		report = box(x + 80, y, 70, f.report(), QslCard.REPORT_MAX, "rst");
		notes = box(x, y += 24, formWidth, f.notes(), QslCard.NOTES_MAX, "notes");
		y += 30;
		addRenderableWidget(Button.builder(Component.translatable(KEY + "done"), b -> save(false)).bounds(x, y, 72, 20).build());
		addRenderableWidget(Button.builder(Component.translatable(KEY + "sign"), b -> save(true)).bounds(x + 78, y, 72, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x, y + 24, formWidth, 20).build());
		setInitialFocus(from != null ? from : to);
		if(preview == null) {
			preview = new DynamicTexture(new NativeImage(QslArt.WIDTH, QslArt.HEIGHT, true));
			previewId = Radiocraft.id("qsl_fill_preview");
			Minecraft.getInstance().getTextureManager().register(previewId, preview);
		}
		shown = null;
	}

	private EditBox box(int x, int y, int w, String value, int max, String label) {
		EditBox box = new EditBox(font, x, y + 9, w, 14, Component.translatable(KEY + label));
		box.setMaxLength(max);
		box.setValue(value);
		box.setHint(Component.translatable(KEY + label).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
		addRenderableWidget(box);
		boxes.add(box);
		return box;
	}

	private QslCard.Fill written() {
		return new QslCard.Fill(from == null ? "" : from.getValue(), to.getValue(), band.getValue(), frequency.getValue(), mode.getValue(), report.getValue(),
				notes.getValue(), System.currentTimeMillis(), "", "", false);
	}

	private void save(boolean sign) {
		QslCard.Fill written = written();
		String problem = card.problem(written, sign);
		if(problem != null) {
			if(minecraft != null && minecraft.player != null)
				minecraft.player.displayClientMessage(Component.translatable("message.radiocraft.qsl." + problem), true);
			return;
		}
		SQslCardPacket.send(hand, written, sign);
		onClose();
	}

	@Override
	public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		QslCard.Fill now = written();
		QslCard.Fill key = new QslCard.Fill(now.from(), now.to(), now.band(), now.frequency(), now.mode(), now.report(), now.notes(), 0L, "", "", false);
		if(!key.equals(shown) && preview.getPixels() != null) { // Repaint only on a change.
			shown = key;
			QslTextures.paint(preview.getPixels(), QslArt.back(new QslCard(card.design(), now, card.copy())));
			preview.upload();
		}
		int w = QslArt.WIDTH * previewScale, h = QslArt.HEIGHT * previewScale;
		g.fill(previewX - 2, previewY - 2, previewX + w + 2, previewY + h + 2, 0xFF202020);
		g.blit(previewId, previewX, previewY, w, h, 0.0F, 0.0F, QslArt.WIDTH, QslArt.HEIGHT, QslArt.WIDTH, QslArt.HEIGHT);
		g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
		g.drawString(font, Component.translatable(KEY + "hint"), previewX, previewY + h + 8, 0xA0A0A0);
		for(EditBox box : boxes)
			g.drawString(font, box.getMessage(), box.getX(), box.getY() - 10, 0xE0E0E0);
	}

	@Override
	public void removed() {
		super.removed();
		if(previewId != null)
			Minecraft.getInstance().getTextureManager().release(previewId);
		preview = null;
		previewId = null;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
