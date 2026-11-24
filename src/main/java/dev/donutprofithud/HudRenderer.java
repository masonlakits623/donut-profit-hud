package dev.donutprofithud;

import dev.donutprofithud.core.DayRecord;
import dev.donutprofithud.core.History;
import dev.donutprofithud.core.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Draws the banner ("Today: $12.4M", optionally "Bal: $345.5M" below), then either today's profit
 * graph or a short list of previous days, then the "+$3M" flash pill for a new payment.
 */
public final class HudRenderer {
	private static final int PAD_X = 5;
	private static final int PAD_Y = 3;
	private static final int LINE_GAP = 2;
	private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("EEE MMM d", Locale.ENGLISH);

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

	/** Everything the banner needs to draw; built once per frame. */
	public record View(BigDecimal net, BigDecimal balance, DayRecord today, List<DayRecord> previousDays,
					   long dayStartMs, long dayEndMs, long nowMs) {}

	static String labelText(HudConfig c) {
		return c.label == null || c.label.isBlank() ? "" : c.label.trim() + ": ";
	}

	/** Builds the view from the live mod state. */
	public static View liveView(DonutProfitHudClient mod) {
		HudConfig c = mod.config();
		long now = System.currentTimeMillis();
		LocalDate day = History.dayOf(now, DonutProfitHudClient.zone(), c.dayStartHour);
		List<DayRecord> prev = new ArrayList<>();
		if (c.extra == HudConfig.Extra.HISTORY) {
			String todayKey = day.toString();
			for (DayRecord d : mod.history().daysNewestFirst()) {
				if (d.date.equals(todayKey) || d.count == 0) continue;
				prev.add(d);
				if (prev.size() >= c.historyDays) break;
			}
		}
		return new View(mod.shownNet(), mod.history().lastBalance(), mod.today(), prev,
			History.dayStartMs(day, DonutProfitHudClient.zone(), c.dayStartHour),
			History.dayEndMs(day, DonutProfitHudClient.zone(), c.dayStartHour), now);
	}

	private static Component line1Label(HudConfig c) {
		return HudFonts.text(c, labelText(c));
	}

	private static Component line1Value(HudConfig c, BigDecimal net) {
		return HudFonts.text(c, Money.format(net));
	}

	private static Component line2Label(HudConfig c) {
		return HudFonts.text(c, "Bal: ");
	}

	private static Component line2Value(HudConfig c, BigDecimal balance) {
		return HudFonts.text(c, balance == null ? "type /bal" : Money.format(balance));
	}

	public static Box layout(HudConfig c, View v, int guiWidth) {
		Font font = Minecraft.getInstance().font;
		int bw = Math.max(0, c.borderWidth);
		int textW = font.width(line1Label(c)) + font.width(line1Value(c, v.net()));
		int textH = font.lineHeight - 1;
		if (c.showBalance) {
			textW = Math.max(textW, font.width(line2Label(c)) + font.width(line2Value(c, v.balance())));
			textH += LINE_GAP + font.lineHeight;
		}
		int w = textW + PAD_X * 2 + bw * 2;
		int h = textH + PAD_Y * 2 + bw * 2;
		float cx = (float) (c.xFraction * guiWidth);
		float sw = w * c.scale;
		float sh = h * c.scale;
		return new Box(w, h, cx - sw / 2f, c.y, sw, sh);
	}

	/** On-screen height of the banner plus the graph or history under it (not the flash), in GUI pixels. */
	public static float stackHeight(HudConfig c, View v, Box box) {
		Font font = Minecraft.getInstance().font;
		int below = box.unscaledH() + 2;
		if (c.extra == HudConfig.Extra.GRAPH) {
			below += c.graphHeight + 2;
		} else if (c.extra == HudConfig.Extra.HISTORY) {
			int rows = Math.max(1, v.previousDays().size());
			below += rows * (font.lineHeight + 1) + PAD_Y * 2 - 1 + 2;
		}
		return below * c.scale;
	}

