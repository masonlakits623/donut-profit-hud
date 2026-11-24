# Donut Profit HUD: store listing kit

Copy-paste text for CurseForge, Modrinth and GitHub. Images are in this folder (see "Images" at the bottom).

---

## Title

**Donut Profit HUD**

(Slug suggestion: `donut-profit-hud`)

## Short summary

Modrinth (max 256 chars):

> Track your DonutSMP profit live: today's net (received minus paid), a +$5M / -$6M flash per payment, a profit graph through the day, daily history and your /bal balance. Client-side, read-only, 8 fonts, fully customisable.

CurseForge (max ~50 chars):

> Track your daily DonutSMP profit on your HUD

GitHub repo description:

> Client-side Fabric mod for Minecraft 1.21.11 and 26.3 that shows your daily DonutSMP profit, a profit graph and history as a live HUD banner.

## Tags / metadata

| Field | Value |
| --- | --- |
| Loader | Fabric |
| Minecraft | 1.21.11 and 26.3 (one jar per version) |
| Dependencies | Fabric API (required) |
| Environment | Client: required · Server: unsupported (not needed) |
| Modrinth categories | Utility, Economy |
| CurseForge categories | Utility & QoL, Map and Information |
| License | CC0-1.0 (as set in `fabric.mod.json`); bundled fonts are SIL OFL 1.1 (see `licenses/fonts` in the jar) |
| GitHub topics | `minecraft`, `fabric`, `fabric-mod`, `donutsmp`, `hud`, `minecraft-mod` |

---

## Full description (Markdown: works on Modrinth, GitHub and CurseForge's Markdown editor)

```markdown
# Donut Profit HUD

**Know exactly how much you made on DonutSMP today, without doing the maths.**

Donut Profit HUD puts a small banner at the top of your screen with your **profit for today**:

> **Today = everything players paid you today − everything you paid players today**

It starts again at $0 every day, keeps every past day in a history, and draws a graph of your profit through the day.

![Banner with balance and today's graph](https://YOUR-IMAGE-LINK/1-incoming-payment.png)

## Features

- 💰 **Today's profit**: received minus paid, updated the moment a payment confirms. Resets every day
  (you pick the hour a new day starts). Prefer one running total? Switch to *Session* mode.
- 📈 **Profit graph** under the banner that fills in as the day goes on: green above $0, red below.
- 📅 **History** (press **H**): every day with its profit, a big graph of the day (hover to see your profit at any
  time), received, paid, number of payments and your balance. Or show your last few days right under the banner.
- 🏦 **Balance**: type `/bal` and the banner shows your balance on a second line. The history keeps your first and
  last balance of each day. (The mod never sends `/bal` for you.)
- ⚡ **Payment flash**: every payment pops up as `+$7.4M` (green) or `-$6.12M` (red).
- 🔤 **8 fonts**: Minecraft, Unicode, Pixel, Bungee, Lobster, Terminal, Comic and Silkscreen, plus bold and italic.
- 🎨 **Full in-game editor** (press **K**): drag to move, drag the gold corner to resize, label, colours (RGB sliders,
  hex, swatches), background opacity, border, text shadow, graph size, flash settings.
- 🌙 **"While you were away" pay counts too** (can be turned off).
- 🧾 **Tax option**: count the /pay tax as money paid (default) or ignore it.
- 🔄 **Reset** with a confirmation step. 💾 Everything is saved across restarts.

![History screen](https://YOUR-IMAGE-LINK/6-history-screen.png)

## How it works

DonutSMP already tells you about every payment in chat. The mod reads only those exact confirmation messages:

| Server message | What the mod does |
| --- | --- |
| `Steve paid you $ 7.4M` | +7,400,000 received |
| `You paid Steve $ 6M (+$ 120K tax)` | 6,120,000 paid (or 6,000,000 with *Count tax as paid* off) |
| `You were paid $ 4.8M while you were away` | +4,800,000 received |
| `You have $ 330,496,203` (reply to your `/bal`) | Saves your balance |

Everything else is ignored:

- ❌ failed payments ("That player does not have payments enabled…", "This user is not online")
- ❌ the `/pay` command you type
- ❌ other people's transfers, shop and auction messages
- ❌ **player chat**: someone typing "x paid you $ 5M" can't fake a payment, because the whole message has to match
  the server's exact format and player chat always carries the sender's name in front.

By default it only counts while you're connected to a server whose address contains `donutsmp`. You can turn that off
in the **Rules** tab.

## Safe and read-only

- Only **reads** messages the server already sends you.
- Never sends chat or commands, never clicks anything, **never makes payments**.
- No API key, no account, no server plugin, no internet connection of its own.
- 100% client-side.

## Controls

| Key | Action |
| --- | --- |
| **J** | Show / hide the banner |
| **K** | Open the editor |
| **H** | Open the history |

All three can be changed in *Options → Controls → Key Binds → Donut Profit HUD*.

## Editor

![Editor: Daily tab](https://YOUR-IMAGE-LINK/4-editor-daily-tab.png)

- **Toolbar**: *Preview + / Preview −* (test the flash, nothing is counted), *Center*, **Reset…**, *History*,
  *Panel: Right / Left / Off*, *HUD: On / Off*, *Done*.
- **Banner**: label, size, center / snap to top, font, bold, italic, border width, text shadow, flash on/off, flash time.
- **Daily**: today's profit or session total, balance line, what goes under the banner (graph, past days or nothing),
  graph width and height, how many past days, the hour a new day starts, open history.
- **Color**: pick what to colour, then use the RGB sliders, opacity, hex code or swatches.
- **Rules**: *Count tax as paid*, *DonutSMP only*, *Count away pay*, plus your session totals.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft **1.21.11** or **26.3**.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for the same version and put it in your `mods` folder.
3. Put the matching jar in the same `mods` folder: `donut-profit-hud-1.1.0+1.21.11.jar` or
   `donut-profit-hud-1.1.0+26.3.jar`.

## Files

- `config/donutprofithud.json`: your settings
- `config/donutprofithud-history.json`: your daily history and balance readings
- `config/donutprofithud-session.properties`: the session total (Session mode)

## FAQ

**Is this allowed on DonutSMP?**
It's a read-only HUD that doesn't automate anything, but every server sets its own rules. Check DonutSMP's
rules on client mods before using it.

**Why does it say $7.4M and not $7,412,345?**
DonutSMP itself prints payment amounts in short form ("$ 7.4M"), so that's the most precise number the client
receives. Your `/bal` balance is exact.

**Why doesn't my balance change match my profit?**
Profit counts player payments only. Your balance also changes from shops, auctions, orders and so on.

**Does it work on other servers?**
It's built for DonutSMP's message format. Other servers word payments differently, so they won't be counted.

---

*Not affiliated with or endorsed by DonutSMP or Mojang. Screenshots were staged in a singleplayer test world with
simulated payment messages.*
```

