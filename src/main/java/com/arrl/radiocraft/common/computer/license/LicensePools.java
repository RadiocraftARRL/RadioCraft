package com.arrl.radiocraft.common.computer.license;

import com.arrl.radiocraft.Radiocraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The question pools License Study offers (computer apps 2), loaded on the server with the data packs and again on
 * {@code /reload}: first every {@code data/<namespace>/license_pools/*.json} in the data packs (RadioCraft ships the US
 * NCVEC Technician, General and Extra pools there), then every {@code *.json} in the server's
 * {@code config/radiocraft/license_pools/} folder, which replaces a pool with the same id or adds a new one. So an
 * admin updates a pool when the NCVEC publishes a new one by dropping its file in either place, no mod update needed.
 * A file that cannot be read is logged and skipped. Format: {@link LicensePool}.
 */
@EventBusSubscriber(modid = Radiocraft.MOD_ID)
public final class LicensePools extends SimplePreparableReloadListener<Map<String, LicensePool>> {

	public static final String DIRECTORY = "license_pools";

	private static volatile Map<String, LicensePool> pools = Map.of();

	@SubscribeEvent
	public static void addReloadListener(AddReloadListenerEvent event) {
		event.addListener(new LicensePools());
	}

	/** Every pool, in their {@code order} then by id. */
	public static List<LicensePool> all() {
		List<LicensePool> out = new ArrayList<>(pools.values());
		out.sort(Comparator.comparingInt(LicensePool::order).thenComparing(LicensePool::id));
		return out;
	}

	@Nullable
	public static LicensePool get(String id) {
		return pools.get(id);
	}

	/** Replaces the pools (tests). */
	public static void set(List<LicensePool> list) {
		Map<String, LicensePool> map = new LinkedHashMap<>();
		for(LicensePool pool : list)
			map.put(pool.id(), pool);
		pools = Map.copyOf(map);
	}

	/** The server's own pool folder: {@code config/radiocraft/license_pools}. */
	public static Path configFolder() {
		return FMLPaths.CONFIGDIR.get().resolve(Radiocraft.MOD_ID).resolve(DIRECTORY);
	}

	@Override
	protected Map<String, LicensePool> prepare(ResourceManager manager, ProfilerFiller profiler) {
		Map<String, LicensePool> out = new LinkedHashMap<>();
		Map<ResourceLocation, Resource> found = manager.listResources(DIRECTORY, location -> location.getPath().endsWith(".json"));
		for(Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
			String file = entry.getKey().getPath();
			String name = file.substring(file.lastIndexOf('/') + 1, file.length() - ".json".length());
			try(Reader reader = entry.getValue().openAsReader()) {
				add(out, read(reader), name, entry.getKey().toString());
			}
			catch(IOException | RuntimeException e) {
				Radiocraft.LOGGER.error("License Study: cannot read question pool {}: {}", entry.getKey(), e.getMessage());
			}
		}
		Path folder = configFolder();
		if(Files.isDirectory(folder)) {
			try(Stream<Path> files = Files.list(folder)) {
				for(Path path : files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")).sorted().toList()) {
					String name = path.getFileName().toString();
					name = name.substring(0, name.length() - ".json".length());
					try {
						add(out, Files.readString(path, StandardCharsets.UTF_8), name, path.toString());
					}
					catch(IOException | RuntimeException e) {
						Radiocraft.LOGGER.error("License Study: cannot read question pool {}: {}", path, e.getMessage());
					}
				}
			}
			catch(IOException e) {
				Radiocraft.LOGGER.error("License Study: cannot list {}: {}", folder, e.getMessage());
			}
		}
		return out;
	}

	private static String read(Reader reader) throws IOException {
		StringBuilder text = new StringBuilder();
		char[] buffer = new char[8192];
		int n;
		while((n = reader.read(buffer)) > 0)
			text.append(buffer, 0, n);
		return text.toString();
	}

	private static void add(Map<String, LicensePool> out, String json, String name, String where) {
		LicensePool pool = LicensePool.parse(json, name);
		for(String problem : pool.problems())
			Radiocraft.LOGGER.warn("License Study: {} skips {}", where, problem);
		out.put(pool.id(), pool); // A later file (the config folder) replaces an earlier one with the same id.
	}

	@Override
	protected void apply(Map<String, LicensePool> prepared, ResourceManager manager, ProfilerFiller profiler) {
		pools = Map.copyOf(prepared);
		Radiocraft.LOGGER.info("License Study: {} question pools loaded", prepared.size());
	}
}
