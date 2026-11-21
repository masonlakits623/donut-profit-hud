# Donut Profit HUD: store listing kit

Copy-paste text for CurseForge, Modrinth and GitHub. Images are in this folder (see "Images" at the bottom).

---

## Title

**Donut Profit HUD**

(Slug suggestion: `donut-profit-hud`)

## Short summary

Modrinth (max 256 chars):

> A tiny on-screen banner that tracks your DonutSMP profit: money received minus money paid, with a +$5M / -$6M flash for every payment. Client-side, read-only, fully customisable.

CurseForge (max ~50 chars):

> Track your DonutSMP profit live on your HUD

GitHub repo description:

> Client-side Fabric mod for Minecraft 1.21.11 that shows your DonutSMP net profit (received − paid) as a live HUD banner.

## Tags / metadata

| Field | Value |
| --- | --- |
| Loader | Fabric |
| Minecraft | 1.21.11 |
| Dependencies | Fabric API (required) |
| Environment | Client: required · Server: unsupported (not needed) |
| Modrinth categories | Utility, Economy |
| CurseForge categories | Utility & QoL, Map and Information |
| License | CC0-1.0 (as set in `fabric.mod.json`; change it before uploading if you want another one) |
| GitHub topics | `minecraft`, `fabric`, `fabric-mod`, `donutsmp`, `hud`, `minecraft-mod` |

---

## Full description (Markdown: works on Modrinth, GitHub and CurseForge's Markdown editor)

```markdown
# Donut Profit HUD

**Know exactly how much you're up (or down) on DonutSMP, without doing the maths.**

Donut Profit HUD puts a small banner at the top of your screen that shows your running **net profit**:

> **Net = everything players paid you − everything you paid players**

Every time a payment goes through, the banner flashes the amount: **+$5M** in green when you get paid,
**-$6.12M** in red when you pay someone. Big numbers are shortened to K / M / B so the banner stays small,
but the mod keeps the exact amounts behind the scenes.

![Banner after two incoming payments](https://YOUR-IMAGE-LINK/1-incoming-payment.png)

## Features

- 💰 **Live net profit**: received minus paid, updated the moment a payment confirms.
- ⚡ **Payment flash**: every payment pops up as `+$7.4M` (green) or `-$6.12M` (red) under the banner.
- 🌙 **"While you were away" pay counts too**: the summary DonutSMP shows when you join is added to your total.
- 🎨 **Full in-game editor** (press **K**):
  - drag the banner anywhere, drag the gold corner to resize it
  - change the label ("Net", "Profit", whatever you like)
  - colours for positive / negative / zero net, label, background and border, using RGB sliders, a hex field or 16 swatches
  - background opacity, border width, text shadow
  - flash on/off and flash duration
- 🙈 **Show / hide** the banner with **J**.
- 🔄 **Reset Session** button (with a confirmation step) to start counting from zero.
- 💾 **Remembers your totals** across disconnects and restarts until you reset.
- 🧾 **Tax option**: count the /pay tax as money paid (default) or ignore it.

## How it works

DonutSMP already tells you about every payment in chat. The mod reads only those exact confirmation messages:

| Server message | What the mod does |
| --- | --- |
| `Steve paid you $ 7.4M` | +7,400,000 received |
| `You paid Steve $ 6M (+$ 120K tax)` | 6,120,000 paid (or 6,000,000 with *Count tax as paid* off) |
| `You were paid $ 4.8M while you were away` | +4,800,000 received |

Everything else is ignored:

- ❌ failed payments ("That player does not have payments enabled…", "This user is not online")
- ❌ the `/pay` command you type
- ❌ other people's transfers, shop and auction messages
- ❌ **player chat**: someone typing "x paid you $ 5M" in chat can't fake a payment, because the whole message has to match the server's exact format and player chat always carries the sender's name in front.

By default it only counts while you're connected to a server whose address contains `donutsmp`
(donutsmp.net, og-donutsmp.net …). You can turn that off in the **Rules** tab.

## Safe and read-only

- Only **reads** messages the server already sends you.
- Never sends chat or commands, never clicks anything, **never makes payments**.
- No API key, no account, no server plugin, no internet connection of its own.
- 100% client-side: the server doesn't need it, and you don't need it on singleplayer.

## Controls

| Key | Action |
| --- | --- |
| **J** | Show / hide the banner |
| **K** | Open the editor |

Both keys can be changed in *Options → Controls → Key Binds → Donut Profit HUD*.

## Editor

![Editor: Banner tab](https://YOUR-IMAGE-LINK/3-editor-banner-tab.png)

- **Toolbar**: *Preview + / Preview −* (test the flash, nothing is counted), *Center*, **Reset Session…**,
  *Panel: Right / Left / Off*, *HUD: On / Off*, *Done*.
- **Banner tab**: label, size, center / snap to top, border width, text shadow, flash on/off, flash time.
- **Color tab**: pick what to colour, then use the RGB sliders, opacity, hex code or swatches.
- **Rules tab**: *Count tax as paid*, *DonutSMP only*, *Count away pay*, plus your received / paid / payment count.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft **1.21.11**.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.11 and put it in your `mods` folder.
3. Put `donut-profit-hud-1.0.0.jar` in the same `mods` folder and start the game.

## Files

- `config/donutprofithud.json`: your settings
- `config/donutprofithud-session.properties`: your current totals

## FAQ

**Is this allowed on DonutSMP?**
It's a read-only HUD that doesn't automate anything, but every server sets its own rules. Check DonutSMP's
rules on client mods before using it.

**Why does it say $7.4M and not $7,412,345?**
DonutSMP itself prints amounts in short form ("$ 7.4M"), so that's the most precise number the client ever receives.

**Does it work on other servers?**
It's built for DonutSMP's message format. Other servers word payments differently, so they won't be counted.

---

*Not affiliated with or endorsed by DonutSMP or Mojang. Screenshots were staged in a singleplayer test world with
simulated payment messages.*
```

---

## Changelog for the first upload (v1.0.0)

```markdown
First release.

- Live net-profit banner (received − paid) for DonutSMP payments
- +/- flash for every payment
- Counts the "while you were away" summary (toggle)
- In-game editor: move, resize, label, colours, opacity, border, shadow, flash settings
- Reset Session with confirmation; totals saved across restarts
- J = show/hide, K = editor
```

---

## Images

All 1920×1080 unless noted. Upload them to the gallery and swap the `YOUR-IMAGE-LINK` placeholders above for the
gallery links (or GitHub's relative paths if you keep them in the repo).

| File | Use as | Caption |
| --- | --- | --- |
| `icon-512.png` | Project icon / logo (Modrinth, CurseForge) | n/a |
| `icon-128.png` | Small icon (also used inside the mod jar) | n/a |
| `1-incoming-payment.png` | **Featured / banner image** | Getting paid: the banner shows your net and flashes +$5M |
| `2-outgoing-payment.png` | Gallery | Paying someone: tax included, flashes -$6.12M |
| `3-editor-banner-tab.png` | Gallery | Press K: drag to move, drag the gold corner to resize |
| `4-editor-color-tab.png` | Gallery | Colours: RGB sliders, opacity, hex and swatches |
| `5-reset-confirmation.png` | Gallery | Reset Session asks before clearing your totals |
| `6-custom-style.png` | Gallery | Make it yours: label, size, colours, border |
