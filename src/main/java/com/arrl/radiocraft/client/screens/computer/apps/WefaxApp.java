package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosColours;
import com.arrl.radiocraft.client.screens.computer.DosDialog;
import com.arrl.radiocraft.client.screens.computer.TextGrid;
import com.arrl.radiocraft.common.computer.ComputerApp;
import net.minecraft.nbt.CompoundTag;
import org.lwjgl.glfw.GLFW;

/**
 * WEFAX (1.12 step 7): weather fax charts over the radio, in greys (see {@link PictureApp} for the screen). It copies
 * the charts other stations send, the Ionospheric Processors' solar bulletins and alerts, and on request (F4, WX REPT)
 * the coast station's broadcast: the radar chart, then the forecast; each is kept on the disk ({@code WFX00001.IMG}).
 * F7 draws a chart of the weather here (radar or forecast) for this station to send as its own with F3; any picture
 * goes out as a grey chart. WEFAX receivers with AUTO on print what it sends.
 */
public final class WefaxApp extends PictureApp {

	public WefaxApp(ComputerSession session, ComputerApp app) {
		super(session, app);
	}

	@Override
	protected String title() {
		return Dos.tr("wefax.title");
	}

	@Override
	protected String keys() {
		return Dos.tr("wefax.keys");
	}

	@Override
	protected String words() {
		return "wefax";
	}

	@Override
	protected boolean moreKeys(int key) {
		switch(key) {
			case GLFW.GLFW_KEY_F4 -> wxRept();
			case GLFW.GLFW_KEY_F7 -> chart();
			default -> {
				return false;
			}
		}
		return true;
	}

	private void wxRept() {
		session.app(app, "wxrept", new CompoundTag(), response -> {
			if(response.ok()) {
				session.message(Dos.tr("wefax.wxrept_started"), false);
				showLive = true;
				refresh();
			}
			else
				showError(response);
		});
	}

	private void chart() {
		showDialog(DosDialog.choice(Dos.tr("wefax.chart_title"), Dos.tr("wefax.chart_ask"),
				new String[] { Dos.tr("wefax.chart_radar"), Dos.tr("wefax.chart_forecast"), Dos.tr("button.cancel") }, index -> {
			if(index < 0 || index > 1)
				return;
			CompoundTag data = new CompoundTag();
			data.putString("kind", index == 0 ? "radar" : "forecast");
			session.app(app, "chart", data, response -> {
				if(response.ok()) {
					String name = response.data().getString("name");
					pictures.remove(name);
					session.message(Dos.tr("edit.saved", name), false);
					listFiles(name);
				}
				else
					showError(response);
			});
		}));
	}

	@Override
	protected void renderExtra(TextGrid g, int bg) {
		if(status == null)
			return;
		int coming = status.getInt("broadcast");
		if(coming > 0)
			g.put(40, 7, Dos.fit(Dos.tr("wefax.coast_coming", coming), 37), DosColours.LIGHT_CYAN, bg);
		else if(!status.getBoolean("canWxrept"))
			g.put(40, 7, Dos.fit(Dos.tr("wefax.no_antenna_hint"), 37), DosColours.YELLOW, bg);
	}
}
