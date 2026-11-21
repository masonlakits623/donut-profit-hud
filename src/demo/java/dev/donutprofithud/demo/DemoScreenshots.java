package dev.donutprofithud.demo;

import dev.donutprofithud.DonutProfitHudClient;
import dev.donutprofithud.HudConfig;
import dev.donutprofithud.SettingsScreen;
import dev.donutprofithud.core.Payment;
import dev.donutprofithud.core.PaymentParser;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Optional;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
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
 * DEV ONLY. Creates a creative test world, feeds DonutSMP-style payment confirmations through the
 * client's normal system-chat pipeline, opens the editor, and saves screenshots to run-demo/screenshots.
 * Singleplayer is ignored by the real mod, so this helper applies the same parse + ledger + flash
 * steps itself.
 */
public final class DemoScreenshots implements ClientModInitializer {
	private static final long SEED = Long.getLong("donutprofithud.demo.seed", 1337133713371337L);

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
				c.visible = true;
				mc.player.connection.sendCommand("time set 6000");
				mc.player.connection.sendCommand("weather clear");
				mc.player.connection.sendCommand("tp @s ~ ~14 ~ 150 14");
			}
			case 5 -> {
				mc.player.getAbilities().flying = true;
				mc.player.onUpdateAbilities();
			}
			case 300 -> pay(mc, mod, incoming("Steve", "7.4M"));
			case 340 -> pay(mc, mod, incoming("maple_trader", "5M"));
			case 348 -> shot(mc, "1-incoming-payment");
			case 400 -> pay(mc, mod, outgoing("CoinCollector42", "6M", "120K"));
			case 408 -> shot(mc, "2-outgoing-payment");
			case 470 -> mc.setScreen(new SettingsScreen(null));
			case 500 -> shot(mc, "3-editor-banner-tab");
			case 510 -> {
				setStatic("tab", "COLOR");
				setStatic("colorTarget", "BACKGROUND");
				mc.setScreen(new SettingsScreen(null));
			}
			case 540 -> shot(mc, "4-editor-color-tab");
			case 550 -> {
				Screen s = new SettingsScreen(null);
				mc.setScreen(s);
				Method m = SettingsScreen.class.getDeclaredMethod("confirmReset");
				m.setAccessible(true);
				m.invoke(s);
			}
			case 580 -> shot(mc, "5-reset-confirmation");
			case 590 -> {
				mc.setScreen(null);
				setStatic("tab", "BANNER");
				setStatic("colorTarget", "POSITIVE");
				c.label = "Profit";
				c.scale = 1.5f;
				c.xFraction = 0.17;
				c.y = 10;
				c.bgColor = 0x2B1055;
				c.bgOpacity = 75;
				c.borderWidth = 1;
				c.borderColor = 0xFFD700;
				c.positiveColor = 0x7CFFB2;
			}
			case 640 -> pay(mc, mod, incoming("CoinCollector42", "18M"));
			case 648 -> shot(mc, "6-custom-style");
			case 700 -> {
				// back to defaults so the dev config does not keep the demo style
				HudConfig d = new HudConfig();
				c.label = d.label; c.scale = d.scale; c.xFraction = d.xFraction; c.y = d.y;
				c.bgColor = d.bgColor; c.bgOpacity = d.bgOpacity; c.borderWidth = d.borderWidth;
				c.borderColor = d.borderColor; c.positiveColor = d.positiveColor;
				mod.saveConfig();
				mc.stop();
			}
			default -> { }
		}
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

	/** Shows the line in chat via the normal system-message path, then applies parse + ledger + flash. */
	private static void pay(Minecraft mc, DonutProfitHudClient mod, Component line) {
		mc.getChatListener().handleSystemMessage(line, false);
		Optional<Payment> p = PaymentParser.parse(line.getString());
		if (p.isEmpty()) throw new IllegalStateException("demo line did not parse: " + line.getString());
		BigDecimal delta = mod.ledger().apply(p.get(), mod.config().includeTax);
		mod.flash().trigger(delta, System.currentTimeMillis());
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
}
