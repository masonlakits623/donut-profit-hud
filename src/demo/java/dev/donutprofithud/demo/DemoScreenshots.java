package dev.donutprofithud.demo;

import dev.donutprofithud.DonutProfitHudClient;
import dev.donutprofithud.HistoryScreen;
import dev.donutprofithud.HudConfig;
import dev.donutprofithud.HudFonts;
import dev.donutprofithud.SettingsScreen;
import dev.donutprofithud.core.DayRecord;
import dev.donutprofithud.core.History;
import dev.donutprofithud.core.Payment;
import dev.donutprofithud.core.PaymentParser;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Random;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * DEV ONLY. Creates a creative test world, fills the demo history with made-up days, feeds DonutSMP-style
 * payment confirmations through the client's normal system-chat pipeline, opens the editor and history
 * screens, and saves screenshots to run-demo/screenshots. Singleplayer is ignored by the real mod, so
 * this helper applies the same parse + ledger + history + flash steps itself. Uses only run-demo/config.
 */
public final class DemoScreenshots implements ClientModInitializer {
	private static final long SEED = Long.getLong("donutprofithud.demo.seed", 1337133713371337L);
	private static final String[] NAMES = {"Steve", "maple_trader", "CoinCollector42", "DiamondDan", "alex9", "Notch2"};

