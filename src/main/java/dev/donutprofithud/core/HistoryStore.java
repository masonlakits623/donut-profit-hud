package dev.donutprofithud.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Saves the daily history as JSON (exact decimal amounts). A broken file starts a new history. */
public final class HistoryStore {
	private static final Gson GSON = new GsonBuilder().create();

	private static final class FileData {
		int version = 1;
		BigDecimal lastBalance;
		long lastBalanceAt;
		List<DayRecord> days;
	}

	private final Path file;

	public HistoryStore(Path file) {
		this.file = file;
	}

	public History load() {
		History h = new History();
		if (!Files.isRegularFile(file)) return h;
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			FileData d = GSON.fromJson(r, FileData.class);
			if (d != null) h.restore(d.days, d.lastBalance, d.lastBalanceAt);
		} catch (IOException | RuntimeException e) {
			// keep an empty history; the broken file is overwritten on the next save
		}
		return h;
	}

	public void save(History h) throws IOException {
		FileData d = new FileData();
		synchronized (h) {
			d.days = h.snapshot();
			d.lastBalance = h.lastBalance();
			d.lastBalanceAt = h.lastBalanceAt();
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(d, w);
			}
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}
}
