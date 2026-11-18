package com.arrl.radiocraft.client.screens;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every GUI layout's size matches its background picture (2026-11-18): the screens draw the background at the layout's
 * width and height, so a picture of another size is stretched and its painted knobs slide away from the live ones (the
 * PC68's background had lost the bottom of its mic cord, 110 rows of 128).
 */
class GuiLayoutSizeTest {

	@Test
	void layoutsMatchTheirBackgrounds() throws IOException {
		Path assets = projectRoot().resolve("src/main/resources/assets/radiocraft");
		List<String> problems = new ArrayList<>();
		try(Stream<Path> files = Files.list(assets.resolve("gui_layouts"))) {
			for(Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				JsonObject layout = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				String background = layout.has("background") ? layout.get("background").getAsString() : layout.get("texture").getAsString();
				String path = background.substring(background.indexOf(':') + 1);
				Path picture = assets.resolve(path.endsWith(".png") ? path : path + ".png");
				BufferedImage image = ImageIO.read(picture.toFile());
				int width = layout.get("width").getAsInt(), height = layout.get("height").getAsInt();
				if(image.getWidth() != width || image.getHeight() != height)
					problems.add(file.getFileName() + " is " + width + " x " + height + " but " + picture.getFileName() + " is "
							+ image.getWidth() + " x " + image.getHeight());
			}
		}
		assertTrue(problems.isEmpty(), String.join("; ", problems));
	}

	/** The repository root: the tests run from inside the build folder. */
	private static Path projectRoot() {
		for(Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent())
			if(Files.isDirectory(dir.resolve("src/main/resources/assets/radiocraft")))
				return dir;
		return Path.of("");
	}
}
