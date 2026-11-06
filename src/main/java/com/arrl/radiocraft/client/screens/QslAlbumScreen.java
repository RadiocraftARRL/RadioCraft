package com.arrl.radiocraft.client.screens;

import com.arrl.radiocraft.client.qsl.QslTextures;
import com.arrl.radiocraft.common.init.RadiocraftItems;
import com.arrl.radiocraft.common.items.QslAlbumItem;
import com.arrl.radiocraft.common.items.QslCardItem;
import com.arrl.radiocraft.common.network.serverbound.SQslAlbumPacket;
import com.arrl.radiocraft.common.qsl.QslArt;
import com.arrl.radiocraft.common.qsl.QslCard;
import com.arrl.radiocraft.common.qsl.QslCards;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Paging through a QSL album (computer batch 3C): one card at a time, large, with its details; click the card (or
 * Space) to turn it over, the arrows or the buttons for the next and last, "Take out" puts the card in the inventory
 * ({@link SQslAlbumPacket}). Reads the album in the hand every frame, so it follows what the server says.
 */
public class QslAlbumScreen extends Screen {

	private static final String KEY = "screen.radiocraft.qsl_album.";

	private final InteractionHand hand;
	private int page = 0;
	private boolean back = false;
	private int cardX, cardY, scale;

	public QslAlbumScreen(InteractionHand hand) {
		super(Component.translatable(KEY + "title"));
		this.hand = hand;
	}

	public static void open(InteractionHand hand) {
		Minecraft.getInstance().setScreen(new QslAlbumScreen(hand));
	}

	private List<ItemStack> cards() {
		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null)
			return List.of();
		ItemStack album = mc.player.getItemInHand(hand);
		return album.is(RadiocraftItems.QSL_ALBUM.get()) ? QslAlbumItem.cards(album) : List.of();
	}

	@Override
	protected void init() {
		scale = width >= 330 && height >= 260 ? 2 : 1;
		cardX = (width - QslArt.WIDTH * scale) / 2;
		cardY = Math.max(24, height / 2 - QslArt.HEIGHT * scale / 2 - 30);
		int y = cardY + QslArt.HEIGHT * scale + 54;
		int mid = width / 2;
		addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1)).bounds(mid - 110, y, 30, 20).build());
		addRenderableWidget(Button.builder(Component.translatable(KEY + "flip"), b -> back = !back).bounds(mid - 75, y, 70, 20).build());
		addRenderableWidget(Button.builder(Component.translatable(KEY + "take"), b -> takeOut()).bounds(mid + 5, y, 70, 20).build());
		addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1)).bounds(mid + 80, y, 30, 20).build());
	}

	private void turn(int by) {
		int size = cards().size();
		if(size == 0)
			return;
		page = Math.floorMod(page + by, size);
		back = false;
	}

	private void takeOut() {
		if(page < cards().size())
			SQslAlbumPacket.send(hand, page);
	}

	@Override
	public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
		List<ItemStack> cards = cards();
		if(cards.isEmpty()) {
			g.drawCenteredString(font, Component.translatable(KEY + "empty"), width / 2, height / 2 - 10, 0xA0A0A0);
			return;
		}
		page = Math.min(page, cards.size() - 1);
		ItemStack stack = cards.get(page);
		QslCard card = QslCards.card(stack);
		if(card == null)
			return;
		int w = QslArt.WIDTH * scale, h = QslArt.HEIGHT * scale;
		g.fill(cardX + 3, cardY + 3, cardX + w + 3, cardY + h + 3, 0x80000000); // A shadow.
		g.blit(QslTextures.get(card, back), cardX, cardY, w, h, 0.0F, 0.0F, QslArt.WIDTH, QslArt.HEIGHT, QslArt.WIDTH, QslArt.HEIGHT);
		String count = stack.getCount() > 1 ? " (x" + stack.getCount() + ")" : "";
		g.drawCenteredString(font, Component.translatable(KEY + "page", page + 1, cards.size()).getString() + count, width / 2, cardY - 12, 0xE0E0E0);
		int y = cardY + h + 6;
		for(Component line : QslCardItem.lines(card)) {
			g.drawCenteredString(font, line, width / 2, y, 0xC0C0C0);
			y += 10;
			if(y > cardY + h + 50)
				break;
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if(mouseX >= cardX && mouseY >= cardY && mouseX < cardX + QslArt.WIDTH * scale && mouseY < cardY + QslArt.HEIGHT * scale) {
			back = !back;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		switch(key) {
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP -> turn(-1);
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN -> turn(1);
			case GLFW.GLFW_KEY_SPACE -> back = !back;
			default -> {
				return super.keyPressed(key, scanCode, modifiers);
			}
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if(scrollY != 0.0D)
			turn(scrollY > 0 ? -1 : 1);
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
