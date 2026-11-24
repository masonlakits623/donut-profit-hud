package dev.donutprofithud;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/** Fonts the banner can use: Minecraft's own two plus six bundled SIL OFL fonts (see /licenses/fonts). */
public final class HudFonts {
	public enum Choice {
		DEFAULT("Minecraft", null),
		UNICODE("Unicode", Identifier.withDefaultNamespace("uniform")),
		PIXEL("Pixel", Identifier.fromNamespaceAndPath(DonutProfitHudClient.MOD_ID, "pixel")),
		BUNGEE("Bungee", Identifier.fromNamespaceAndPath(DonutProfitHudClient.MOD_ID, "bungee")),
		LOBSTER("Lobster", Identifier.fromNamespaceAndPath(DonutProfitHudClient.MOD_ID, "lobster")),
		TERMINAL("Terminal", Identifier.fromNamespaceAndPath(DonutProfitHudClient.MOD_ID, "terminal")),
		COMIC("Comic", Identifier.fromNamespaceAndPath(DonutProfitHudClient.MOD_ID, "comic")),
		SILKSCREEN("Silkscreen", Identifier.fromNamespaceAndPath(DonutProfitHudClient.MOD_ID, "silkscreen"));

		public final String title;
		private final Identifier id;

		Choice(String title, Identifier id) {
			this.title = title;
			this.id = id;
		}

		public Choice next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	private HudFonts() {}

	/** Text style for the banner from the config: font, bold and italic. */
	public static Style style(HudConfig c) {
		Style s = Style.EMPTY;
		Choice f = c.font == null ? Choice.DEFAULT : c.font;
		if (f.id != null) s = s.withFont(new FontDescription.Resource(f.id));
		if (c.bold) s = s.withBold(true);
		if (c.italic) s = s.withItalic(true);
		return s;
	}

	public static MutableComponent text(HudConfig c, String text) {
		return Component.literal(text).withStyle(style(c));
	}

	/** A label written in the given font (used to preview fonts on the editor button). */
	public static MutableComponent preview(Choice f, String text) {
		Style s = f.id == null ? Style.EMPTY : Style.EMPTY.withFont(new FontDescription.Resource(f.id));
		return Component.literal(text).withStyle(s);
	}
}
