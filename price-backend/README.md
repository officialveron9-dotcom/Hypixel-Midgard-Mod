# Midgard Preis-Backend

Holt automatisch (alle ~5 Min, kostenlos über GitHub Actions):
- **Bazaar**-Preise (Hypixel API),
- **Auktionshaus** Min/Max/Ø + Lowest BIN pro Item (Hypixel API),
- **Jacob-Zeitplan** (von elitebot.dev, 1× pro Lauf),

und veröffentlicht alles als eine kompakte Datei `prices.json` auf **GitHub Pages**.
Die Midgard-Mod lädt nur diese eine Datei vom CDN — skaliert auf beliebig viele Spieler, ohne dass jeder Hypixel selbst abfragt.

**Kein API-Key nötig** (Bazaar/Auctions sind öffentlich). **Kein Server, keine Kosten.**

---

## Einrichtung (einmalig, ~5 Min)

Du brauchst nur einen **kostenlosen GitHub-Account** (https://github.com/signup).

1. **Neues Repo anlegen:** github.com → oben rechts „+“ → *New repository*.
   - Name z. B. `midgard-prices`
   - **Public** wählen (wichtig: öffentliche Repos haben unbegrenzte Action-Minuten + Pages)
   - „Create repository“.

2. **Diese Dateien hochladen.** Im neuen Repo auf *„uploading an existing file“* klicken und den **Inhalt dieses `price-backend`-Ordners** hochladen (also `aggregate.mjs`, `package.json`, `.gitignore`, `README.md` und den Ordner `.github/`).
   - Falls du Git auf dem PC hast, geht auch:
     ```
     cd price-backend
     git init && git add . && git commit -m "init"
     git branch -M main
     git remote add origin https://github.com/DEINNAME/midgard-prices.git
     git push -u origin main
     ```

3. **Pages aktivieren:** Repo → *Settings* → *Pages* → unter „Build and deployment“ → *Source* = **Deploy from a branch** → Branch = **gh-pages** / `/ (root)` → *Save*.
   - Den `gh-pages`-Branch erstellt der erste Action-Lauf automatisch.

4. **Ersten Lauf starten:** Repo → *Actions* → ggf. Workflows aktivieren → *„Update Prices“* → *Run workflow*.
   - Danach läuft er automatisch alle ~5 Min.

5. **URL prüfen:** Nach dem ersten Lauf ist die Datei hier erreichbar:
   ```
   https://DEINNAME.github.io/midgard-prices/prices.json
   ```
   Diese URL trägst du später in der Mod ein (Einstellungen).

---

## Datei-Format (`prices.json`)

```json
{
  "updated": 1700000000,
  "bazaar":   { "ENCHANTED_DIAMOND": { "buy": 1234.5, "sell": 1100.0 } },
  "auctions": { "HYPERION": { "min": 900000000, "max": 1800000000, "avg": 1200000000, "lowestBin": 900000000, "count": 42 } },
  "jacob":    { "1700001200": ["Wheat", "Carrot", "Cactus"] }
}
```

## Wichtig: elitebot-Quellenangabe
Der Jacob-Zeitplan kommt von **elitebot.dev**. Deren Bedingungen verlangen für öffentliche
Nutzung **Erlaubnis + einen sichtbaren Link**. Hol dir kurz das OK (deren Discord) und wir
bauen einen klickbaren „Daten: elitebot.dev“-Hinweis in die Mod ein. Pro Lauf wird elitebot
nur **1×** abgefragt (nicht von jedem Spieler) — das ist fair und schont ihre Limits.
