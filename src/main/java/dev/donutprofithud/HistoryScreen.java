package dev.donutprofithud;

import dev.donutprofithud.core.DayRecord;
import dev.donutprofithud.core.History;
import dev.donutprofithud.core.Money;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Daily history (H): a list of days on the left; on the right the selected day's profit graph through
 * the day (hover for the net at a time) and its totals and /bal readings.
 */
public final class HistoryScreen extends Screen {
	private static final DateTimeFormatter LIST_FMT = DateTimeFormatter.ofPattern("EEE MMM d", Locale.ENGLISH);
	private static final DateTimeFormatter TITLE_FMT = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
	private static final int ROW = 12;
	private static final int LIST_W = 130;

	private static String selected;

	private final Screen parent;
	private List<DayRecord> days = new ArrayList<>();
	private int listScroll;
	private int listX, listY, listBottom;
	private int gx, gy, gw, gh, headX;

	public HistoryScreen(Screen parent) {
		super(Component.literal("Profit history"));
		this.parent = parent;
	}

	private static DonutProfitHudClient mod() {
		return DonutProfitHudClient.get();
	}

	@Override
	protected void init() {
		DonutProfitHudClient m = mod();
		String todayKey = m.todayKey();
		days = new ArrayList<>(m.history().daysNewestFirst());
		if (days.isEmpty() || !days.get(0).date.equals(todayKey)) days.add(0, m.today());
		if (selected == null || days.stream().noneMatch(d -> d.date.equals(selected))) selected = todayKey;

		listX = 8;
		listY = 26;
		listBottom = height - 32;
		listScroll = Math.max(0, Math.min(listScroll, maxListScroll()));

		headX = listX + LIST_W + 12;
		gx = headX + font.width("+$999.99M") + 6;
		gy = listY + 14;
		gw = Math.max(60, width - 10 - gx);
		gh = Math.max(40, listBottom - gy - 12 - 4 * 11 - 6);

		int bw = 90, gap = 4;
		int total = bw * 3 + gap * 2;
		int bx = width / 2 - total / 2, by = height - 24;
		DayRecord sel = selectedDay();
		String delText = sel.date.equals(todayKey) ? "Clear today..." : "Delete day...";
		Button del = Button.builder(Component.literal(delText), b -> confirmDelete()).bounds(bx, by, bw, 20).build();
		del.active = sel.count > 0 || sel.endBalance != null;
		addRenderableWidget(del);
		addRenderableWidget(Button.builder(Component.literal("Banner editor"),
			b -> minecraft.setScreen(new SettingsScreen(this))).bounds(bx + bw + gap, by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
			.bounds(bx + (bw + gap) * 2, by, bw, 20).build());
	}

	private DayRecord selectedDay() {
		for (DayRecord d : days) if (d.date.equals(selected)) return d;
		return days.get(0);
	}

	private int maxListScroll() {
		return Math.max(0, days.size() * ROW - (listBottom - listY));
	}

	private void confirmDelete() {
		DayRecord d = selectedDay();
		String when = LocalDate.parse(d.date).format(TITLE_FMT);
		minecraft.setScreen(new ConfirmScreen(yes -> {
			if (yes) mod().deleteDay(d.date);
			minecraft.setScreen(this);
		},
			Component.literal("Delete the history for " + when + "?"),
			Component.literal("Its payments, graph and /bal readings are removed. It cannot be undone."),
			Component.literal("Delete"),
			Component.literal("Cancel")));
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) return true;
		double mx = click.x(), my = click.y();
		if (click.button() == 0 && mx >= listX && mx < listX + LIST_W && my >= listY && my < listBottom) {
			int i = (int) ((my - listY + listScroll) / ROW);
			if (i >= 0 && i < days.size()) {
				selected = days.get(i).date;
				rebuildWidgets();
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX < listX + LIST_W + 4) {
			listScroll = Math.max(0, Math.min(maxListScroll(), listScroll - (int) Math.signum(verticalAmount) * ROW * 2));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	// ------------------------------------------------------------------ render

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		HudConfig c = mod().config();
		g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);

		renderList(g, c, mouseX, mouseY);
		renderDay(g, c, mouseX, mouseY);
	}

	private void renderList(GuiGraphics g, HudConfig c, int mouseX, int mouseY) {
		g.fill(listX - 2, listY - 2, listX + LIST_W + 2, listBottom + 2, 0x90000000);
		g.renderOutline(listX - 2, listY - 2, LIST_W + 4, listBottom - listY + 4, 0xFF505060);
		g.enableScissor(listX, listY, listX + LIST_W, listBottom);
		String todayKey = mod().todayKey();
		for (int i = 0; i < days.size(); i++) {
			int y = listY + i * ROW - listScroll;
			if (y + ROW < listY || y > listBottom) continue;
			DayRecord d = days.get(i);
			boolean sel = d.date.equals(selected);
			boolean hover = mouseX >= listX && mouseX < listX + LIST_W && mouseY >= y && mouseY < y + ROW
				&& mouseY >= listY && mouseY < listBottom;
			if (sel) g.fill(listX, y, listX + LIST_W, y + ROW, 0x60FFD700);
			else if (hover) g.fill(listX, y, listX + LIST_W, y + ROW, 0x30FFFFFF);
			String name = d.date.equals(todayKey) ? "Today" : LocalDate.parse(d.date).format(LIST_FMT);
			g.drawString(font, name, listX + 3, y + 2, sel ? 0xFFFFD700 : 0xFFDDDDDD, true);
			String v = d.count == 0 ? "-" : Money.formatSigned(d.net());
			int vc = d.count == 0 ? 0xFF808080 : 0xFF000000 | HudRenderer.netColor(c, d.net());
			g.drawString(font, v, listX + LIST_W - 3 - font.width(v), y + 2, vc, true);
		}
		g.disableScissor();
		if (maxListScroll() > 0) {
			int trackH = listBottom - listY;
			int thumbH = Math.max(10, trackH * trackH / (days.size() * ROW));
			int thumbY = listY + (trackH - thumbH) * listScroll / maxListScroll();
			g.fill(listX + LIST_W, thumbY, listX + LIST_W + 2, thumbY + thumbH, 0xC0FFFFFF);
		}
	}

	private void renderDay(GuiGraphics g, HudConfig c, int mouseX, int mouseY) {
		DonutProfitHudClient m = mod();
		DayRecord d = selectedDay();
		LocalDate date = LocalDate.parse(d.date);
		boolean isToday = d.date.equals(m.todayKey());
		long start = History.dayStartMs(date, DonutProfitHudClient.zone(), c.dayStartHour);
		long end = History.dayEndMs(date, DonutProfitHudClient.zone(), c.dayStartHour);
		long now = isToday ? System.currentTimeMillis() : end;

		g.drawString(font, date.format(TITLE_FMT) + (isToday ? "  (today)" : ""), headX, listY, 0xFFFFFFFF, true);

		// Graph panel
		g.fill(gx - 1, gy - 1, gx + gw + 1, gy + gh + 1, 0x90000000);
		g.renderOutline(gx - 2, gy - 2, gw + 4, gh + 4, 0xFF505060);
		HudRenderer.drawDayGraph(g, d, start, end, now, gx, gy, gw, gh, c.positiveColor, c.negativeColor);

		double max = 0, min = 0;
		for (DayRecord.Point p : d.points()) {
			if (p.t > now) break;
			max = Math.max(max, p.net.doubleValue());
			min = Math.min(min, p.net.doubleValue());
		}
		if (d.points().isEmpty()) {
			String none = isToday ? "No payments yet today" : "No payments this day";
			g.drawCenteredString(font, none, gx + gw / 2, gy + gh / 2 - 4, 0xFF909090);
		} else {
			String top = Money.formatSigned(BigDecimal.valueOf(max));
			String bottom = Money.formatSigned(BigDecimal.valueOf(min));
			g.drawString(font, top, gx - 4 - font.width(top), gy, 0xFFAAAAAA, true);
			g.drawString(font, bottom, gx - 4 - font.width(bottom), gy + gh - 8, 0xFFAAAAAA, true);
			if (max > 0 && min < 0) {
				int zeroY = gy + (int) Math.round(max / (max - min) * (gh - 1));
				if (zeroY > gy + 10 && zeroY < gy + gh - 18) g.drawString(font, "$0", gx - 4 - font.width("$0"), zeroY - 4, 0xFF808080, true);
			}
		}

		// Time axis
		for (int k = 0; k <= 4; k++) {
			String t = String.format(Locale.ROOT, "%02d:00", (c.dayStartHour + 6 * k) % 24);
			int x = gx + k * gw / 4 - font.width(t) / 2;
			x = Math.max(gx - 2, Math.min(gx + gw - font.width(t) + 2, x));
			g.fill(gx + k * gw / 4, gy + gh + 1, gx + k * gw / 4 + 1, gy + gh + 3, 0xFF808080);
			g.drawString(font, t, x, gy + gh + 4, 0xFF909090, true);
		}

		// Hover readout
		if (mouseX >= gx && mouseX < gx + gw && mouseY >= gy && mouseY < gy + gh) {
			long t = start + (long) ((mouseX - gx + 0.5) / gw * (end - start));
			if (t <= now) {
				g.fill(mouseX, gy, mouseX + 1, gy + gh, 0x80FFFFFF);
				BigDecimal v = d.netAt(t);
				String read = Instant.ofEpochMilli(t).atZone(DonutProfitHudClient.zone()).format(TIME_FMT) + "  " + Money.formatSigned(v);
				int rx = Math.min(gx + gw - font.width(read) - 2, mouseX + 4);
				g.fill(rx - 2, gy + 1, rx + font.width(read) + 2, gy + 12, 0xC0000000);
				g.drawString(font, read, rx, gy + 3, 0xFF000000 | HudRenderer.netColor(c, v), true);
			}
		}

		// Totals
		int sy = gy + gh + 16;
		int col2 = headX + Math.max(150, (width - 10 - headX) / 2);
		g.drawString(font, "Received: " + exact(d.received()), headX, sy, 0xFF55FF55, true);
		g.drawString(font, "Paid: " + exact(d.paid()), col2, sy, 0xFFFF5555, true);
		String net = (d.net().signum() > 0 ? "+" : "") + exact(d.net());
		g.drawString(font, "Net: " + net, headX, sy + 11, 0xFF000000 | HudRenderer.netColor(c, d.net()), true);
		g.drawString(font, "Payments: " + d.count, col2, sy + 11, 0xFFCCCCCC, true);
		if (d.endBalance == null) {
			g.drawString(font, isToday ? "Balance: type /bal to record it" : "Balance: not checked this day", headX, sy + 22, 0xFF909090, true);
		} else {
			String bal = "Balance: " + exact(d.endBalance) + " at "
				+ Instant.ofEpochMilli(d.endBalanceAt).atZone(DonutProfitHudClient.zone()).format(TIME_FMT);
			g.drawString(font, bal, headX, sy + 22, 0xFFFFFFFF, true);
			BigDecimal ch = d.balanceChange();
			if (ch != null) {
				String s = "Since " + Instant.ofEpochMilli(d.startBalanceAt).atZone(DonutProfitHudClient.zone()).format(TIME_FMT)
					+ ": " + (ch.signum() > 0 ? "+" : "") + exact(ch) + " (all sources)";
				g.drawString(font, s, headX, sy + 33, 0xFF000000 | HudRenderer.netColor(c, ch), true);
			}
		}
	}

	static String exact(BigDecimal v) {
		DecimalFormat f = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
		String s = f.format(v.abs());
		return (v.signum() < 0 ? "-$" : "$") + s;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
