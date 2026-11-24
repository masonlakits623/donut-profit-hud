package dev.donutprofithud;

import dev.donutprofithud.core.FlashTracker;
import dev.donutprofithud.core.Ledger;
import dev.donutprofithud.core.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Banner editor in the style of the Textboxes mod: the world stays visible, the banner is dragged to
 * move it and its gold corner handle is dragged to resize it; a side panel edits text, colours
 * (RGB sliders, opacity, hex, swatches) and counting rules. Reset Session asks for confirmation.
 */
public final class SettingsScreen extends Screen {
	private enum Tab {
		BANNER("Banner", 9), DAILY("Daily", 10), COLOR("Color", 8), RULES("Rules", 6);
		final String title;
		final int rows;
		Tab(String title, int rows) { this.title = title; this.rows = rows; }
	}

	private enum PanelMode { RIGHT, LEFT, HIDDEN }

	private enum ColorTarget {
		POSITIVE("Positive net", true), NEGATIVE("Negative net", true), ZERO("Zero net", true),
		LABEL("Label", true), BACKGROUND("Background", false), BORDER("Border", false);
		final String title;
		final boolean text;
		ColorTarget(String title, boolean text) { this.title = title; this.text = text; }
	}

	private enum Drag { NONE, MOVE, RESIZE }

	private record Label(int row, int x, String text, int color) {}

	private static final int PANEL_W = 170;
	private static final int ROW = 18;
	private static final int WH = 16;
	private static final int HANDLE = 6;
	private static final int[] SWATCHES = {
		0xFFFFFF, 0xC0C0C0, 0x808080, 0x404040, 0x000000, 0xFF5555, 0xAA0000, 0xFFAA00,
		0xFFFF55, 0x55FF55, 0x00AA00, 0x55FFFF, 0x00AAAA, 0x5555FF, 0x0000AA, 0xFF55FF
	};

	// Remembered between openings
	private static Tab tab = Tab.BANNER;
	private static PanelMode panelMode = PanelMode.RIGHT;
	private static ColorTarget colorTarget = ColorTarget.POSITIVE;
	private static int scroll = 0;

	private final Screen parent;
	private final FlashTracker previewFlash = new FlashTracker();

	private Drag drag = Drag.NONE;
	private double dragOffX, dragOffY;
	private float resizeLeft;

	private int tx, ty, toolbarBottom, toolbarTopUsed;
	private int panelX, panelY, panelBottom, contentTop, px, iw;
	private final List<Label> labels = new ArrayList<>();

	private Slider rSlider, gSlider, bSlider;
	private EditBox hexField;
	private boolean syncing;

	public SettingsScreen(Screen parent) {
		super(Component.literal("Donut Profit HUD Editor"));
		this.parent = parent;
	}

	private static HudConfig cfg() {
		return DonutProfitHudClient.get().config();
	}

	private static Ledger ledger() {
		return DonutProfitHudClient.get().ledger();
	}

	private static HudRenderer.View view() {
		return HudRenderer.liveView(DonutProfitHudClient.get());
	}

	private boolean panelVisible() {
		return panelMode != PanelMode.HIDDEN;
	}

	// ------------------------------------------------------------------ init

	@Override
	protected void init() {
		labels.clear();
		rSlider = gSlider = bSlider = null;
		hexField = null;

		buildToolbar();

		panelY = toolbarBottom + 4;
		panelBottom = height - 16;
		panelX = panelMode == PanelMode.LEFT ? 4 : width - PANEL_W - 4;
		px = panelX + 4;
		iw = PANEL_W - 8;
		contentTop = panelY + 4 + WH + 4;
		scroll = Math.max(0, Math.min(scroll, maxScroll()));

		if (panelVisible()) buildPanel();
	}

	private int maxScroll() {
		return Math.max(0, tab.rows * ROW - (panelBottom - contentTop));
	}

	private static final int[] TOOL_WIDTHS = {52, 52, 40, 50, 46, 62, 50, 36};

