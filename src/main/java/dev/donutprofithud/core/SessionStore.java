package dev.donutprofithud.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Saves and loads the session totals as exact decimal strings (survives reconnects and restarts). */
public final class SessionStore {
	private final Path file;

	public SessionStore(Path file) {
		this.file = file;
	}

	public Ledger load() {
		if (!Files.isRegularFile(file)) return new Ledger();
		Properties p = new Properties();
		try (InputStream in = Files.newInputStream(file)) {
			p.load(in);
			return new Ledger(
				new BigDecimal(p.getProperty("received", "0")),
				new BigDecimal(p.getProperty("paid", "0")),
				Integer.parseInt(p.getProperty("count", "0")));
		} catch (IOException | RuntimeException e) {
			return new Ledger();
		}
	}

	public void save(Ledger ledger) throws IOException {
		Properties p = new Properties();
		p.setProperty("received", ledger.received().toPlainString());
		p.setProperty("paid", ledger.paid().toPlainString());
		p.setProperty("count", Integer.toString(ledger.count()));
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (OutputStream out = Files.newOutputStream(tmp)) {
			p.store(out, "Donut Profit HUD session totals (exact amounts)");
		}
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
