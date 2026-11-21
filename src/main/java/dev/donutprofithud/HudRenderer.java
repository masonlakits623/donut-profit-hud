package dev.donutprofithud;

import dev.donutprofithud.core.Money;
import java.math.BigDecimal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Draws the banner: "Label: $net" with an optional "+$3M" flash pill just below it. */
public final class HudRenderer {
	private static final int PAD_X = 5;
	private static final int PAD_Y = 3;

	private HudRenderer() {}

	/**
	 * Geometry of the main banner box. {@code unscaledW/H} are in banner pixels; {@code left/top/width/height}
	 * are on-screen GUI pixels after scaling (used by the editor for dragging and resizing).
	 */
	public record Box(int unscaledW, int unscaledH, float left, float top, float width, float height) {
		public boolean contains(double x, double y) {
			return x >= left && x < left + width && y >= top && y < top + height;
		}
	}

	static String labelText(HudConfig c) {
		return c.label == null || c.label.isBlank() ? "" : c.label.trim() + ": ";
	}

	public static Box layout(HudConfig c, BigDecimal net, int guiWidth) {
		Font font = Minecraft.getInstance().font;
		int bw = Math.max(0, c.borderWidth);
		int w = font.width(labelText(c)) + font.width(Money.format(net)) + PAD_X * 2 + bw * 2;
		int h = font.lineHeight + PAD_Y * 2 - 1 + bw * 2;
		float cx = (float) (c.xFraction * guiWidth);
		float sw = w * c.scale;
		float sh = h * c.scale;
		return new Box(w, h, cx - sw / 2f, c.y, sw, sh);
	}

	static void renderHud(GuiGraphics g) {
		DonutProfitHudClient mod = DonutProfitHudClient.get();
		if (mod == null || !mod.config().visible) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui) return;
		if (mc.screen instanceof SettingsScreen) return; // the editor draws its own live copy
		long now = System.currentTimeMillis();
		render(g, mod.config(), mod.ledger().net(),
			mod.config().flashEnabled ? mod.flash().active(now, mod.config().flashMs) : null,
			mod.flash().alpha(now, mod.config().flashMs));
	}

	private static int argb(int percent, int rgb) {
		int a = Math.round(Math.max(0, Math.min(100, percent)) * 2.55f);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	/** Shared by the HUD and the editor. */
	static void render(GuiGraphics g, HudConfig c, BigDecimal net, BigDecimal flashDelta, float flashAlpha) {
		Font font = Minecraft.getInstance().font;
		String label = labelText(c);
		String value = Money.format(net);
		int valueColor = net.signum() > 0 ? c.positiveColor : net.signum() < 0 ? c.negativeColor : c.zeroColor;
		Box box = layout(c, net, g.guiWidth());
		int bw = Math.max(0, c.borderWidth);
		int boxW = box.unscaledW();
		int boxH = box.unscaledH();

		g.pose().pushMatrix();
		g.pose().translate(box.left() + box.width() / 2f, box.top());
		g.pose().scale(c.scale, c.scale);

		int left = -boxW / 2;
		int right = left + boxW;
		if (c.bgOpacity > 0) g.fill(left, 0, right, boxH, argb(c.bgOpacity, c.bgColor));
		if (bw > 0 && c.borderOpacity > 0) {
			int bc = argb(c.borderOpacity, c.borderColor);
			g.fill(left, 0, right, bw, bc);
			g.fill(left, boxH - bw, right, boxH, bc);
			g.fill(left, bw, left + bw, boxH - bw, bc);
			g.fill(right - bw, bw, right, boxH - bw, bc);
		}
		int tx = left + bw + PAD_X;
		int ty = bw + PAD_Y;
		int labelW = font.width(label);
		if (!label.isEmpty()) g.drawString(font, label, tx, ty, 0xFF000000 | c.labelColor, c.textShadow);
		g.drawString(font, value, tx + labelW, ty, 0xFF000000 | valueColor, c.textShadow);

		if (flashDelta != null && flashAlpha > 0.02f) {
			String f = Money.formatSigned(flashDelta);
			int fColor = flashDelta.signum() >= 0 ? c.positiveColor : c.negativeColor;
			int a = Math.max(5, Math.round(255 * flashAlpha));
			int fw = font.width(f) + PAD_X * 2;
			int fh = font.lineHeight + PAD_Y * 2 - 1;
			int fy = boxH + 2;
			int fBgPct = Math.round(c.bgOpacity * flashAlpha);
			if (fBgPct > 0) g.fill(-fw / 2, fy, -fw / 2 + fw, fy + fh, argb(fBgPct, c.bgColor));
			g.drawString(font, f, -fw / 2 + PAD_X, fy + PAD_Y, (a << 24) | fColor, c.textShadow);
		}
		g.pose().popMatrix();
	}
}
