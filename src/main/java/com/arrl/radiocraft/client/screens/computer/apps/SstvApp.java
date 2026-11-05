package com.arrl.radiocraft.client.screens.computer.apps;

import com.arrl.radiocraft.client.screens.computer.ComputerSession;
import com.arrl.radiocraft.client.screens.computer.Dos;
import com.arrl.radiocraft.client.screens.computer.DosFormDialog;
import com.arrl.radiocraft.client.screens.computer.DosInput;
import com.arrl.radiocraft.common.computer.ComputerApp;
import com.arrl.radiocraft.common.computer.ComputerFileFormats;
import com.arrl.radiocraft.common.computer.ComputerStorage;
import com.arrl.radiocraft.common.computer.ImageCaption;
import com.arrl.radiocraft.common.photo.MapPalettes;
import com.arrl.radiocraft.common.photo.PhotoImage;
import com.arrl.radiocraft.common.photo.PhotoPalette;
import com.arrl.radiocraft.common.radio.data.PixelFont;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * SSTV (1.12 step 7): slow-scan television pictures over the radio, in colour (see {@link PictureApp} for the screen).
 * Pictures that come in are kept on the disk ({@code SSTV0001.IMG}) and stay digital until printed; any picture in the
 * gallery can be sent again (F3), captioned (F4: a line or two along the top and the bottom, written into a new file so
 * the first stays as it was), printed (F5) or scanned in from the scanner's glass (F6). A picture takes about half a
 * minute on the air.
 */
public final class SstvApp extends PictureApp {

	public SstvApp(ComputerSession session, ComputerApp app) {
		super(session, app);
	}

	@Override
	protected String title() {
		return Dos.tr("sstv.title");
	}

	@Override
	protected String keys() {
		return Dos.tr("sstv.keys");
	}

	@Override
	protected String words() {
		return "sstv";
	}

	@Override
	protected boolean moreKeys(int key) {
		switch(key) {
			case GLFW.GLFW_KEY_F4 -> caption();
			case GLFW.GLFW_KEY_F6 -> scan();
			default -> {
				return false;
			}
		}
		return true;
	}

	/** Writes captions onto the picture picked, as a new file. */
	private void caption() {
		String name = selectedName();
		byte[] pixels = selectedPicture();
		if(name == null || pixels == null || pixels.length != PhotoImage.PIXELS)
			return;
		java.util.function.Predicate<Character> printable = PixelFont::has;
		DosInput top = new DosInput("", ImageCaption.MAX_CHARS, printable);
		DosInput bottom = new DosInput("", ImageCaption.MAX_CHARS, printable);
		String base = name.substring(0, name.indexOf('.'));
		DosInput saveAs = DosInput.fileName((base.length() >= 8 ? base.substring(0, 7) : base) + "C.IMG");
		showDialog(new DosFormDialog(Dos.tr("sstv.caption_title"), Dos.tr("sstv.caption_hint"), List.of(
				new DosFormDialog.Field(Dos.tr("sstv.caption_top"), top),
				new DosFormDialog.Field(Dos.tr("sstv.caption_bottom"), bottom),
				new DosFormDialog.Field(Dos.tr("sstv.caption_save"), saveAs)), values -> {
			if(values.get(0).isBlank() && values.get(1).isBlank())
				return Dos.tr("sstv.caption_none");
			String file = ComputerStorage.suggest(values.get(2), ComputerFileFormats.IMAGE);
			PhotoPalette palette = MapPalettes.vanilla();
			byte white = palette.id(palette.nearest(0xFFFFFF)), black = palette.id(palette.nearest(0x000000));
			byte[] captioned = ImageCaption.apply(pixels, values.get(0), values.get(1), white, black);
			session.files().write(file, captioned, response -> {
				if(response.ok()) {
					pictures.put(file, captioned);
					session.message(Dos.tr("edit.saved", file), false);
					listFiles(file);
				}
				else
					showError(response);
			});
			return null;
		}));
	}

	/** Scans the picture on the scanner's glass into the gallery. */
	private void scan() {
		String next = ComputerStorage.nextName("SCAN", ComputerFileFormats.IMAGE, names::contains);
		session.scanner().save(next == null ? "SCAN" : next, response -> {
			if(!response.ok()) {
				showError(response);
				return;
			}
			String file = response.data().getString("name");
			if(!ComputerStorage.extension(file).equals(ComputerFileFormats.IMAGE)) {
				session.message(Dos.tr("sstv.scan_not_picture", file), true);
				return;
			}
			pictures.remove(file);
			session.message(Dos.tr("edit.saved", file), false);
			listFiles(file);
		});
	}
}
