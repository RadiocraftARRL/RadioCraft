package com.arrl.radiocraft.common.network.serverbound;

import com.arrl.radiocraft.Radiocraft;
import com.arrl.radiocraft.common.blockentities.radio.HFRadioAllBandBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.HFRadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.RadioBlockEntity;
import com.arrl.radiocraft.common.blockentities.radio.VHFRadioBlockEntity;
import com.arrl.radiocraft.common.radio.voice.Ctcss;
import com.arrl.radiocraft.common.menus.RadioMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record SBlockRadioControlPacket(long pos, int actionId, int stepCount, boolean value) implements CustomPacketPayload {

    public enum Action {
        TOGGLE_POWER,
        SET_SSB,
        SET_PTT,
        STEP_FREQUENCY,
        SET_CW,
        SET_FM,
        SET_BAND,
        SET_TONE, // stepCount: CTCSS tone list position (Ctcss.index), 0 for none
        SET_OFFSET, // stepCount: repeater offset in Hz
        SET_SPEAKER_GAIN, // stepCount: percent
        SET_MIC_GAIN, // stepCount: percent
        TAKE_HAND_MIC, // Right click on a screen's PTT: the screen shuts and the hand mic comes off its hook (2026-11-08).
        SET_AM, // The all-band radio's AM (2026-11-08).
        SET_SQUELCH, // stepCount: percent of the S-meter's scale (2026-11-09).
                SET_AGC, // stepCount: RadioBlockEntity.AGC_OFF, AGC_SLOW or AGC_FAST (2026-11-09).
        QUICK_STORE, // QUICK held: remember this frequency (2026-11-09).
        QUICK_TOGGLE, // QUICK pressed: to the stored frequency, or back.
        MEM_STORE, // stepCount: the memory channel (0-49) to store the VFO in (all-band radio).
        VFO_MEM, // VFO/MEM pressed: between the VFO and the memories.
        MEM_SELECT, // stepCount: the memory channel to recall.
        MEM_TO_VFO // VFO/MEM held on a memory: copy it to the VFO, and back to VFO.
    }

    public static final CustomPacketPayload.Type<SBlockRadioControlPacket> TYPE = new CustomPacketPayload.Type<>(Radiocraft.id("s_block_radio_control"));

    public static final StreamCodec<ByteBuf, SBlockRadioControlPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,
            SBlockRadioControlPacket::pos,
            ByteBufCodecs.VAR_INT,
            SBlockRadioControlPacket::actionId,
            ByteBufCodecs.INT,
            SBlockRadioControlPacket::stepCount,
            ByteBufCodecs.BOOL,
            SBlockRadioControlPacket::value,
            SBlockRadioControlPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if(actionId < 0 || actionId >= Action.values().length)
                return;

                        BlockPos targetPos = BlockPos.of(pos);
            if(!com.arrl.radiocraft.common.network.PacketChecks.nearAndLoaded(context.player(), targetPos))
                return; // Never look up (and so load) a chunk the player is nowhere near.
            BlockEntity blockEntity = context.player().level().getBlockEntity(targetPos);
            if(!(blockEntity instanceof RadioBlockEntity radio))
                return;

            Action action = Action.values()[actionId];
            if(action == Action.SET_PTT) // A screen's PTT press, and whether it is allowed (logs/debug.log).
                com.arrl.radiocraft.Radiocraft.LOGGER.debug("[ptt-debug] SET_PTT {} at {}: allowed={} menu={} menuBE==radio={}", value, targetPos.toShortString(),
                        canControl(context, radio, targetPos, action), context.player().containerMenu.getClass().getSimpleName(),
                        context.player().containerMenu instanceof com.arrl.radiocraft.common.menus.RadioMenu<?> m && m.blockEntity == radio);
            if(!canControl(context, radio, targetPos, action))
                return;
            if(action == Action.TOGGLE_POWER && !radio.mayTogglePower(context.player())) {
                context.player().displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        com.arrl.radiocraft.Radiocraft.translationKey("message", "real_world_interface.op_only")), true);
                return;
            }

            switch(action) {
                case TOGGLE_POWER -> radio.toggle();
                case SET_SSB -> radio.setSSBEnabled(value);
                case SET_PTT -> radio.setPTTDown(value);
                case STEP_FREQUENCY -> radio.updateFrequency(stepCount);
                case SET_CW -> {
                    if(radio instanceof HFRadioBlockEntity hf)
                        hf.setCWEnabled(value);
                }
                case SET_FM -> {
                    if(radio instanceof HFRadioAllBandBlockEntity allBand)
                        allBand.setFMEnabled(value);
                }
                case SET_AM -> {
                    if(radio instanceof HFRadioBlockEntity hf)
                        hf.setAMEnabled(value);
                }
                case SET_AGC -> radio.setAgc(stepCount);
                case QUICK_STORE -> radio.storeQuick();
                case QUICK_TOGGLE -> radio.toggleQuick();
                case MEM_STORE -> {
                    if(radio instanceof HFRadioAllBandBlockEntity allBand)
                        allBand.storeMemory(stepCount);
                }
                case VFO_MEM -> {
                    if(radio instanceof HFRadioAllBandBlockEntity allBand)
                        allBand.toggleMemoryMode();
                }
                case MEM_SELECT -> {
                    if(radio instanceof HFRadioAllBandBlockEntity allBand)
                        allBand.selectMemory(stepCount);
                }
                case MEM_TO_VFO -> {
                    if(radio instanceof HFRadioAllBandBlockEntity allBand)
                        allBand.memoryToVfo();
                }
                case SET_BAND -> {
                    if(radio instanceof HFRadioAllBandBlockEntity allBand)
                        allBand.setBandIndex(stepCount);
                }
                case SET_TONE -> {
                    if(radio instanceof VHFRadioBlockEntity vhf)
                        vhf.setCtcssTone(Ctcss.fromIndex(stepCount));
                }
                case SET_OFFSET -> {
                    if(radio instanceof VHFRadioBlockEntity vhf)
                        vhf.setOffsetHz(stepCount);
                }
                case SET_SPEAKER_GAIN -> radio.setSpeakerGain(stepCount / 100.0F);
                case SET_MIC_GAIN -> radio.setMicGain(stepCount / 100.0F);
                case SET_SQUELCH -> {
                    if(radio.hasSquelchKnob())
                        radio.setSquelch(stepCount / 100.0F);
                }
                case TAKE_HAND_MIC -> {
                    if(radio.canLendHandMic() && context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        player.closeContainer();
                        radio.takeHandMic(player); // Says so if their hand is full or someone else has it.
                    }
                }
            }
        });
    }

    private boolean canControl(IPayloadContext context, RadioBlockEntity radio, BlockPos targetPos, Action action) {
        if(context.player().containerMenu instanceof RadioMenu<?> menu && menu.blockEntity == radio)
            return true;

        return action == Action.SET_PTT
                && !value
                && context.player().distanceToSqr(targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D) <= 64.0D;
    }

    public static void updateServer(BlockPos pos, Action action, boolean value) {
        PacketDistributor.sendToServer(new SBlockRadioControlPacket(pos.asLong(), action.ordinal(), 0, value));
    }

    public static void updateServer(BlockPos pos, Action action, int stepCount) {
        PacketDistributor.sendToServer(new SBlockRadioControlPacket(pos.asLong(), action.ordinal(), stepCount, false));
    }
}
