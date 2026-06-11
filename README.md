# Midgard

Eine **Fabric-Mod für Minecraft 1.21.11** für Hypixel **SkyBlock**. Midgard ist
als Dach für mehrere Features gedacht; aktuell enthält es ein **Event-HUD**, das
aktuelle und kommende Events auf dem Bildschirm anzeigt. Jede Anzeige lässt sich
einzeln ein- und ausschalten – per Tastenkürzel **oder direkt im „Mods"-Menü**.

> ✅ Reine Anzeige-/QoL-Mod – sie schreibt **nichts in den Chat** (sie liest ihn
> nur, um Events zu erkennen) und gibt keinen unfairen Vorteil. Damit ist sie
> nach Hypixels Regeln erlaubt (wie z. B. SkyHanni oder SkyblockAddons).

> ℹ️ **Datenquelle:** Der Jacob-Contest-Zeitplan (inkl. Crops) wird von der
> öffentlichen API `api.elitebot.dev` geladen – dieselbe Quelle, die auch
> SkyHanni nutzt. Das ist der einzige externe Web-Zugriff der Mod; es wird
> kein Hypixel-Key benötigt. Alles andere läuft rein lokal (Scoreboard/Chat).

## GUI & Schrift

- **Einstellungs-GUI im Karten-Stil**: links Kategorie-Leiste, rechts
  scrollbare Karten mit **Item-Icon**, Titel, Beschreibung und An/Aus-Schalter.
- **Jacob-Crops mit Symbolen**: z. B. Weizen-Contest → Weizen-Item-Icon
  (alle Crops inkl. Sunflower/Wild Rose werden unterstützt).
- **Schrift**: gebündelte glatte Roboto-TTF (dick für Titel, dünn für Texte).

## Was wird angezeigt?

- **Jacob's Farming Contest** (live erkannt, sobald angekündigt)
- **Kalender-Events** mit Countdown aus dem SkyBlock-Datum:
  Traveling Zoo, Spooky Festival, Season of Jerry, New Year Celebration
- **Live erkannte Events** aus dem Chat: Mining Fiesta, Fishing Festival,
  Dark Auction, Bürgermeister-Wahl

Laufende Events stehen oben (grün, ●), kommende darunter (gelb, ○), sortiert
nach verbleibender Zeit. Das HUD ist ein frei verschiebbares Overlay.

## Installieren

1. **Fabric Loader** für Minecraft **1.21.11** installieren
   → https://fabricmc.net/use/installer/
2. In den `mods`-Ordner (`%appdata%\.minecraft\mods`) legen:
   - **Fabric API** für 1.21.11 → https://modrinth.com/mod/fabric-api
   - **`midgard-1.0.0.jar`** (aus `build/libs/`)
   - *(empfohlen)* **ModMenu** → https://modrinth.com/mod/modmenu –
     damit erscheint Midgard im „Mods"-Menü und ist dort konfigurierbar
3. Minecraft mit dem Fabric-Profil starten.

> ModMenu ist optional: Ohne ModMenu öffnest du die Einstellungen per Taste,
> mit ModMenu zusätzlich bequem über die Mod-Liste.

## Bedienung

| Aktion | Standard-Taste |
| --- | --- |
| Einstellungen öffnen | **Rechte Umschalttaste** |
| HUD an/aus | (nicht belegt – in `Optionen → Steuerung → Midgard` setzbar) |

**Im „Mods"-Menü:** Midgard auswählen → „Configure" → dasselbe Einstellungsmenü.

Im Einstellungsmenü kannst du:
- das HUD komplett an-/ausschalten,
- **jede einzelne Event-Anzeige** an-/ausschalten,
- den Hintergrund an-/ausschalten,
- die Größe ändern,
- das HUD per **„HUD verschieben"** mit der Maus platzieren.

Die Einstellungen werden in `config/midgard.json` gespeichert.

## Selbst bauen

Voraussetzung: **JDK 21** (Minecraft 1.21.x verlangt Java 21).

```bash
# Windows
gradlew.bat build

# falls Gradle ein falsches JDK nimmt, JDK 21 explizit angeben:
gradlew.bat build "-Dorg.gradle.java.home=C:\Pfad\zu\jdk-21"
```

Fertige Mod danach unter `build/libs/midgard-1.0.0.jar`
(die `-sources.jar` wird nicht benötigt).

## Projektstruktur (für künftige Features)

```
com.midgard            → Einstiegspunkt (Midgard) + ModMenu-Anbindung
com.midgard.events     → Event-Feature (Erkennung, Kalender, HUD, GUI)
```

Neue Module einfach als weiteres Paket unter `com.midgard` ergänzen und in
`Midgard.onInitializeClient()` initialisieren.

## Termine / Events anpassen

- Kalendertermine: eine Zeile in
  [`EventType.java`](src/main/java/com/midgard/events/event/EventType.java),
  z. B. `occ(8, 29, 3)` = Monat 8, Tag 29, Dauer 3 SkyBlock-Tage.
- Neue live erkannte Events: eine Regel pro Chat-Stichwort in
  [`LiveEventTracker.java`](src/main/java/com/midgard/events/event/LiveEventTracker.java).
