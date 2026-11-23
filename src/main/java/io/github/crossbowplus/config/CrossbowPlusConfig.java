package io.github.crossbowplus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.crossbowplus.CrossbowPlusClient;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;

public final class CrossbowPlusConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("crossbow_plus.json");
	private static boolean enabled = true;
	private static boolean axShulkersArrowRefill = false;

	private CrossbowPlusConfig() {
	}

	public static void load() {
		if (!Files.exists(CONFIG_PATH)) {
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonObject()) {
				throw new IOException("Config root must be a JSON object");
			}

			JsonObject object = root.getAsJsonObject();
			enabled = readBoolean(object, "enabled", true);
			axShulkersArrowRefill = readBoolean(object, "axShulkersArrowRefill", false);
		} catch (Exception exception) {
			CrossbowPlusClient.LOGGER.error("Failed to load {}", CONFIG_PATH, exception);
		}
	}

	public static void save() {
		JsonObject object = new JsonObject();
		object.addProperty("enabled", enabled);
		object.addProperty("axShulkersArrowRefill", axShulkersArrowRefill);

		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Path temporaryPath = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(temporaryPath, StandardCharsets.UTF_8)) {
				GSON.toJson(object, writer);
			}

			try {
				Files.move(temporaryPath, CONFIG_PATH, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporaryPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			CrossbowPlusClient.LOGGER.error("Failed to save {}", CONFIG_PATH, exception);
		}
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(boolean value) {
		enabled = value;
	}

	public static boolean isAxShulkersArrowRefillEnabled() {
		return axShulkersArrowRefill;
	}

	public static void setAxShulkersArrowRefillEnabled(boolean value) {
		axShulkersArrowRefill = value;
	}

	private static boolean readBoolean(JsonObject object, String key, boolean defaultValue) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
			? element.getAsBoolean()
			: defaultValue;
	}
}
