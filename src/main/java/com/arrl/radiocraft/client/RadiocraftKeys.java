package com.arrl.radiocraft.client;

import com.arrl.radiocraft.Radiocraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** RadioCraft's key bindings (Options, Controls, under RadioCraft). */
@EventBusSubscriber(modid = Radiocraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class RadiocraftKeys {

    /** Hold to talk on a handheld worn on the belt (Curios), the way right click talks on one in the hand. */
    public static final KeyMapping WORN_PTT = new KeyMapping("key.radiocraft.worn_ptt", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.radiocraft");

    /** A straight key: hold to send CW from an open HF radio screen in CW mode (Space since 2026-11-12, maintainer). */
    public static final KeyMapping CW_KEY = new KeyMapping("key.radiocraft.cw_key", KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE, "key.categories.radiocraft");

    /** The iambic keyer's paddles (2026-11-12): dit and dah, with the keyer's screen open. */
    public static final KeyMapping KEYER_DIT = new KeyMapping("key.radiocraft.keyer_dit", KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_A, "key.categories.radiocraft");
    public static final KeyMapping KEYER_DAH = new KeyMapping("key.radiocraft.keyer_dah", KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_D, "key.categories.radiocraft");

    /** Opens the panel of the HF manpack the player is wearing. */
    public static final KeyMapping OPEN_MANPACK = new KeyMapping("key.radiocraft.open_manpack", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.radiocraft");

    private RadiocraftKeys() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(WORN_PTT);
        event.register(CW_KEY);
        event.register(KEYER_DIT);
        event.register(KEYER_DAH);
        event.register(OPEN_MANPACK);
    }
}
