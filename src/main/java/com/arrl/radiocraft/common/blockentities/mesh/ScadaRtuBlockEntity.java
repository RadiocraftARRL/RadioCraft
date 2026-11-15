package com.arrl.radiocraft.common.blockentities.mesh;

import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.radio.mesh.MeshRole;
import com.arrl.radiocraft.common.radio.scada.Scada;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A SCADA remote terminal unit (2026-11-10): reports its IN faces to the master when polled and puts out on its OUT
 * faces what the master sends back. Its id is handed out when a license is used on it (1, 2, 3... on that frequency).
 */
public class ScadaRtuBlockEntity extends ScadaFacesBlockEntity {

	public ScadaRtuBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.SCADA_RTU.get(), pos, state);
	}

	@Override
	public MeshRole role() {
		return MeshRole.RTU;
	}

	@Override
	protected Scada.FaceMode[] modes(Scada scada) {
		return scada.rtu(globalPos()).modes;
	}

	@Override
	protected int[] inputs(Scada scada) {
		return scada.rtu(globalPos()).inputs;
	}

	@Override
	protected int[] outputs(Scada scada) {
		return scada.rtu(globalPos()).outputs;
	}

	@Override
	protected void setPowered(Scada scada, boolean powered) {
		if(scada.rtu(globalPos()).powered != powered) {
			scada.rtu(globalPos()).powered = powered;
			scada.changed();
		}
	}

	@Override
	public List<Component> statusLines() {
		List<Component> out = super.statusLines();
		if(getFrequency() > 0.0F)
			out.add(1, Component.translatable("message.radiocraft.scada.rtu_id", getAddress()));
		return out;
	}
}
