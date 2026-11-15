package com.arrl.radiocraft.datagen.lang;

import com.arrl.radiocraft.common.init.RadiocraftBlocks;
import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.function.BiConsumer;

/** Weather satellite imaging (2026-11-12): the WXSAT program and the External CRT Monitor (also a premium radio's waterfall). */
public final class WxSatLang {

	private WxSatLang() {}

	public static void add(LanguageProvider provider) {
		provider.addBlock(RadiocraftBlocks.EXTERNAL_CRT_MONITOR, "External CRT Monitor");
		provider.add("tooltip.radiocraft.external_crt_monitor", "A vintage CRT. Beside a radio computer running WXSAT (or beside a monitor that is), it shows the weather satellite mosaic centred where it stands; monitors side by side make one big screen. Right-click: live mosaic, loop, off.");
		provider.add("tooltip.radiocraft.external_crt_monitor.radio", "On its own touching a premium radio (and no WXSAT computer), it shows a waterfall round the radio's dial. Right-click: narrow, wide, off.");
		BiConsumer<String, String> m = (key, text) -> provider.add("message.radiocraft.external_crt_monitor." + key, text);
		m.accept("mode", "External CRT Monitor: %s");
		m.accept("mode.live", "LIVE MOSAIC");
		m.accept("mode.loop", "LOOP");
		m.accept("mode.off", "OFF");
		m.accept("mode.narrow", "NARROW WATERFALL");
		m.accept("mode.wide", "WIDE WATERFALL");

		BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("app.wxsat", "Weather Satellite");
		s.accept("app.wxsat.description", "Weather satellite pictures from commercial satellites: watch a pass come in, build a regional mosaic, loop it, save and print.");
		s.accept("wx.title", "WXSAT - Weather Satellite Pictures");
		s.accept("wx.keys", "F5=Live F6=Mosaic F7=Loop Wheel=Zoom Drag=Pan Home=Stn F3=Save F4=Print X=Exit");
		s.accept("wx.menu.file", "File");
		s.accept("wx.menu.view", "View");
		s.accept("wx.menu.save", "Save picture...");
		s.accept("wx.menu.print", "Print picture");
		s.accept("wx.menu.clear", "Clear the mosaic...");
		s.accept("wx.menu.exit", "Exit");
		s.accept("wx.menu.live", "Live pass");
		s.accept("wx.menu.mosaic", "Mosaic");
		s.accept("wx.menu.loop", "Loop the passes");
		s.accept("wx.menu.zoom_out", "Zoom out");
		s.accept("wx.menu.zoom_in", "Zoom in");
		s.accept("wx.menu.home", "Back to the station");
		s.accept("wx.menu.options", "Options");
		s.accept("wx.menu.lan_block", "Block LAN sync");
		s.accept("wx.lan_blocked", "LAN sync blocked: this mosaic is its own");
		s.accept("wx.lan_unblocked", "LAN sync on: sharing pictures with WXSAT on the LAN");
		s.accept("wx.lan_on", "LAN: sharing with %s computer(s)");
		s.accept("wx.lan_off", "LAN sync blocked");
		s.accept("wx.reading", "Listening for weather satellites...");
		s.accept("wx.loading", "Painting the mosaic...");
		s.accept("wx.live", "Live pass");
		s.accept("wx.mosaic", "Mosaic, %s blocks a pixel");
		s.accept("wx.loop", "Loop, %s blocks a pixel");
		s.accept("wx.live_none1", "No pass received yet. Tune a radio linked");
		s.accept("wx.live_none2", "to this computer to an imaging downlink.");
		s.accept("wx.mosaic_none1", "Nothing stitched yet: the mosaic fills in");
		s.accept("wx.mosaic_none2", "with every pass this station receives.");
		s.accept("wx.frame", "Pass %s/%s  %s  %s");
		s.accept("wx.paused", "PAUSED");
		s.accept("wx.station", "Station");
		s.accept("wx.not_overworld", "Satellites only pass over the Overworld");
		s.accept("wx.radio", "Radio on %s MHz");
		s.accept("wx.no_radio", "No radio linked to the computer");
		s.accept("wx.receiving", "RECEIVING %s");
		s.accept("wx.signal", "Signal");
		s.accept("wx.last_pass", "Last pass %s, %s ago");
		s.accept("wx.waiting", "Waiting for a pass");
		s.accept("wx.lines", "%s lines, %s lost");
		s.accept("wx.frame_row", "Frame %s, row %s of %s");
		s.accept("wx.frame_done", "Frame %s complete");
		s.accept("wx.stored", "Mosaic: %s passes, %s tiles");
		s.accept("wx.imagers", "Imaging satellites");
		s.accept("wx.no_imagers", "None: a commercial satellite's owner switches it to IMAGING in SATTRACK");
		s.accept("wx.up", "UP %s %s");
		s.accept("wx.next", "next in %s, max %s");
		s.accept("wx.hint_live", "Newest line at the bottom");
		s.accept("wx.hint_mosaic", "Drag or arrows pan, wheel zooms");
		s.accept("wx.you_are", "Red ring: this station");
		s.accept("wx.why.no_radio", "%s up: link a radio");
		s.accept("wx.why.wrong_freq", "%s sends on %s");
		s.accept("wx.why.wrong_freq2", "Radio is on %3$s MHz");
		s.accept("wx.why.sat_off", "%s up but switched off");
		s.accept("wx.why.unheard", "%s up but not heard");
		s.accept("wx.why.unheard2", "Too weak, roofed, or radio off");
		s.accept("wx.monitor_hint", "CRT monitor by PC shows map");
		s.accept("wx.hint_loop", "Space pauses, PgUp/PgDn step");
		s.accept("wx.save_title", "Save picture");
		s.accept("wx.save_prompt", "Save the view (128 x 128) as:");
		s.accept("wx.saved", "Saved %s");
		s.accept("wx.printed", "Printed");
		s.accept("wx.clear_title", "Clear the mosaic");
		s.accept("wx.clear_prompt", "Forget every pass stored on this computer?");
	}
}