	private boolean worldRequested;
	private int t = -1;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(Minecraft mc) {
		if (!worldRequested) {
			if (mc.screen instanceof TitleScreen title && mc.level == null) {
				worldRequested = true;
				LevelSettings settings = new LevelSettings("Demo", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
					new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
				mc.createWorldOpenFlows().createFreshLevel("Demo", settings, new WorldOptions(SEED, true, false),
					WorldPresets::createNormalWorldDimensions, title);
			}
			return;
		}
		if (mc.player == null || mc.level == null) return;
		t++;
		try {
			step(mc);
		} catch (Exception e) {
			DonutProfitHudClient.LOGGER.error("demo step {} failed", t, e);
		}
	}

	private void step(Minecraft mc) throws Exception {
		DonutProfitHudClient mod = DonutProfitHudClient.get();
		HudConfig c = mod.config();
		switch (t) {
			case 1 -> {
				mod.resetSession();
				HudConfig d = new HudConfig();
				copyStyle(d, c);
				c.visible = true;
				seedHistory(mod);
				mc.player.connection.sendCommand("time set 6000");
				mc.player.connection.sendCommand("weather clear");
				mc.player.connection.sendCommand("tp @s ~ ~14 ~ 150 14");
			}
			case 5 -> {
				mc.player.getAbilities().flying = true;
				mc.player.onUpdateAbilities();
			}
			case 280 -> balance(mc, mod, "330,496,203");
			case 300 -> pay(mc, mod, incoming("Steve", "7.4M"));
			case 340 -> pay(mc, mod, incoming("maple_trader", "5M"));
			case 348 -> shot(mc, "1-incoming-payment");
			case 400 -> pay(mc, mod, outgoing("CoinCollector42", "6M", "120K"));
			case 408 -> shot(mc, "2-outgoing-payment");
			case 470 -> mc.setScreen(new SettingsScreen(null));
			case 500 -> shot(mc, "3-editor-banner-tab");
			case 510 -> {
				setStatic("tab", "DAILY");
				mc.setScreen(new SettingsScreen(null));
			}
			case 540 -> shot(mc, "4-editor-daily-tab");
			case 550 -> {
				setStatic("tab", "COLOR");
				setStatic("colorTarget", "BACKGROUND");
				mc.setScreen(new SettingsScreen(null));
			}
			case 580 -> shot(mc, "5-editor-color-tab");
			case 590 -> {
				setStatic("tab", "BANNER");
				setStatic("colorTarget", "POSITIVE");
				mc.setScreen(new HistoryScreen(null));
			}
			case 620 -> shot(mc, "6-history-screen");
			case 630 -> {
				mc.setScreen(null);
				c.extra = HudConfig.Extra.HISTORY;
			}
			case 660 -> shot(mc, "7-past-days");
			case 670 -> {
				c.extra = HudConfig.Extra.GRAPH;
				c.label = "Profit";
				c.font = HudFonts.Choice.BUNGEE;
				c.scale = 1.5f;
				c.xFraction = 0.17;
				c.y = 10;
				c.bgColor = 0x2B1055;
				c.bgOpacity = 75;
				c.borderWidth = 1;
				c.borderColor = 0xFFD700;
				c.positiveColor = 0x7CFFB2;
				c.graphWidth = 120;
				c.graphHeight = 36;
			}
			case 720 -> pay(mc, mod, incoming("CoinCollector42", "18M"));
			case 728 -> shot(mc, "8-custom-style");
			case 740 -> mc.setScreen(new FontGallery());
			case 770 -> shot(mc, "9-fonts");
			case 790 -> {
				mc.setScreen(null);
				// back to defaults so the dev config does not keep the demo style
				copyStyle(new HudConfig(), c);
				mod.saveConfig();
				mc.stop();
			}
			default -> { }
		}
	}

	private static void copyStyle(HudConfig from, HudConfig to) {
		to.label = from.label; to.scale = from.scale; to.xFraction = from.xFraction; to.y = from.y;
		to.bgColor = from.bgColor; to.bgOpacity = from.bgOpacity; to.borderWidth = from.borderWidth;
		to.borderColor = from.borderColor; to.positiveColor = from.positiveColor; to.font = from.font;
		to.extra = from.extra; to.graphWidth = from.graphWidth; to.graphHeight = from.graphHeight;
		to.shows = from.shows; to.showBalance = from.showBalance; to.bold = from.bold; to.italic = from.italic;
	}

	/** Made-up previous days plus earlier payments today, so the graph and history have something to show. */
	private static void seedHistory(DonutProfitHudClient mod) {
		History h = mod.history();
		for (DayRecord d : h.daysNewestFirst()) h.deleteDay(d.date);
		var zone = DonutProfitHudClient.zone();
		Random r = new Random(42);
		LocalDate today = History.dayOf(System.currentTimeMillis(), zone, 0);
		long bal = 250_000_000L;
		for (int back = 8; back >= 1; back--) {
			LocalDate day = today.minusDays(back);
			if (back == 4) continue; // a day off
			long start = History.dayStartMs(day, zone, 0);
			h.recordBalance(BigDecimal.valueOf(bal), start + hours(9.5), zone, 0);
			int n = 6 + r.nextInt(14);
			for (int i = 0; i < n; i++) {
				long at = start + hours(10 + 13.0 * i / n + r.nextDouble() * 0.5);
				long amount = (1 + r.nextInt(40)) * 250_000L;
				boolean in = r.nextDouble() < 0.62;
				String who = NAMES[r.nextInt(NAMES.length)];
				String line = in ? who + " paid you $ " + amount : "You paid " + who + " $ " + amount;
				Payment p = PaymentParser.parse(line).orElseThrow();
				h.applyPayment(p, true, at, zone, 0);
				bal += in ? amount : -amount;
			}
			h.recordBalance(BigDecimal.valueOf(bal), start + hours(23.4), zone, 0);
		}
		// Earlier today: a dip in the morning, then a climb.
		long start = History.dayStartMs(today, zone, 0);
		long now = System.currentTimeMillis();
		long from = start + (now - start) / 8, to = now - 60_000;
		String[] lines = {
			"You paid DiamondDan $ 4M", "You paid alex9 $ 2.5M", "maple_trader paid you $ 3M",
			"Steve paid you $ 6.2M", "You paid Notch2 $ 1.5M", "CoinCollector42 paid you $ 9M",
			"maple_trader paid you $ 2.25M", "Steve paid you $ 4.8M"};
		for (int i = 0; i < lines.length; i++) {
			long at = from + (to - from) * i / lines.length;
			h.applyPayment(PaymentParser.parse(lines[i]).orElseThrow(), true, at, zone, 0);
		}
		mod.saveHistory();
	}

	private static long hours(double h) {
		return (long) (h * 3_600_000L);
	}

	private static Component incoming(String name, String amount) {
		return Component.literal(name).withStyle(ChatFormatting.WHITE)
			.append(Component.literal(" paid you ").withStyle(ChatFormatting.WHITE))
			.append(Component.literal("$ ").withStyle(ChatFormatting.GREEN))
			.append(Component.literal(amount).withStyle(ChatFormatting.GREEN));
	}

	private static Component outgoing(String name, String amount, String tax) {
		return Component.literal("You paid ").withStyle(ChatFormatting.WHITE)
			.append(Component.literal(name).withStyle(ChatFormatting.WHITE))
			.append(Component.literal(" $ ").withStyle(ChatFormatting.GREEN))
			.append(Component.literal(amount).withStyle(ChatFormatting.GREEN))
			.append(Component.literal(" (+$ " + tax + " tax)").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	/** Shows the line in chat via the normal system-message path, then applies parse + ledger + history + flash. */
	private static void pay(Minecraft mc, DonutProfitHudClient mod, Component line) {
		mc.getChatListener().handleSystemMessage(line, false);
		Optional<Payment> p = PaymentParser.parse(line.getString());
		if (p.isEmpty()) throw new IllegalStateException("demo line did not parse: " + line.getString());
		long now = System.currentTimeMillis();
		BigDecimal delta = mod.ledger().apply(p.get(), mod.config().includeTax);
		mod.history().applyPayment(p.get(), mod.config().includeTax, now, DonutProfitHudClient.zone(), mod.config().dayStartHour);
		mod.flash().trigger(delta, now);
		mod.saveHistory();
	}

	/** The reply DonutSMP sends to a /bal the player typed. */
	private static void balance(Minecraft mc, DonutProfitHudClient mod, String amount) {
		Component line = Component.literal("You have ").withStyle(ChatFormatting.WHITE)
			.append(Component.literal("$ " + amount).withStyle(ChatFormatting.GREEN));
		mc.getChatListener().handleSystemMessage(line, false);
		BigDecimal v = PaymentParser.parseBalance(line.getString()).orElseThrow();
		mod.history().recordBalance(v, System.currentTimeMillis(), DonutProfitHudClient.zone(), mod.config().dayStartHour);
		mod.saveHistory();
	}

	private static void shot(Minecraft mc, String name) {
		Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), 1, msg -> { });
		DonutProfitHudClient.LOGGER.info("demo screenshot saved: {}", name);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void setStatic(String field, String enumName) throws Exception {
		Field f = SettingsScreen.class.getDeclaredField(field);
		f.setAccessible(true);
		f.set(null, Enum.valueOf((Class) f.getType(), enumName));
	}

	/** Every banner font, side by side, for the listing and for checking sizes. */
	private static final class FontGallery extends Screen {
		FontGallery() {
			super(Component.literal("Fonts"));
		}

		@Override
		public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
			g.fill(0, 0, width, height, 0xF0101018);
			g.drawCenteredString(font, "Banner fonts", width / 2, 10, 0xFFFFD700);
			HudFonts.Choice[] all = HudFonts.Choice.values();
			int rowH = Math.max(22, (height - 40) / all.length);
			for (int i = 0; i < all.length; i++) {
				HudFonts.Choice f = all[i];
				int y = 30 + i * rowH;
				g.drawString(font, f.title, 20, y + 4, 0xFFAAAAAA, true);
				g.pose().pushMatrix();
				g.pose().translate(110, y);
				g.pose().scale(1.5f, 1.5f);
				Component label = HudFonts.preview(f, "Today: ");
				Component value = HudFonts.preview(f, "+$12.4M");
				g.drawString(font, label, 0, 0, 0xFFFFFFFF, true);
				g.drawString(font, value, font.width(label), 0, 0xFF55FF55, true);
				g.drawString(font, HudFonts.preview(f, "   Bal: $345,496,203"), font.width(label) + font.width(value), 0, 0xFFDDDDDD, true);
				g.pose().popMatrix();
			}
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}
	}
}
