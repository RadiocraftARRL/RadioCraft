package com.arrl.radiocraft.client.compat;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.client.screens.AprsTerminalScreen;
import com.arrl.radiocraft.client.screens.StationPanelScreen;
import com.arrl.radiocraft.client.screens.RadioFaxScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI support, loaded only when JEI is installed. The station terminal screens are laid out small and drawn scaled up
 * (see {@link StationPanelScreen#getScale}), so JEI must be told where it really is on the window, or it lays its
 * item list out over it.
 */
@JeiPlugin
public class RadiocraftJeiPlugin implements IModPlugin {

	@Override
	public @NotNull ResourceLocation getPluginUid() {
		return Radiocraft.id("jei");
	}

	@Override
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		registration.addGuiScreenHandler(AprsTerminalScreen.class, RadiocraftJeiPlugin::properties);
		registration.addGuiScreenHandler(RadioFaxScreen.class, RadiocraftJeiPlugin::properties);
		registration.addGuiScreenHandler(com.arrl.radiocraft.client.screens.TeletypeScreen.class, RadiocraftJeiPlugin::properties);
		registration.addGuiScreenHandler(com.arrl.radiocraft.client.screens.DirectionFinderScreen.class, RadiocraftJeiPlugin::properties);
		registration.addGuiScreenHandler(com.arrl.radiocraft.client.screens.MicrowaveRadioScreen.class, RadiocraftJeiPlugin::properties);
		registration.addGuiScreenHandler(com.arrl.radiocraft.client.screens.TelephoneScreen.class, RadiocraftJeiPlugin::properties);
		registration.addGuiScreenHandler(com.arrl.radiocraft.client.screens.WefaxReceiverScreen.class, RadiocraftJeiPlugin::properties);
		registration.addGuiScreenHandler(com.arrl.radiocraft.client.screens.IonosphericProcessorScreen.class, RadiocraftJeiPlugin::properties); // Was missing (2026-10-27): JEI covered it.
		// Every scaled panel, listed above or not, also reports where it really is, so JEI moves its list off it or
		// hides what would land on it (2026-10-31: JEI still overlapped some).
		registration.addGenericGuiContainerHandler(StationPanelScreen.class, new PanelArea());
		// The DTMF screens have no slots: no item list beside them, and no JEI hotkeys taking keys typed into them.
		registration.addGuiScreenHandler(com.arrl.radiocraft.client.screens.DtmfEncoderScreen.class, screen -> null);
		registration.addGuiScreenHandler(com.arrl.radiocraft.client.screens.DtmfDecoderScreen.class, screen -> null);
		registration.addGuiScreenHandler(com.arrl.radiocraft.client.screens.moderation.ModerationComputerScreen.class, screen -> null); // It fills the window.
		registration.addGuiScreenHandler(com.arrl.radiocraft.client.screens.computer.RadioComputerScreen.class, screen -> null); // So does the radio computer.
	}

	/** Recipes JEI must not show: the MARS mod is contraband (maintainer, 2026-11-05). The item stays in the list. */
	static final List<ResourceLocation> HIDDEN_RECIPES = List.of(Radiocraft.id("mars_mod"), Radiocraft.id("mars_mod_install"));

	@Override
	public void onRuntimeAvailable(@NotNull IJeiRuntime runtime) {
		net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
		if(level == null)
			return;
		List<RecipeHolder<CraftingRecipe>> hidden = new ArrayList<>();
		for(ResourceLocation id : HIDDEN_RECIPES) {
			level.getRecipeManager().byKey(id).ifPresent(holder -> {
				if(holder.value() instanceof CraftingRecipe crafting)
					hidden.add(new RecipeHolder<>(holder.id(), crafting));
			});
		}
		if(!hidden.isEmpty())
			runtime.getRecipeManager().hideRecipes(RecipeTypes.CRAFTING, hidden);
	}

	/** The panel's rectangle on the window, scaled up as it is drawn. */
	private static final class PanelArea implements IGuiContainerHandler<StationPanelScreen<?>> {
		@Override
		public @NotNull List<Rect2i> getGuiExtraAreas(@NotNull StationPanelScreen<?> screen) {
			float scale = screen.getScale();
			return List.of(new Rect2i((int)Math.floor(screen.getGuiLeft() * scale), (int)Math.floor(screen.getGuiTop() * scale),
					(int)Math.ceil(screen.getXSize() * scale), (int)Math.ceil(screen.getYSize() * scale)));
		}
	}

	private static IGuiProperties properties(StationPanelScreen<?> screen) {
		float scale = screen.getScale();
		return new Properties(screen.getClass(), Math.round(screen.getGuiLeft() * scale), Math.round(screen.getGuiTop() * scale),
				Math.round(screen.getXSize() * scale), Math.round(screen.getYSize() * scale),
				Math.round(screen.width * scale), Math.round(screen.height * scale));
	}

	private record Properties(Class<? extends Screen> screenClass, int guiLeft, int guiTop, int guiXSize, int guiYSize,
	                          int screenWidth, int screenHeight) implements IGuiProperties {}
}
