package dev.donutprofithud;

import com.mojang.blaze3d.platform.InputConstants;
import dev.donutprofithud.core.DayRecord;
import dev.donutprofithud.core.FlashTracker;
import dev.donutprofithud.core.History;
import dev.donutprofithud.core.HistoryStore;
import dev.donutprofithud.core.Ledger;
import dev.donutprofithud.core.Payment;
import dev.donutprofithud.core.PaymentParser;
import dev.donutprofithud.core.SessionStore;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point. Listens only to incoming system ("game") messages; never sends chat, commands,
 * clicks or payments.
 */
public final class DonutProfitHudClient implements ClientModInitializer {
	public static final String MOD_ID = "donutprofithud";
	public static final Logger LOGGER = LoggerFactory.getLogger("Donut Profit HUD");

	private static DonutProfitHudClient instance;

	private Path configFile;
	private HudConfig config;
	private SessionStore sessionStore;
	private Ledger ledger;
	private HistoryStore historyStore;
	private History history;
	private final FlashTracker flash = new FlashTracker();

	private KeyMapping toggleKey;
	private KeyMapping settingsKey;
	private KeyMapping historyKey;

	public static DonutProfitHudClient get() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		Path configDir = FabricLoader.getInstance().getConfigDir();
		configFile = configDir.resolve("donutprofithud.json");
		config = HudConfig.load(configFile);
		sessionStore = new SessionStore(configDir.resolve("donutprofithud-session.properties"));
		ledger = sessionStore.load();
		historyStore = new HistoryStore(configDir.resolve("donutprofithud-history.json"));
		history = historyStore.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.donutprofithud.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, category));
		settingsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.donutprofithud.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
		historyKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.donutprofithud.history", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				config.visible = !config.visible;
				saveConfig();
			}
			while (settingsKey.consumeClick()) {
				if (client.screen == null) client.setScreen(new SettingsScreen(null));
			}
			while (historyKey.consumeClick()) {
				if (client.screen == null) client.setScreen(new HistoryScreen(null));
			}
		});

		// System/game messages only. Signed player chat (CHAT event) is deliberately ignored.
		ClientReceiveMessageEvents.GAME.register(this::onGameMessage);

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "banner"),
			(graphics, tickCounter) -> HudRenderer.renderHud(graphics));

		LOGGER.info("Donut Profit HUD loaded (session net {} from {} payments, {} days of history)",
			ledger.net().toPlainString(), ledger.count(), history.daysNewestFirst().size());
	}

	private void onGameMessage(Component message, boolean overlay) {
		if (overlay) return; // action bar, never a payment confirmation
		if (!onTrackedServer()) return;
		String text = plainTextWithoutIcons(message);
		long now = System.currentTimeMillis();
		Optional<BigDecimal> balance = PaymentParser.parseBalance(text);
		if (balance.isPresent()) {
			// Reply to a /bal the player typed. The mod never sends /bal itself.
			history.recordBalance(balance.get(), now, zone(), config.dayStartHour);
			saveHistory();
			return;
		}
		Optional<Payment> parsed = PaymentParser.parse(text);
		if (parsed.isEmpty()) return;
		Payment payment = parsed.get();
		if (payment.away() && !config.countAway) return;
		BigDecimal delta = ledger.apply(payment, config.includeTax);
		history.applyPayment(payment, config.includeTax, now, zone(), config.dayStartHour);
		if (config.flashEnabled) flash.trigger(delta, now);
		saveSession();
		saveHistory();
	}

	public static ZoneId zone() {
		return ZoneId.systemDefault();
	}

	/** Key of the current day ("2026-11-23"), using the configured day start hour. */
	public String todayKey() {
		return History.keyOf(System.currentTimeMillis(), zone(), config.dayStartHour);
	}

	public DayRecord today() {
		return history.day(todayKey());
	}

	/** The number on the banner: today's profit or the session total, depending on the setting. */
	public BigDecimal shownNet() {
		return config.shows == HudConfig.Shows.SESSION ? ledger.net() : today().net();
	}

	private boolean onTrackedServer() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.isSingleplayer()) return false;
		if (!config.donutOnly) return true;
		ServerData server = mc.getCurrentServer();
		if (server == null || server.ip == null) return false;
		String match = config.serverMatch == null ? "" : config.serverMatch.trim().toLowerCase(Locale.ROOT);
		return !match.isEmpty() && server.ip.toLowerCase(Locale.ROOT).contains(match);
	}

	/**
	 * Flattens a chat component to plain text, skipping icon objects (player heads / sprites,
	 * which 1.21.9+ can embed in chat) so "[head] name paid you $ 5M" reads as "name paid you $ 5M".
	 */
	static String plainTextWithoutIcons(Component component) {
		StringBuilder sb = new StringBuilder();
		appendPlain(component, sb, 0);
		return sb.toString();
	}

	private static void appendPlain(Component component, StringBuilder sb, int depth) {
		if (depth > 32 || sb.length() > 512) return;
		ComponentContents contents = component.getContents();
		if (!(contents instanceof ObjectContents)) {
			contents.visit(part -> {
				sb.append(part);
				return Optional.empty();
			});
		}
		for (Component sibling : component.getSiblings()) {
			appendPlain(sibling, sb, depth + 1);
		}
	}

	public HudConfig config() {
		return config;
	}

	public Ledger ledger() {
		return ledger;
	}

	public History history() {
		return history;
	}

	public FlashTracker flash() {
		return flash;
	}

	public void saveConfig() {
		config.save(configFile);
	}

	public void resetSession() {
		ledger.reset();
		flash.clear();
		saveSession();
	}

	/** Resets what the banner shows: today's payments (TODAY) or the session totals (SESSION). */
	public void resetShown() {
		if (config.shows == HudConfig.Shows.SESSION) {
			resetSession();
		} else {
			history.resetDayPayments(todayKey());
			flash.clear();
			saveHistory();
		}
	}

	public void deleteDay(String key) {
		history.deleteDay(key);
		saveHistory();
	}

	public void saveHistory() {
		try {
			historyStore.save(history);
		} catch (IOException e) {
			LOGGER.warn("Could not save history", e);
		}
	}

	private void saveSession() {
		try {
			sessionStore.save(ledger);
		} catch (IOException e) {
			LOGGER.warn("Could not save session totals", e);
		}
	}
}
