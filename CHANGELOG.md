# Changelog

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
