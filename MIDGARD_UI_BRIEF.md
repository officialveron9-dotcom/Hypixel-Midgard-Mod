# Projekt-Brief: Eigenes Modern-UI-Render-Backend (Fabric Mod)

> **Zweck dieses Dokuments:** Kontext-Handoff für eine Coding-Instanz (Claude Code in VS Code).
> Es beschreibt Ziel, Constraints, Architektur und eine konkrete Umsetzungs-Reihenfolge.
> Bei Konflikten mit der MC-Version: **zuerst die Version klären** (siehe „Offene Punkte").

---

## 1. Ziel

Ein **eigenständiger Fabric-Mod** mit einer **modernen, client-artigen Oberfläche** (Look in Richtung Badlion/Lunar: runde Panels, glatte Schrift, weiche Animationen, optional Frosted-Glass-Blur). Die UI soll **nicht** nach dem pixeligen Vanilla-GUI aussehen.

Verwendungszwecke:
- Eigene Menü-Screens (Mod-Einstellungen etc.)
- **Ingame-HUD-Overlays** (z. B. Jacob-Contest-Timer, Mining-Progress – Hypixel-SkyBlock-Stil)

### Gewünschte Features (Soll-Funktionsumfang)

> Diese Liste ist der angestrebte Funktionsumfang. Claude Code soll sie als **Ziel** behandeln.
> **Wichtig:** Einige Funktionen existieren evtl. schon im bestehenden Code — diese **nicht doppelt bauen** (siehe „Bereits vorhanden / ignorieren").

**Allgemein**
- **Globaler Ein/Aus-Schalter** für die komplette Mod-UI.
- **Anzeige-Bereich:** Option „**nur auf Hypixel**" vs. „**überall**" (Server-Erkennung über Server-Adresse / Server-Brand).
- **HUD-Bearbeitungs-Modus (Button):** öffnet ein Edit-Overlay, in dem **alle Ingame-Elemente** frei **verschoben, skaliert und aneinander angedockt** werden können (Snapping Fenster-an-Fenster). Position & Größe werden **pro Element persistent** gespeichert.

**Events** (Hypixel-weit)
- Als **erweiterbare Event-Registry**: alle Hypixel-Events **einzeln auflistbar und aktivierbar** (Jacob-Contest, Dwarven-/Mining-Events und alle weiteren) — ein Eintrag pro Event-Typ.
- Pro Event: **mehrere Instanzen gleichzeitig** anzeigbar (z. B. Jacob: laufender Contest **+ die nächsten zwei**).
- Pro Event: **Map-/Kontext-Filter** — „nur auf relevanter Map" (z. B. Jacob nur im Garten, Dwarven nur in den Dwarven Mines) **oder** „überall" — pro Event einstellbar. Map-Erkennung über Scoreboard / Location-Parsing.

**Inventar (Pets & Equipment-Sidebar)**
- Eigenes **Seiten-Panel neben dem Vanilla-Inventar**, das ausgerüstetes **Pet** und **Equipment** (Necklace/Halskette, Belt/Gürtel, Gloves, Cloak, Bracelet) anzeigt.
- **Schnell-Buttons** zu den jeweiligen SkyBlock-Menüs (Pets, Equipment, Wardrobe, Accessory Bag …).
- *Umsetzbarkeit:* rein **anzeigend + Shortcuts** (Ansatz wie NotEnoughUpdates / SkyHanni). **Keine** echten funktionalen Equip-Slots — das Ausrüsten passiert serverseitig. Daten kommen aus der **Hypixel-API** bzw. dem **Parsen der SkyBlock-Menüs**.
- ⚠️ **Equip/Unequip NICHT automatisieren** (kein Auto-Click, keine Packet-Injection ins Server-Menü) — das gilt als **Macro/Automation** und ist auf Hypixel **bannbar**. Erlaubter Weg: Klick auf das Feld **öffnet das echte SkyBlock-Menü**, der finale Equip-Klick bleibt **manuell** beim Spieler.

**Menü / Config**
- Kategorien-Sidebar, Toggles, Slider, optional Profile/Presets.

**Design / Themes**
- Umschaltbares Theme (z. B. Frost-Teal / Wikinger-Gold), optional Akzentfarben-Picker & Schriftauswahl.

**Bereits vorhanden / ignorieren (nicht doppelt bauen)**
- *<hier eintragen, welche Funktionen im bestehenden Code schon existieren>*

## 2. Harte Constraints

1. **All-in-One, keine Zusatz-Mods.** Der Nutzer lädt **nur eine JAR**. Keine externen Library-Mods als separate Downloads.
2. **Keine fremden (LGPL-)Libs einbetten.** Kein OneConfig, kein Modern UI (BloCamLimb). Das Render-Backend wird **selbst** geschrieben.
3. **Erlaubter Unterbau = was Minecraft via LWJGL sowieso mitbringt** (permissive Lizenzen, kein Extra-Download, nichts zu verstecken):
   - **OpenGL** (GPU draw calls, über MC's `RenderSystem` / Core-Shader-System)
   - **STB / `STBTruetype`** (TTF-Rasterung) — Achtung: NanoVG ist **nicht** in MC enthalten, daher bewusst **nicht** verwenden.
   - **GLFW** (Maus-/Tastatur-Input)
4. Einzige akzeptable „externe" Abhängigkeit: **Fabric Loader + Fabric API** (Standard; entweder als Dependency deklarieren oder per Jar-in-Jar nesten).
5. Eigene Assets (TTF-Font, Icons) liegen in den Mod-Resources und werden mit ausgeliefert.

## 3. Architektur / Pipeline

Von oben (was der Mod-Code aufruft) nach unten (Unterbau):

```
[ Screen / HUD-Overlay ]      <- MC-Integration & Input
        |
[ Widget-Baum (retained) ]    <- Components, State, Event-Dispatch
        |
[ Layout-Pass ]               <- measure -> arrange (Flex/Stack/Grid)
        |
[ Animator ]                  <- Tweens, Easing, delta-time
        |
[ Render-Backend ]            <- SDF-Rounded-Rect-Shader, Blur-Pass, Glyph-Atlas
        |
--- ab hier kommt alles mit Minecraft (LWJGL) ---
[ OpenGL ]   [ STB ]   [ GLFW ]
```

**Wichtige Isolation:** Das eigene Backend rendert **nur**, was der Mod selbst zeichnet (eigene Screens + eigene HUD-Elemente). Es ersetzt **nicht** Minecrafts Vanilla-Font, Inventar, Chat, Tooltips o. Ä. — die bleiben unverändert. (Ein globaler Vanilla-Font-Ersatz wäre ein separater, invasiver Mixin-Eingriff in MC's Text-Engine und ist hier **nicht** gewünscht.)

---

## 4. Render-Backend (Kernstück)

### 4.1 SDF-Rounded-Rect-Shader
Erzeugt den modernen Look: scharfe runde Ecken, Rahmen, Gradienten — skalierungsunabhängig.

- Ein **Quad** (2 Triangles) über die Panel-Bounds zeichnen.
- Fragment-Shader berechnet die **Signed Distance** zur abgerundeten Box:
  ```glsl
  float sdRoundBox(vec2 p, vec2 b, float r) {
      vec2 q = abs(p) - b + r;
      return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
  }
  ```
- Kante per `smoothstep` über ~1 px antialiasen (z. B. `fwidth(d)` als AA-Breite).
- Uniforms: Rect-Größe (`bounds`), `cornerRadius`, `fillColor`, optional `borderWidth` + `borderColor`, optional Gradient (2 Farben, Mischung über `uv.y`).
- Border = zweiter SDF-Schwellenwert (`abs(d) < borderWidth`).
- Optionaler Drop-Shadow = separater, leicht vergrößerter, weichgezeichneter SDF-Pass hinter dem Panel.

### 4.2 Blur-Pass (Frosted Glass, optional)
- Aktuellen Framebuffer-Inhalt hinter dem Panel als Textur greifen.
- **Two-Pass Gaussian** (horizontal + vertikal) **oder Dual-Kawase** (günstiger).
- Ergebnis mit der Rounded-Rect-Maske clippen, dann halbtransparente Fill-Farbe drüberlegen.
- **Performance-Hinweis:** Blur ist teuer (Framebuffer-Capture). Für **dauerhaft sichtbare HUD-Overlays sparsam** einsetzen; für geöffnete Screens unkritisch.

### 4.3 Glyph-Atlas (glatte TTF-Schrift via STB)
- TTF-Bytes aus `assets/<modid>/font/...ttf` in einen `ByteBuffer` laden.
- `STBTTFontinfo` initialisieren.
- Atlas backen: `stbtt_PackBegin` / `stbtt_PackSetOversampling` / `stbtt_PackFontRange` (Single-Channel-Bitmap) → als GL-Textur (R8/Alpha) hochladen.
  - Alternativ **SDF-Glyphen** via `stbtt_GetGlyphSDF` für skalierungsunabhängige Schärfe (mehr Aufwand, schöner bei starker Skalierung).
- Textrendering: pro Zeichen `stbtt_GetPackedQuad` → texturiertes Quad zeichnen, Atlas als Coverage/Alpha sampeln, mit `textColor` tinten.
- Advance/Kerning und Line-Height berücksichtigen. Hilfsfunktionen: `drawText(x, y, str, size, color)`, `measureText(str, size) -> (w, h)`.
- Empfehlung: einen guten freien Font bündeln (z. B. **Inter** oder **Roboto**, beide permissiv) — Lizenz-Hinweis in `LICENSES/` mitliefern.

### 4.4 Render-State / Projektion
- GL-State sauber via `RenderSystem` setzen (Blend an: `SRC_ALPHA, ONE_MINUS_SRC_ALPHA`) und nach dem Zeichnen wiederherstellen.
- Eigene Ortho-Projektion in **echten Pixeln** verwenden ODER konsequent an MC's GUI-Matrix/`guiScale` ausrichten — **eine** Konvention wählen und überall einhalten.

---

## 5. Höhere Schichten

- **Render-Abstraktion (`UIRenderer`):** kapselt 4.1–4.3 als einfache API: `fillRoundedRect(...)`, `strokeRoundedRect(...)`, `blurPanel(...)`, `drawText(...)`, `measureText(...)`, `drawTexture(...)` (für Icons).
- **Widget-Baum (retained mode):** Basisklasse `Component` mit `measure()`, `layout()`, `draw(UIRenderer)`, `onMouse*/onKey*`. State + Dirty-Flag.
- **Layouts:** mind. `Row`/`Column` (Flex-artig) und `Stack`. Padding/Gap/Alignment.
- **Animator:** Tween-System mit Easing-Funktionen (easeOutCubic etc.), getrieben über delta-time pro Frame. Für Fades, Slide-ins, smoothe Counter.
- **Icons/Symbole:** eigene Icons als PNG-Sprites im Atlas **oder** als Icon-Font-Glyphen. Komplett eigenes Design, unabhängig von MC.

## 6. Integration mit Minecraft

- **Screens:** eigener `Screen`-Subtyp; in der Render-Methode den UI-Root layouten + zeichnen; Input an den Widget-Baum weiterreichen.
- **HUD-Overlays:** in den **HUD-Render-Hook** einklinken und pro Frame zeichnen.
  - ⚠️ **Versionsabhängig:** ältere Versionen nutzen `HudRenderCallback`; neuere 1.21.x haben einen Layer-/Element-basierten HUD-Mechanismus. → **An tatsächliche Version anpassen.**

## 7. HUD-Datenquellen (separat vom Rendering!)

Das **Zeichnen** ist mit dem Backend leicht. Die **Daten** (z. B. „nächster Jacob-Contest", Mining-Fortschritt) sind die eigentliche Arbeit und kommen client-seitig aus:
- **Scoreboard** (Sidebar parsen)
- **Tab-Liste** (Player-List-Header/Footer + Entries)
- **Action-Bar** und **Chat** (Pattern-Matching)

So machen es SkyHanni & Co. → es gibt keine reiche Client-API dafür. **Hypixels Regeln für erlaubte Mods beachten.**

## 8. Vorgeschlagene Projektstruktur

```
src/main/java/<group>/midgardui/
    MidgardUiMod.java            (Entrypoint)
    render/
        UIRenderer.java          (öffentliche Zeichen-API)
        RoundedRectShader.java   (SDF-Shader laden/binden, Uniforms)
        BlurShader.java          (Two-Pass / Dual-Kawase)
        GlyphAtlas.java          (STB: Font laden, Atlas backen, drawText)
        GlState.java             (Blend/Projektion setup/teardown)
    ui/
        Component.java
        layout/ (Row, Column, Stack)
        widgets/ (Panel, Label, Button, Icon, ProgressBar)
        Animator.java
        Easing.java
    screen/
        ExampleScreen.java
    hud/
        HudOverlayManager.java   (HUD-Hook, versionsabhängig)
        overlays/ (JacobContestOverlay, MiningProgressOverlay)
    data/
        ScoreboardReader.java, TabListReader.java, ChatListener.java

src/main/resources/
    fabric.mod.json
    assets/<modid>/font/<font>.ttf
    assets/<modid>/shaders/...     (Shader-Setup, versionsabhängiges Format)
    assets/<modid>/textures/ui/    (Icons als Sprites, optional)
LICENSES/                          (Font-Lizenz etc.)
```

## 9. Build / Bundling

- Reiner Fabric-Mod (Loom). Java-Version + Loom-Version passend zur MC-Version.
- **STB nicht bündeln** — kommt mit MC's LWJGL.
- **NanoVG nicht verwenden.**
- Fabric API: als Dependency deklarieren (Standard) oder per **Jar-in-Jar** nesten, falls „eine Datei" strikt gewünscht ist.
- Font-/Icon-Assets liegen in den Resources und werden automatisch mit verpackt.

## 10. Offene Punkte / Annahmen (zuerst klären)

1. **MC-Version + Fabric Loom/API-Versionen** → bestimmt HUD-Hook-API und das **Core-Shader-Format** (Shader-JSON + `.vsh`/`.fsh`, geladen über `ShaderProgram`/`RenderPipeline`). **Default-Annahme falls nichts angegeben: aktuelle 1.21.x.**
2. Java-Version (an MC koppeln).
3. SDF-Glyphen oder gebackener Bitmap-Atlas? (Default: Bitmap-Atlas mit Oversampling = einfacher, reicht meist.)
4. Welcher Font wird gebündelt? (Default-Vorschlag: Inter.)

## 11. Empfohlene Umsetzungs-Reihenfolge (für die Coding-Instanz)

1. **Bootstrap:** Leerer Fabric-Mod, lädt, Hello-World-Screen per Keybind öffnet sich.
2. **RoundedRectShader + UIRenderer.fillRoundedRect:** ein einziges rundes Panel auf einem Screen zeichnen (AA-Kanten prüfen).
3. **Border + Gradient** im selben Shader ergänzen.
4. **GlyphAtlas:** Font laden, backen, `drawText` — Text auf dem Panel.
5. **Widget-Baum + 1 Layout (Column) + Panel/Label/Button** — ein klickbarer Button.
6. **Animator** — Fade-in des Panels.
7. **HUD-Overlay-Manager** (versionsrichtiger Hook) + ein statisches Demo-Overlay.
8. **Blur-Pass** (optional, zuletzt) — Frosted-Glass-Panel.
9. **Datenleser** (Scoreboard/Tab/Chat) + erste echte Overlays (Jacob/Mining).

> **Akzeptanzkriterium für „Backend fertig" (Schritte 1–4):** Ein eigener Screen zeigt ein rundes Panel mit Rahmen, Gradient und glattem TTF-Text — komplett ohne fremde Library-Mods, nur mit MC's LWJGL.
