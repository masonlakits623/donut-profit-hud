package dev.donutprofithud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** User settings, stored as JSON in config/donutprofithud.json. */
public final class HudConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public boolean visible = true;
	public String label = "Net";
	/** Horizontal centre of the banner as a fraction of the screen width (0.5 = top centre). */
	public double xFraction = 0.5;
	/** Distance of the banner from the top of the screen, in GUI pixels. */
	public int y = 4;
	public float scale = 1.0f;
	public int labelColor = 0xFFFFFF;
	public int positiveColor = 0x55FF55;
	public int negativeColor = 0xFF5555;
	public int zeroColor = 0xFFFFFF;
	/** Background colour and opacity in percent (0-100). */
	public int bgColor = 0x000000;
	public int bgOpacity = 45;
	/** Border around the banner (0 = no border). */
	public int borderWidth = 0;
	public int borderColor = 0xFFD700;
	public int borderOpacity = 100;
	public boolean textShadow = true;
	public boolean flashEnabled = true;
	public int flashMs = 2500;
	/** Count DonutSMP's payment tax as part of what you paid. */
	public boolean includeTax = true;
	/** Count the join summary "You were paid $ X while you were away" as received. */
	public boolean countAway = true;
	/** Only count messages while connected to a server whose address contains {@link #serverMatch}. */
	public boolean donutOnly = true;
	public String serverMatch = "donutsmp";

	public static HudConfig load(Path file) {
		if (Files.isRegularFile(file)) {
			try (Reader r = Files.newBufferedReader(file)) {
				HudConfig c = GSON.fromJson(r, HudConfig.class);
				if (c != null) return c.sanitize();
			} catch (IOException | RuntimeException e) {
				DonutProfitHudClient.LOGGER.warn("Could not read {}, using defaults", file, e);
			}
		}
		return new HudConfig();
	}

	public void save(Path file) {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer w = Files.newBufferedWriter(tmp)) {
				GSON.toJson(sanitize(), w);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			DonutProfitHudClient.LOGGER.warn("Could not save {}", file, e);
		}
	}

	public HudConfig sanitize() {
		if (label == null) label = "Net";
		if (label.length() > 32) label = label.substring(0, 32);
		if (serverMatch == null) serverMatch = "donutsmp";
		xFraction = clamp(xFraction, 0.0, 1.0);
		y = (int) clamp(y, 0, 2000);
		scale = (float) clamp(scale, 0.5, 4.0);
		bgOpacity = (int) clamp(bgOpacity, 0, 100);
		borderOpacity = (int) clamp(borderOpacity, 0, 100);
		borderWidth = (int) clamp(borderWidth, 0, 4);
		bgColor &= 0xFFFFFF;
		borderColor &= 0xFFFFFF;
		flashMs = (int) clamp(flashMs, 250, 10_000);
		labelColor &= 0xFFFFFF;
		positiveColor &= 0xFFFFFF;
		negativeColor &= 0xFFFFFF;
		zeroColor &= 0xFFFFFF;
		return this;
	}

	private static double clamp(double v, double lo, double hi) {
		return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
	}
}
