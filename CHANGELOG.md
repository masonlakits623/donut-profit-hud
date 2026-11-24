# Changelog

## 1.1.0

Now available for Minecraft **1.21.11** and **26.3** (two separate jars).

### Added
- Daily profit: the banner now shows today's profit and starts again at $0 every day (pick the hour a new day starts;
  switch back to the old all-time session total under *Daily → Shows*)
- Profit graph under the banner that fills in through the day (size adjustable)
- Or show your past days under the banner instead (1-10 days)
- History screen (H): every day with its net, a big graph of the day (hover to see your profit at any time),
  received / paid / payment count and your balance; days can be deleted
- Balance: type `/bal` and the banner shows your balance on a second line; the history keeps the first and last
  balance of each day. The mod never sends `/bal` itself
- 8 fonts for the banner: Minecraft, Unicode, Pixel, Bungee, Lobster, Terminal, Comic, Silkscreen, plus bold and italic
- New *Daily* tab in the editor and a *History* button in its toolbar
- History is saved in `config/donutprofithud-history.json` (about a year of days is kept)

### Changed
- Reset now resets what the banner shows (today's profit, or the session total in session mode), with matching
  confirmation text
- Wider editor panel to fit the new options

## 1.0.0

First release for Minecraft 1.21.11 (Fabric).

### Added
- Live net-profit banner: money received minus money paid, from DonutSMP payment messages
- +/- flash for every payment (green when you get paid, red when you pay)
- "While you were away" pay counts toward your total (can be turned off)
- Tax option: count the /pay tax as money paid, or ignore it
- In-game editor (K): move, resize, label, colours, opacity, border, text shadow, flash on/off and flash time
- Reset Session button with a confirmation step
- Totals are saved across disconnects and restarts
- Keybinds: J = show/hide banner, K = open editor (both can be changed)

### Safety
- Read-only: never sends chat or commands, never clicks anything and never makes payments
- Ignores failed payments, typed /pay commands and player chat