	/** Top of the toolbar: row 4 normally, but pushed below the banner (plus flash and size text) when the
	 *  banner sits where the toolbar would be, so the toolbar never covers it. */
	private int toolbarTop() {
		int rows = 1, x = 4, right = 4;
		for (int w : TOOL_WIDTHS) {
			if (x + w > width - 4 && x > 4) { rows++; x = 4; }
			x += w + 2;
			right = Math.max(right, x);
		}
		int normalBottom = 4 + (rows - 1) * ROW + WH;
		HudRenderer.Box b = box();
		boolean overlaps = b.top() < normalBottom + 2 && b.left() < right;
		if (!overlaps) return 4;
		float stack = HudRenderer.stackHeight(cfg(), view(), b) + (font.lineHeight + 6) * cfg().scale;
		int below = (int) Math.ceil(b.top() + stack) + 3 + font.lineHeight + 3;
		return Math.min(below, Math.max(4, height / 2));
	}

	private void buildToolbar() {
		tx = 4;
		ty = toolbarTopUsed = toolbarTop();
		HudConfig c = cfg();
		tool("Preview +", TOOL_WIDTHS[0], true, () -> previewFlash.trigger(new BigDecimal("3000000"), System.currentTimeMillis()));
		tool("Preview -", TOOL_WIDTHS[1], true, () -> previewFlash.trigger(new BigDecimal("-3000000"), System.currentTimeMillis()));
		tool("Center", TOOL_WIDTHS[2], true, () -> {
			c.xFraction = 0.5;
			c.y = 4;
			rebuildWidgets();
		});
		tool("Reset...", TOOL_WIDTHS[3], true, this::confirmReset);
		tool("History", TOOL_WIDTHS[4], true, () -> minecraft.setScreen(new HistoryScreen(this)));
		String pm = switch (panelMode) { case RIGHT -> "Right"; case LEFT -> "Left"; case HIDDEN -> "Off"; };
		tool("Panel: " + pm, TOOL_WIDTHS[5], true, () -> {
			panelMode = PanelMode.values()[(panelMode.ordinal() + 1) % PanelMode.values().length];
			rebuildWidgets();
		});
		tool("HUD: " + (c.visible ? "On" : "Off"), TOOL_WIDTHS[6], true, () -> {
			c.visible = !c.visible;
			rebuildWidgets();
		});
		tool("Done", TOOL_WIDTHS[7], true, this::onClose);
		toolbarBottom = ty + WH;
	}

	private void tool(String label, int w, boolean active, Runnable action) {
		if (tx + w > width - 4 && tx > 4) {
			tx = 4;
			ty += ROW;
		}
		Button btn = Button.builder(Component.literal(label), b -> action.run()).bounds(tx, ty, w, WH).build();
		btn.active = active;
		addRenderableWidget(btn);
		tx += w + 2;
	}

	// ------------------------------------------------------------------ panel helpers

	private int rowY(int row) {
		return contentTop + row * ROW - scroll;
	}

	private boolean rowVisible(int row) {
		int y = rowY(row);
		return y >= contentTop && y + WH <= panelBottom;
	}

	private <T extends AbstractWidget> T panel(T widget, int row) {
		widget.setY(rowY(row));
		widget.visible = rowVisible(row);
		return addRenderableWidget(widget);
	}

	private void label(int row, int x, String text, int color) {
		labels.add(new Label(row, x, text, color));
	}

	private Button button(int x, int w, String text, Runnable action) {
		return Button.builder(Component.literal(text), b -> action.run()).bounds(x, 0, w, WH).build();
	}