	static void renderHud(GuiGraphics g) {
		DonutProfitHudClient mod = DonutProfitHudClient.get();
		if (mod == null || !mod.config().visible) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui) return;
		if (mc.screen instanceof SettingsScreen) return; // the editor draws its own live copy
		long now = System.currentTimeMillis();
		HudConfig c = mod.config();
		render(g, c, liveView(mod), c.flashEnabled ? mod.flash().active(now, c.flashMs) : null,
			mod.flash().alpha(now, c.flashMs));
	}

	static int argb(int percent, int rgb) {
		int a = Math.round(Math.max(0, Math.min(100, percent)) * 2.55f);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	static int netColor(HudConfig c, BigDecimal v) {
		return v.signum() > 0 ? c.positiveColor : v.signum() < 0 ? c.negativeColor : c.zeroColor;
	}

	/** Shared by the HUD and the editor. */
	static void render(GuiGraphics g, HudConfig c, View v, BigDecimal flashDelta, float flashAlpha) {
		Font font = Minecraft.getInstance().font;
		Box box = layout(c, v, g.guiWidth());
		int bw = Math.max(0, c.borderWidth);
		int boxW = box.unscaledW();
		int boxH = box.unscaledH();

		g.pose().pushMatrix();
		g.pose().translate(box.left() + box.width() / 2f, box.top());
		g.pose().scale(c.scale, c.scale);

		int left = -boxW / 2;
		panel(g, c, left, 0, boxW, boxH, bw);
		int tx = left + bw + PAD_X;
		int ty = bw + PAD_Y;
		Component label = line1Label(c);
		g.drawString(font, label, tx, ty, 0xFF000000 | c.labelColor, c.textShadow);
		g.drawString(font, line1Value(c, v.net()), tx + font.width(label), ty, 0xFF000000 | netColor(c, v.net()), c.textShadow);
		if (c.showBalance) {
			int y2 = ty + font.lineHeight + LINE_GAP;
			Component bl = line2Label(c);
			g.drawString(font, bl, tx, y2, 0xFF000000 | c.labelColor, c.textShadow);
			int valColor = v.balance() == null ? 0x909090 : c.zeroColor;
			g.drawString(font, line2Value(c, v.balance()), tx + font.width(bl), y2, 0xFF000000 | valColor, c.textShadow);
		}

		int below = boxH + 2;
		if (c.extra == HudConfig.Extra.GRAPH) {
			int gw = c.graphWidth, gh = c.graphHeight;
			int gb = Math.min(bw, 1);
			panel(g, c, -gw / 2, below, gw, gh, gb);
			int inset = gb + 2;
			drawDayGraph(g, v.today(), v.dayStartMs(), v.dayEndMs(), v.nowMs(),
				-gw / 2 + inset, below + inset, gw - inset * 2, gh - inset * 2,
				c.positiveColor, c.negativeColor);
			below += gh + 2;
		} else if (c.extra == HudConfig.Extra.HISTORY) {
			below += drawHistoryList(g, c, v.previousDays(), below, font) + 2;
		}

		if (flashDelta != null && flashAlpha > 0.02f) {
			Component f = HudFonts.text(c, Money.formatSigned(flashDelta));
			int fColor = flashDelta.signum() >= 0 ? c.positiveColor : c.negativeColor;
			int a = Math.max(5, Math.round(255 * flashAlpha));
			int fw = font.width(f) + PAD_X * 2;
			int fh = font.lineHeight + PAD_Y * 2 - 1;
			int fBgPct = Math.round(c.bgOpacity * flashAlpha);
			if (fBgPct > 0) g.fill(-fw / 2, below, -fw / 2 + fw, below + fh, argb(fBgPct, c.bgColor));
			g.drawString(font, f, -fw / 2 + PAD_X, below + PAD_Y, (a << 24) | fColor, c.textShadow);
		}
		g.pose().popMatrix();
	}

	private static void panel(GuiGraphics g, HudConfig c, int x, int y, int w, int h, int bw) {
		if (c.bgOpacity > 0) g.fill(x, y, x + w, y + h, argb(c.bgOpacity, c.bgColor));
		if (bw > 0 && c.borderOpacity > 0) {
			int bc = argb(c.borderOpacity, c.borderColor);
			g.fill(x, y, x + w, y + bw, bc);
			g.fill(x, y + h - bw, x + w, y + h, bc);
			g.fill(x, y + bw, x + bw, y + h - bw, bc);
			g.fill(x + w - bw, y + bw, x + w, y + h - bw, bc);
		}
	}

	/** "Sun Nov 22  +$12.4M" rows for previous days; returns the height used. */
	private static int drawHistoryList(GuiGraphics g, HudConfig c, List<DayRecord> days, int top, Font font) {
		int gb = Math.min(c.borderWidth, 1);
		if (days.isEmpty()) {
			Component none = HudFonts.text(c, "No earlier days yet");
			int w = font.width(none) + PAD_X * 2;
			int h = font.lineHeight + PAD_Y * 2 - 1;
			panel(g, c, -w / 2, top, w, h, gb);
			g.drawString(font, none, -w / 2 + PAD_X, top + PAD_Y, 0xFF909090, c.textShadow);
			return h;
		}
		List<Component> dates = new ArrayList<>();
		List<Component> vals = new ArrayList<>();
		int wDate = 0, wVal = 0;
		for (DayRecord d : days) {
			Component date = HudFonts.text(c, LocalDate.parse(d.date).format(DAY_FMT));
			Component val = HudFonts.text(c, Money.formatSigned(d.net()));
			dates.add(date);
			vals.add(val);
			wDate = Math.max(wDate, font.width(date));
			wVal = Math.max(wVal, font.width(val));
		}
		int w = wDate + 8 + wVal + PAD_X * 2;
		int rowH = font.lineHeight + 1;
		int h = days.size() * rowH + PAD_Y * 2 - 1;
		panel(g, c, -w / 2, top, w, h, gb);
		int y = top + PAD_Y;
		for (int i = 0; i < days.size(); i++) {
			g.drawString(font, dates.get(i), -w / 2 + PAD_X, y, 0xFF000000 | c.labelColor, c.textShadow);
			int vx = -w / 2 + w - PAD_X - font.width(vals.get(i));
			g.drawString(font, vals.get(i), vx, y, 0xFF000000 | netColor(c, days.get(i).net()), c.textShadow);
			y += rowH;
		}
		return h;
	}

	/**
	 * Area graph of a day's running net from day start to day end. Columns after {@code nowMs} stay empty,
	 * so today's graph grows through the day. Green above the zero line, red below.
	 */
	public static void drawDayGraph(GuiGraphics g, DayRecord day, long startMs, long endMs, long nowMs,
									int x, int y, int w, int h, int posRgb, int negRgb) {
		if (w <= 0 || h <= 0 || endMs <= startMs) return;
		double span = endMs - startMs;
		double[] vals = new double[w];
		int drawn = 0;
		List<DayRecord.Point> pts = day.points();
		int pi = 0;
		double cur = 0, max = 0, min = 0;
		for (int col = 0; col < w; col++) {
			long colStart = startMs + (long) (col / (double) w * span);
			if (colStart > nowMs) break;
			long t = Math.min(nowMs, startMs + (long) ((col + 1) / (double) w * span));
			while (pi < pts.size() && pts.get(pi).t <= t) {
				cur = pts.get(pi).net.doubleValue();
				max = Math.max(max, cur);
				min = Math.min(min, cur);
				pi++;
			}
			vals[col] = cur;
			drawn = col + 1;
		}
		if (max == min) max = min + 1;
		double range = max - min;
		int zeroY = Math.max(y, Math.min(y + h - 1, y + (int) Math.round(max / range * (h - 1))));
		int posFill = 0x70000000 | (posRgb & 0xFFFFFF), negFill = 0x70000000 | (negRgb & 0xFFFFFF);
		int posLine = 0xFF000000 | posRgb, negLine = 0xFF000000 | negRgb;
		for (int col = 0; col < drawn; col++) {
			double val = vals[col];
			int vy = Math.max(y, Math.min(y + h - 1, y + (int) Math.round((max - val) / range * (h - 1))));
			int cx = x + col;
			if (val > 0) {
				g.fill(cx, vy, cx + 1, zeroY, posFill);
				g.fill(cx, vy, cx + 1, vy + 1, posLine);
			} else if (val < 0) {
				g.fill(cx, zeroY + 1, cx + 1, vy + 1, negFill);
				g.fill(cx, vy, cx + 1, vy + 1, negLine);
			}
		}
		g.fill(x, zeroY, x + w, zeroY + 1, 0x60FFFFFF);
		if (drawn > 0 && drawn < w) {
			g.fill(x + drawn - 1, y, x + drawn, y + h, 0x30FFFFFF); // "now"
		}
	}
}
