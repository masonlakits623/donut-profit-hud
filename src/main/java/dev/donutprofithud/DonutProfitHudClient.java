package dev.donutprofithud;

import com.mojang.blaze3d.platform.InputConstants;
import dev.donutprofithud.core.FlashTracker;
import dev.donutprofithud.core.Ledger;
import dev.donutprofithud.core.Payment;
import dev.donutprofithud.core.PaymentParser;
import dev.donutprofithud.core.SessionStore;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
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
	private final FlashTracker flash = new FlashTracker();

	private KeyMapping toggleKey;
	private KeyMapping settingsKey;

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

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.donutprofithud.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, category));
		settingsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.donutprofithud.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				config.visible = !config.visible;
				saveConfig();
			}
			while (settingsKey.consumeClick()) {
				if (client.screen == null) client.setScreen(new SettingsScreen(null));
			}
		});

		// System/game messages only. Signed player chat (CHAT event) is deliberately ignored.
		ClientReceiveMessageEvents.GAME.register(this::onGameMessage);

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "banner"),
			(graphics, tickCounter) -> HudRenderer.renderHud(graphics));

		LOGGER.info("Donut Profit HUD loaded (session net {} from {} payments)", ledger.net().toPlainString(), ledger.count());
	}

	private void onGameMessage(Component message, boolean overlay) {
		if (overlay) return; // action bar, never a payment confirmation
		if (!onTrackedServer()) return;
		String text = plainTextWithoutIcons(message);
		Optional<Payment> parsed = PaymentParser.parse(text);
		if (parsed.isEmpty()) return;
		Payment payment = parsed.get();
		if (payment.away() && !config.countAway) return;
		BigDecimal delta = ledger.apply(payment, config.includeTax);
		if (config.flashEnabled) flash.trigger(delta, System.currentTimeMillis());
		saveSession();
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

	private void saveSession() {
		try {
			sessionStore.save(ledger);
		} catch (IOException e) {
			LOGGER.warn("Could not save session totals", e);
		}
	}
}