	private static Component onOff(String name, boolean on) {
		return Component.literal(name + ": ").append(Component.literal(on ? "On" : "Off")
			.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	private Button toggle(int x, int w, String name, BooleanSupplier get, Consumer<Boolean> set) {
		return Button.builder(onOff(name, get.getAsBoolean()), b -> {
			boolean v = !get.getAsBoolean();
			set.accept(v);
			b.setMessage(onOff(name, v));
		}).bounds(x, 0, w, WH).build();
	}

	// ------------------------------------------------------------------ panel

	private void buildPanel() {
		int tw = (iw - 6) / 4;
		Tab[] tabs = Tab.values();
		for (int i = 0; i < tabs.length; i++) {
			Tab t = tabs[i];
			Button btn = Button.builder(Component.literal(t.title), b -> {
				tab = t;
				scroll = 0;
				rebuildWidgets();
			}).bounds(px + i * (tw + 2), panelY + 4, tw, WH).build();
			btn.active = t != tab;
			addRenderableWidget(btn);
		}
		switch (tab) {
			case BANNER -> buildBannerTab();
			case DAILY -> buildDailyTab();
			case COLOR -> buildColorTab();
			case RULES -> buildRulesTab();
		}
	}

	private void buildBannerTab() {
		HudConfig c = cfg();
		label(0, px, "Label", 0xFFCCCCCC);
		EditBox labelBox = new EditBox(font, px + 32, 0, iw - 32, WH, Component.literal("Label"));
		labelBox.setMaxLength(32);
		labelBox.setValue(c.label);
		labelBox.setHint(Component.literal("(no label)").withStyle(ChatFormatting.DARK_GRAY));
		labelBox.setResponder(s -> c.label = s);
		panel(labelBox, 0);

		panel(new Slider(px, iw, "Size", 0.5, 4.0, 0.05, c.scale,
			v -> Math.round(v * 100) + "%", v -> c.scale = (float) v), 1);
		int half = (iw - 4) / 2;
		panel(button(px, half, "Center H", () -> {
			c.xFraction = 0.5;
			rebuildWidgets();
		}), 2);
		panel(button(px + half + 4, half, "Snap top", () -> {
			c.y = 4;
			rebuildWidgets();
		}), 2);
		HudFonts.Choice fc = c.font == null ? HudFonts.Choice.DEFAULT : c.font;
		panel(Button.builder(Component.literal("Font: ").append(HudFonts.preview(fc, fc.title)), b -> {
			c.font = fc.next();
			rebuildWidgets();
		}).bounds(px, 0, iw, WH).build(), 3);
		panel(toggle(px, half, "Bold", () -> c.bold, v -> c.bold = v), 4);
		panel(toggle(px + half + 4, half, "Italic", () -> c.italic, v -> c.italic = v), 4);
		panel(new Slider(px, iw, "Border width", 0, 4, 1, c.borderWidth,
			v -> Integer.toString((int) Math.round(v)), v -> c.borderWidth = (int) Math.round(v)), 5);
		panel(toggle(px, iw, "Text shadow", () -> c.textShadow, v -> c.textShadow = v), 6);
		panel(toggle(px, iw, "Flash payments", () -> c.flashEnabled, v -> c.flashEnabled = v), 7);
		panel(new Slider(px, iw, "Flash time", 0.5, 10, 0.5, c.flashMs / 1000.0,
			v -> String.format(Locale.ROOT, "%.1fs", v), v -> c.flashMs = (int) Math.round(v * 1000)), 8);
	}

	private void buildDailyTab() {
		HudConfig c = cfg();
		DonutProfitHudClient mod = DonutProfitHudClient.get();
		boolean today = c.shows != HudConfig.Shows.SESSION;
		panel(button(px, iw, "Shows: " + (today ? "Today's profit" : "Session total"), () -> {
			c.shows = today ? HudConfig.Shows.SESSION : HudConfig.Shows.TODAY;
			// Keep the default label in step with what is shown.
			if (today && "Today".equals(c.label)) c.label = "Session";
			else if (!today && "Session".equals(c.label)) c.label = "Today";
			rebuildWidgets();
		}), 0);
		panel(toggle(px, iw, "Balance line", () -> c.showBalance, v -> c.showBalance = v), 1);
		String under = switch (c.extra) { case NONE -> "Nothing"; case GRAPH -> "Today's graph"; case HISTORY -> "Past days"; };
		panel(button(px, iw, "Under banner: " + under, () -> {
			c.extra = HudConfig.Extra.values()[(c.extra.ordinal() + 1) % HudConfig.Extra.values().length];
			rebuildWidgets();
		}), 2);
		Slider gw = panel(new Slider(px, iw, "Graph width", 40, 300, 5, c.graphWidth,
			Slider::fmtInt, v -> c.graphWidth = (int) Math.round(v)), 3);
		Slider gh = panel(new Slider(px, iw, "Graph height", 12, 120, 2, c.graphHeight,
			Slider::fmtInt, v -> c.graphHeight = (int) Math.round(v)), 4);
		gw.active = gh.active = c.extra == HudConfig.Extra.GRAPH;
		Slider hd = panel(new Slider(px, iw, "Past days shown", 1, 10, 1, c.historyDays,
			Slider::fmtInt, v -> c.historyDays = (int) Math.round(v)), 5);
		hd.active = c.extra == HudConfig.Extra.HISTORY;
		panel(new Slider(px, iw, "New day at", 0, 23, 1, c.dayStartHour,
			v -> String.format(Locale.ROOT, "%02d:00", (int) Math.round(v)), v -> c.dayStartHour = (int) Math.round(v)), 6);
		panel(button(px, iw, "Open history (H)", () -> minecraft.setScreen(new HistoryScreen(this))), 7);
		var d = mod.today();
		label(8, px, "Today: " + Money.formatSigned(d.net()) + " (" + d.count + " payments)",
			0xFF000000 | HudRenderer.netColor(c, d.net()));
		BigDecimal bal = mod.history().lastBalance();
		label(9, px, bal == null ? "Balance: type /bal to record it" : "Last /bal: " + Money.format(bal),
			bal == null ? 0xFF909090 : 0xFFCCCCCC);
	}

	private void buildRulesTab() {
		HudConfig c = cfg();
		panel(toggle(px, iw, "Count tax as paid", () -> c.includeTax, v -> c.includeTax = v), 0);
		panel(toggle(px, iw, "DonutSMP only", () -> c.donutOnly, v -> c.donutOnly = v), 1);
		panel(toggle(px, iw, "Count away pay", () -> c.countAway, v -> c.countAway = v), 2);
		Ledger l = ledger();
		label(3, px, "Session received: " + Money.format(l.received()), 0xFF55FF55);
		label(4, px, "Session paid: " + Money.format(l.paid()), 0xFFFF5555);
		label(5, px, "Session payments: " + l.count(), 0xFFCCCCCC);
	}

	private void buildColorTab() {
		HudConfig c = cfg();
		panel(button(px, iw, "Editing: " + colorTarget.title, () -> {
			colorTarget = ColorTarget.values()[(colorTarget.ordinal() + 1) % ColorTarget.values().length];
			rebuildWidgets();
		}), 0);

		int rgb = getRgb();
		rSlider = panel(new Slider(px, iw, "Red", 0, 255, 1, (rgb >> 16) & 0xFF,
			Slider::fmtInt, v -> setChannel(16, (int) Math.round(v))), 1);
		gSlider = panel(new Slider(px, iw, "Green", 0, 255, 1, (rgb >> 8) & 0xFF,
			Slider::fmtInt, v -> setChannel(8, (int) Math.round(v))), 2);
		bSlider = panel(new Slider(px, iw, "Blue", 0, 255, 1, rgb & 0xFF,
			Slider::fmtInt, v -> setChannel(0, (int) Math.round(v))), 3);
		Slider opacity = panel(new Slider(px, iw, "Opacity", 0, 100, 1, colorTarget.text ? 100 : getOpacity(),
			v -> Math.round(v) + "%", v -> setOpacity((int) Math.round(v))), 4);
		opacity.active = !colorTarget.text;

		label(5, px, "Hex #", 0xFFCCCCCC);
		hexField = new EditBox(font, px + 30, 0, 52, WH, Component.literal("Hex"));
		hexField.setMaxLength(6);
		hexField.setValue(String.format("%06X", rgb));
		hexField.setFilter(s -> s.matches("[0-9a-fA-F]{0,6}"));
		hexField.setResponder(s -> {
			if (syncing || s.length() != 6) return;
			int v = Integer.parseInt(s, 16);
			setRgb(v);
			syncSliders(v);
		});
		panel(hexField, 5);
	}

	// ------------------------------------------------------------------ colour plumbing

	private int getRgb() {
		HudConfig c = cfg();
		return switch (colorTarget) {
			case POSITIVE -> c.positiveColor;
			case NEGATIVE -> c.negativeColor;
			case ZERO -> c.zeroColor;
			case LABEL -> c.labelColor;
			case BACKGROUND -> c.bgColor;
			case BORDER -> c.borderColor;
		} & 0xFFFFFF;
	}

	private void setRgb(int rgb) {
		HudConfig c = cfg();
		rgb &= 0xFFFFFF;
		switch (colorTarget) {
			case POSITIVE -> c.positiveColor = rgb;
			case NEGATIVE -> c.negativeColor = rgb;
			case ZERO -> c.zeroColor = rgb;
			case LABEL -> c.labelColor = rgb;
			case BACKGROUND -> c.bgColor = rgb;
			case BORDER -> c.borderColor = rgb;
		}
	}

	private int getOpacity() {
		return colorTarget == ColorTarget.BORDER ? cfg().borderOpacity : cfg().bgOpacity;
	}

	private void setOpacity(int pct) {
		pct = Math.max(0, Math.min(100, pct));
		if (colorTarget == ColorTarget.BACKGROUND) cfg().bgOpacity = pct;
		else if (colorTarget == ColorTarget.BORDER) cfg().borderOpacity = pct;
	}

	private void setChannel(int shift, int value) {
		int rgb = getRgb();
		rgb = (rgb & ~(0xFF << shift)) | ((value & 0xFF) << shift);
		setRgb(rgb);
		syncHex(rgb);
	}

	private void syncHex(int rgb) {
		if (hexField == null) return;
		syncing = true;
		hexField.setValue(String.format("%06X", rgb & 0xFFFFFF));
		syncing = false;
	}

	private void syncSliders(int rgb) {
		if (rSlider != null) rSlider.setReal((rgb >> 16) & 0xFF);
		if (gSlider != null) gSlider.setReal((rgb >> 8) & 0xFF);
		if (bSlider != null) bSlider.setReal(rgb & 0xFF);
	}

	private int swatchCell() {
		return iw / 8;
	}

	/** Swatches occupy rows 6 and 7 of the colour tab. */
	private int swatchAt(double mx, double my) {
		int cw = swatchCell();
		for (int i = 0; i < SWATCHES.length; i++) {
			int row = 6 + i / 8;
			if (!rowVisible(row)) continue;
			int x = px + (i % 8) * cw;
			int y = rowY(row) + 2;
			if (mx >= x && mx < x + cw && my >= y && my < y + 12) return i;
		}
		return -1;
	}

	// ------------------------------------------------------------------ actions

	private void confirmReset() {
		boolean session = cfg().shows == HudConfig.Shows.SESSION;
		minecraft.setScreen(new ConfirmScreen(yes -> {
			if (yes) DonutProfitHudClient.get().resetShown();
			minecraft.setScreen(this);
		},
			Component.literal(session ? "Reset session totals?" : "Reset today's profit?"),
			Component.literal(session
				? "This sets received, paid, net and the payment count back to 0. It cannot be undone."
				: "This clears today's payments and graph (your /bal readings stay). It cannot be undone."),
			Component.literal("Reset"),
			Component.literal("Cancel")));
	}

	// ------------------------------------------------------------------ input

	private HudRenderer.Box box() {
		return HudRenderer.layout(cfg(), view(), width);
	}

	private boolean inPanel(double mx, double my) {
		return panelVisible() && mx >= panelX && mx < panelX + PANEL_W && my >= panelY && my < panelBottom;
	}

	private boolean onHandle(HudRenderer.Box b, double mx, double my) {
		float r = b.left() + b.width();
		float bt = b.top() + b.height();
		return mx >= r - 3 && mx <= r + HANDLE && my >= bt - 3 && my <= bt + HANDLE;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) return true;
		double mx = click.x(), my = click.y();

		if (inPanel(mx, my)) {
			if (tab == Tab.COLOR) {
				int idx = swatchAt(mx, my);
				if (idx >= 0) {
					setRgb(SWATCHES[idx]);
					syncSliders(SWATCHES[idx]);
					syncHex(SWATCHES[idx]);
				}
			}
			return true;
		}

		setFocused(null);
		HudRenderer.Box b = box();
		if (click.button() == 0 && (b.contains(mx, my) || onHandle(b, mx, my))) {
			if (onHandle(b, mx, my)) {
				drag = Drag.RESIZE;
				resizeLeft = b.left();
			} else {
				drag = Drag.MOVE;
				dragOffX = mx - (b.left() + b.width() / 2f);
				dragOffY = my - b.top();
			}
			return true;
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
		if (drag != Drag.NONE) {
			HudConfig c = cfg();
			double mx = click.x(), my = click.y();
			HudRenderer.Box b = box();
			if (drag == Drag.MOVE) {
				double half = b.width() / 2.0;
				double cx = Math.max(half, Math.min(width - half, mx - dragOffX));
				c.xFraction = width > 0 ? cx / width : 0.5;
				c.y = (int) Math.round(Math.max(0, Math.min(height - b.height(), my - dragOffY)));
			} else {
				// Resize from the bottom-right corner, keeping the left edge where it was.
				double newW = Math.max(8, mx - resizeLeft);
				float s = (float) Math.max(0.5, Math.min(4.0, newW / b.unscaledW()));
				s = Math.round(s * 20f) / 20f;
				c.scale = s;
				double cx = resizeLeft + b.unscaledW() * s / 2.0;
				c.xFraction = width > 0 ? Math.max(0, Math.min(1, cx / width)) : 0.5;
			}
			return true;
		}
		return super.mouseDragged(click, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		if (drag != Drag.NONE) {
			drag = Drag.NONE;
			cfg().sanitize();
			DonutProfitHudClient.get().saveConfig();
			rebuildWidgets();
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (inPanel(mouseX, mouseY) && maxScroll() > 0) {
			int ns = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(verticalAmount) * ROW));
			if (ns != scroll) {
				scroll = ns;
				rebuildWidgets();
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		boolean typing = getFocused() instanceof EditBox eb && eb.isFocused()
			|| getFocused() instanceof AbstractSliderButton s && s.isFocused();
		if (!typing) {
			HudConfig c = cfg();
			int step = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
			boolean moved = true;
			switch (input.key()) {
				case GLFW.GLFW_KEY_LEFT -> c.xFraction = Math.max(0, c.xFraction - (double) step / Math.max(1, width));
				case GLFW.GLFW_KEY_RIGHT -> c.xFraction = Math.min(1, c.xFraction + (double) step / Math.max(1, width));
				case GLFW.GLFW_KEY_UP -> c.y = Math.max(0, c.y - step);
				case GLFW.GLFW_KEY_DOWN -> c.y = c.y + step;
				default -> moved = false;
			}
			if (moved) {
				if (toolbarTop() != toolbarTopUsed) rebuildWidgets();
				return true;
			}
		}
		return super.keyPressed(input);
	}

	// ------------------------------------------------------------------ render

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// Keep the world visible behind the editor (no blur / dim), like Textboxes.
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		HudConfig c = cfg();
		long now = System.currentTimeMillis();
		BigDecimal f = c.flashEnabled ? previewFlash.active(now, c.flashMs) : null;
		HudRenderer.View v = view();
		HudRenderer.render(g, c, v, f, previewFlash.alpha(now, c.flashMs));
		g.nextStratum();

		// Selection outline, resize handle and size readout
		HudRenderer.Box b = HudRenderer.layout(c, v, width);
		int bx = (int) Math.floor(b.left()), by = (int) Math.floor(b.top());
		int bw = (int) Math.ceil(b.width()), bh = (int) Math.ceil(b.height());
		boolean hover = drag == Drag.NONE && !inPanel(mouseX, mouseY) && b.contains(mouseX, mouseY);
		g.renderOutline(bx - 1, by - 1, bw + 2, bh + 2, hover || drag != Drag.NONE ? 0xFFFFFFFF : 0xFFFFD700);
		int hx = bx + bw, hy = by + bh;
		// Handle sits on the corner, outside the box, so it never covers the amount.
		g.fill(hx - 1, hy - 1, hx + HANDLE - 1, hy + HANDLE - 1, 0xFFFFD700);
		String info = "Size " + Math.round(c.scale * 100) + "%" + (c.visible ? "" : "  (hidden in HUD)");
		int infoY = by + Math.round(HudRenderer.stackHeight(c, v, b)) + 3
			+ (f != null ? Math.round((font.lineHeight + 6) * c.scale) : 0);
		g.drawString(font, info, bx, Math.min(infoY, height - 24), c.visible ? 0xFFFFD700 : 0xFFFF5555, true);

		if (panelVisible()) {
			g.fill(panelX, panelY, panelX + PANEL_W, panelBottom, 0xD0101018);
			g.renderOutline(panelX, panelY, PANEL_W, panelBottom - panelY, 0xFF505060);
		}

		super.render(g, mouseX, mouseY, delta);

		if (panelVisible()) {
			for (Label l : labels) {
				if (rowVisible(l.row())) {
					g.drawString(font, l.text(), l.x(), rowY(l.row()) + 4, l.color(), false);
				}
			}
			if (tab == Tab.COLOR) renderColorExtras(g);
			if (maxScroll() > 0) {
				int trackTop = contentTop, trackH = panelBottom - contentTop - 2;
				int thumbH = Math.max(10, trackH * trackH / (tab.rows * ROW));
				int thumbY = trackTop + (trackH - thumbH) * scroll / maxScroll();
				g.fill(panelX + PANEL_W - 3, trackTop, panelX + PANEL_W - 1, trackTop + trackH, 0x40FFFFFF);
				g.fill(panelX + PANEL_W - 3, thumbY, panelX + PANEL_W - 1, thumbY + thumbH, 0xC0FFFFFF);
			}
		}

		String hint = "Drag: move | Gold corner: resize | Arrows: nudge (Shift = 10) | Esc: done";
		g.drawString(font, font.plainSubstrByWidth(hint, width - 8), 4, height - 12, 0xFFAAAAAA, true);
	}

	private void renderColorExtras(GuiGraphics g) {
		int rgb = getRgb();
		if (rowVisible(5)) {
			int y = rowY(5);
			int x1 = px + 86, x2 = px + iw;
			g.fill(x1, y, x2, y + WH, 0xFF000000 | rgb);
			g.renderOutline(x1, y, x2 - x1, WH, 0xFFFFFFFF);
		}
		int cw = swatchCell();
		for (int i = 0; i < SWATCHES.length; i++) {
			int row = 6 + i / 8;
			if (!rowVisible(row)) continue;
			int x = px + (i % 8) * cw;
			int y = rowY(row) + 2;
			g.fill(x + 1, y, x + cw - 1, y + 12, 0xFF000000 | SWATCHES[i]);
			g.renderOutline(x + 1, y, cw - 2, 12, SWATCHES[i] == rgb ? 0xFFFFD700 : 0xFF303030);
		}
	}

	// ------------------------------------------------------------------ lifecycle

	@Override
	public void removed() {
		cfg().sanitize();
		DonutProfitHudClient.get().saveConfig();
		super.removed();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Slider over a real range [min, max] with step snapping (same as Textboxes' ValueSlider). */
	private static final class Slider extends AbstractSliderButton {
		private final String name;
		private final double min, max, step;
		private final DoubleFunction<String> fmt;
		private final DoubleConsumer setter;

		Slider(int x, int w, String name, double min, double max, double step, double current,
			   DoubleFunction<String> fmt, DoubleConsumer setter) {
			super(x, 0, w, WH, Component.empty(), 0.0);
			this.name = name;
			this.min = min;
			this.max = max;
			this.step = step;
			this.fmt = fmt;
			this.setter = setter;
			setReal(current);
		}

		static String fmtInt(double v) {
			return Integer.toString((int) Math.round(v));
		}

		double getReal() {
			double v = min + value * (max - min);
			if (step > 0) v = Math.round(v / step) * step;
			return Math.max(min, Math.min(max, v));
		}

		/** Moves the knob without firing the setter. */
		void setReal(double v) {
			value = max > min ? Math.max(0.0, Math.min(1.0, (v - min) / (max - min))) : 0.0;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			if (name == null) return;
			setMessage(Component.literal(name + ": " + fmt.apply(getReal())));
		}

		@Override
		protected void applyValue() {
			setter.accept(getReal());
		}
	}
}