---

## Changelog for this upload (v1.1.0)

```markdown
- Daily profit: the banner shows today's profit and resets every day (choose when a new day starts)
- Profit graph through the day under the banner, or your last few days instead
- History screen (H): every day, a big graph with hover readout, totals and balance; delete days
- Balance from /bal on a second line (the mod never sends /bal)
- 8 fonts plus bold and italic
- New Daily tab and History button in the editor
- Now also for Minecraft 26.3
```

---

## Images

All 1920×1080. Upload them to the gallery and swap the `YOUR-IMAGE-LINK` placeholders above for the
gallery links (or GitHub's relative paths if you keep them in the repo).

| File | Use as | Caption |
| --- | --- | --- |
| `icon-512.png` | Project icon / logo (Modrinth, CurseForge) | n/a |
| `icon-128.png` | Small icon (also used inside the mod jar) | n/a |
| `1-incoming-payment.png` | **Featured / banner image** | Today's profit, your balance and the day's graph |
| `2-outgoing-payment.png` | Gallery | Paying someone: tax included, flashes red |
| `3-editor-banner-tab.png` | Gallery | Press K: drag to move, drag the gold corner to resize |
| `4-editor-daily-tab.png` | Gallery | Daily settings: graph, past days, balance, new day hour |
| `5-editor-color-tab.png` | Gallery | Colours: RGB sliders, opacity, hex and swatches |
| `6-history-screen.png` | Gallery | Press H: every day, with a graph you can hover |
| `7-past-days.png` | Gallery | Your last few days right under the banner |
| `8-custom-style.png` | Gallery | Make it yours: font, size, colours, border |
| `9-fonts.png` | Gallery | 8 fonts to choose from |
