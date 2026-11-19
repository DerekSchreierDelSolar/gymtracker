# GymTracker – Projektnotizen für Claude

Private Android-App (kein Play Store), Installation per USB-Debugging.
Entwickler ist erfahren in JS/PL/SQL, aber neu in Kotlin/Compose → **auf Deutsch antworten,
Android-/Kotlin-Konzepte kurz erklären** (gern mit JS/SQL-Vergleich).

## Technik
- Kotlin + Jetpack Compose, Material 3, `minSdk 24`, Package `com.example.gymtracker`
- **Keine zusätzlichen Dependencies** (nur Template). Daher:
  - Icons selbst gezeichnet (`Icons.kt`, `AppIcon`/`Ico`), keine material-icons-Bibliothek
  - Navigation über State in `GymApp` (kein Navigation-Compose), Zurück via `BackHandler`
  - Speicherung als JSON (`org.json`) in `filesDir/gym_data.json`, kein Room
  - `java.util.Calendar` statt `java.time` (minSdk 24)
- Übersetzung DE/EN über `tr("de", "en")` (I18n.kt), **keine strings.xml**

## Bauen & Testen (Windows, Git-Bash)
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew installDebug --console=plain 2>&1 | grep -E "^e:|^w:|BUILD"
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
"$ADB" shell am start -n com.example.gymtracker/.MainActivity
"$ADB" exec-out screencap -p > <scratchpad>/x.png        # Screenshot ansehen
"$ADB" shell run-as com.example.gymtracker cat files/gym_data.json   # Daten prüfen
```
- **Release-APK:** `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`,
  zum Weitergeben nach `release/GymTracker-<version>.apk` kopieren. Vorher `versionCode` (+1) und
  `versionName` in `app/build.gradle.kts` erhöhen, sonst lässt sich das Update nicht installieren.
  Signiert mit `signing/gymtracker-release.jks` + `signing/keystore.properties` (gitignored, NICHT
  verlieren; fehlen sie, wird der Release-Build unsigniert). Release nicht über eine Debug-Installation
  installierbar (anderer Schlüssel) → im Emulator vorher Daten sichern (`run-as … cat`), deinstallieren.
- R8: **kein `packageScope`** in `optimization {}` – verursachte Absturz beim Start (IllegalAccessError).
  Nach Änderungen an der Release-Konfiguration immer die Release-APK im Emulator starten und testen.
- Emulator: 1080×2400; Screenshot-Koordinaten ×1,2 = Tap-Koordinaten
- Bei `adb push`/`shell` mit Pfaden `MSYS_NO_PATHCONV=1` setzen (Git-Bash wandelt `/data/...` sonst um)
- Python ist nicht installiert → für JSON-Manipulation `sed` nutzen
- Emulator läuft in UTC (Uhrzeiten weichen ab)

## Dateien (`app/src/main/java/com/example/gymtracker/`)
| Datei | Inhalt |
|---|---|
| `MainActivity.kt` | Activity, Theme/Systemleisten, **`GymApp` = Navigation** (welcher Screen oben liegt) |
| `GymStore.kt` | Gesamter Zustand + Laden/Speichern (atomar via .tmp), Import/Export, alle Mutationen |
| `AutoBackup.kt` | Automatisches Backup: Ordner per SAF (`OpenDocumentTree`, Berechtigung persistent), beim Verlassen der App (`onStop`) höchstens 1×/Tag `gymtracker-auto-JJJJ-MM-TT.json`, die 7 neuesten bleiben; Einstellungen in SharedPreferences (gerätegebunden, nicht im Export); `AutoBackupSettings` im Einstellungen-Dialog |
| `Model.kt` | `GymSet`, `MuscleGroup`, `Equipment`, `Workout`, `TrainingSession`, `CustomMeta`, `TEMPLATES`, `fmtSet`, `oneRepMax`, `isTimed` |
| `Catalog.kt` | `CATALOG` (177 Übungen: DE-Name = Schlüssel, Grundübung, Gruppe, Gerät, EN-Name), `RENAMED_EXERCISES`, MET-Werte, `WeightEntry` |
| `I18n.kt` | `appLanguage`, `tr()`, `appLocale()` |
| `Format.kt` | `fmtW`, `fmtDate`, `fmtDateTime`, `fmtDuration`, `plural` |
| `Stats.kt` | `ExerciseResult`, `SessionStats`, `sessionStats()`, `estimateKcal()`, `allSessions()` |
| `Filter.kt` | `ExerciseFilter` (Kategorie/Gerät/Suche), `FilterUiState`, `FilterActions`, `FilterBar` |
| `HomeScreen.kt` | Startseite: TopBar, ⋮-Menü (Export/Import/Einstellungen), NavigationBar, **HorizontalPager mit 5 Tabs**, `SettingsDialog`, `ExerciseGrid`, `TileGrid`, `ExerciseTile` |
| `DashboardTab.kt` | Tab 0 „Start“: Wochenziel, Serie, Schnellstart, Muskel-Heatmap, Diagramme (Zeitraum-Umschalter), Rekorde |
| `WorkoutScreens.kt` | Tab 2 Liste, `NewWorkoutDialog` (+Vorlagen), `WorkoutScreen` (Drag & Drop), `WorkoutPickerScreen` (Ziel-Sätze) |
| `TrainingScreen.kt` | Trainingsmodus (Pager pro Übung, Name + Eingabe + heutige Sätze + Verlauf früherer Tage per `HistoryDayCard`), Kopf mit `TrainingRings` (links Timer: Trainingsdauer bzw. Pausen-Countdown, antippen = Pause überspringen; rechts Ziel-Sätze), `StatRing` (Farbverlauf), `RecordLine` je Übung, Vibration |
| `SummaryScreen.kt` | Übersicht nach dem Training, `ComparisonLine`, `StatBox` |
| `CalendarTab.kt` | Tab 3: Monatskalender mit Abschluss-Ringen, `dayKey()`, `CompletionRing` |
| `BodyTab.kt` | Tab 4: Körpergewicht, Wochen-kcal, `startOfWeek()` |
| `ExerciseDetail.kt` | `SetInput` (inkl. Stoppuhr), `SetRow`/`EditSetDialog`, `ExerciseDetailScreen` mit Reitern Training (`RecordLine`, Verlauf neueste zuerst, Satz antippen = bearbeiten)/Fortschritt/Info (`MuscleSideCard` Vorder-/Rückseite, Figur mittig groß, Muskelliste nur in der letzten Karte + Wiki), `glassCardColors()`, `ExerciseProgress` (Rekord, Zuwachs, Kennzahl + Tage/Wochen/Monate) |
| `CatalogScreen.kt` | Katalog + `CustomExerciseDialog` |
| `Progression.kt` | 💡 Gewichts-Vorschlag (doppelte Progression) |
| `Wiki.kt` | Übungs-Wiki: lädt `assets/wiki/*.json` (`Wiki.load`), `wikiFor(name)`, `WikiButton` (ⓘ), `WikiSheet` (Bottom Sheet), `WikiScreen` (Übersicht + Suche) |
| `BodyMap.kt` | Körperdiagramm (Meerschweinchen, vorne + hinten): `MUSCLES` (Muskel-IDs + Namen), `BODY_SHAPES` (SVG-Pfade, linke Hälfte + Spiegelung), `MuscleMap(primary, secondary)`. Muskeln: `musclesOf(name)` = Wiki-JSON (`primary`/`secondary`, alle Grundübungen) bzw. bei eigenen Übungen `CustomMeta.muscles` (gespeichert als `customMuscles`); `MusclePicker`/`MuscleEditDialog`. Heatmap: `muscleScore(sets)` (Hauptmuskel 1, Hilfsmuskel 0,5 pro Satz), `relativeLoad`, `HEAT_LEVELS`, `StatPeriod`, `MuscleHeatCard` (Startseite in %, Trainingsübersicht mit Satz-Summe) |
| `ExerciseImages.kt` | Übungsbilder statt Piktogrammen: `assets/exercises/<schlüssel>.jpg`, `exerciseImageKey(name)` („Bankdrücken (Langhantel)“ → `bankdruecken-langhantel`, Grundübung-Bild gilt für alle Varianten), `ExerciseImage`; `ExerciseIcon` nimmt automatisch das Foto (abgerundetes Quadrat, leicht gezoomt); `ExerciseBackdrop` = Bild als durchsichtiger Hintergrund (Übungsdetail, Trainingsseite; Web: `screen({ backdrop })`) |
| `Icons.kt` | `AppIcon`/`Ico` (Linien-Icons für Buttons/Navigation) |
| `Pictograms.kt` | Übungs-Piktogramme: `Motion` (~47 Bewegungen), `MOTION_OF` (Katalog-Name → Bewegung), `ExerciseIcon(name, group)`, `MuscleIcon(group)`, `drawMotion()` |
| `Components.kt` | `GroupHeader`, `ChartCard`, `HorizontalBars`, `HistoryBars`, `LineChart`, `ProgressChart` |
| `assets/wiki/*.json` | Wiki-Inhalte pro Muskelgruppe, Schlüssel = Grundübung (`base`): `muscles`, `how`, `tips` (je `[de, en]`), optional `variants` (Schlüssel = Klammertext des Katalog-Namens) |
| `res/mipmap-*/` | App-Icon (Meerschweinchen): `ic_launcher_foreground.png` (108 dp, Adaptive Icon) + Legacy-`ic_launcher(_round).png` (48 dp), erzeugt per PowerShell/System.Drawing aus dem Original-PNG |
| `ui/theme/` | Farben (Indigo), Typografie, Formen; `GymTrackerTheme(dynamicColor = false)` |

## Datenmodell & wichtige Konventionen
- **Übungsnamen sind Schlüssel** (deutscher Katalog-Name). Anzeige immer über `exName(name)` (übersetzt).
  Katalog-Namen haben die Form „Grundübung (Gerät)“ (`CatalogExercise.base` = Grundübung).
  Umbenennen nur mit Eintrag alt → neu in `RENAMED_EXERCISES` (Catalog.kt); `readFrom()` wendet
  `migrateName()` auf alle gespeicherten Namen an (auch bei Import alter Backups). Einträge nie löschen.
- `GymSet.reps` ist je nach Übung: Wiederholungen | **Minuten** (Cardio, `group.isCardio`) | **Sekunden** (`isTimed(name)`).
  Cardio/Zeit zählen nicht ins Volumen (kg × Wdh.).
- Eigene Übungen: Gruppe in `GymStore.groups`, Gerät/Zeit in globalem `CustomMeta`.
- `TrainingSession` speichert nur Start/Ende + Kopie von Name/Übungen/Zielen; die Sätze sind
  alle `sets` der Übungen im Zeitraum. **Negative Session-ID = „freies Training“** (Sätze ohne
  Workout, pro Tag virtuell berechnet, `freeOnly`-Filter gegen Doppelzählung).
- Ziel-Sätze: `Workout.targets`; Standard **0 = optional**. Prozent = `goalCompletion()`.
- kcal: MET-Methode, braucht Körpergewicht (`weightAt`), sonst `null`.
- Neue gespeicherte Felder: in `readFrom()` mit `opt…`-Default lesen (alte Dateien bleiben gültig)
  und in `toJson()` schreiben. Bei Import zusätzlich in `importJson()` leeren.
- **Kotlin-Falle:** Properties in `GymStore`, die `load()` befüllt, müssen **vor** `init { load() }` stehen.
- `HomeScreen`-Tabs: 0 Start, 1 Übungen, 2 Workouts, 3 Kalender, 4 Körper (Pager-Größe 5).
- Alle UI-Texte über `tr(...)`; Zahlen/Datum über `fmtW`/`fmtDate` (locale-abhängig).
- **Neue Katalog-Übung** = Zeile in `CATALOG` (Catalog.kt) **und** Eintrag in `MOTION_OF` (Pictograms.kt),
  bei Cardio zusätzlich MET in `CARDIO_MET`, bei Zeitübung in `TIMED_CATALOG`.
  Prüfen, dass `MOTION_OF` alle Katalog-Namen abdeckt (Vergleich per grep/comm).
  Neue Grundübung zusätzlich in `assets/wiki/<gruppe>.json` beschreiben (`gen-data.mjs` warnt, wenn ein Eintrag fehlt,
  und bricht bei unbekannten Grundübungen/Varianten ab).
- **Übungsbild hinzufügen:** `.	oolsdd-exercise-image.ps1 -Source <bild> -Exercise "<Katalog- oder Grundübungsname>"`
  (verkleinert auf 512 × 512 JPEG), danach `node web/tools/gen-data.mjs` (kopiert nach `web/img/exercises/`).
- Symbole: wo der Übungsname bekannt ist `ExerciseIcon(name, group)`, sonst `MuscleIcon(group)`.
  Piktogramme zeichnen in 0..1-Koordinaten, Seitenansicht blickt nach links; alles innerhalb
  des Kreises halten (Abstand zur Mitte < 0,48). Zum Prüfen temporär ein Raster aller
  `Motion.entries` (z. B. im Dashboard) anzeigen und Screenshot machen.

## Web-Version / iPhone (`web/`)
PWA (Web-App „Zum Home-Bildschirm“) mit gleichem Funktionsumfang, für iPhones. Reines HTML/CSS/JS
(ES-Module), **kein Framework, kein Build-Schritt, keine npm-Abhängigkeiten**.
- **Gleiches JSON-Format** wie Android (Export/Import in beide Richtungen), gespeichert im `localStorage`
  (`gym_data`); laufendes Training zusätzlich in `gym_training` (iOS beendet Web-Apps im Hintergrund).
- `web/js/data.js` wird **erzeugt**: `node web/tools/gen-data.mjs` liest CATALOG, CARDIO_MET,
  RENAMED_EXERCISES, TIMED_CATALOG, TEMPLATES, MOTION_OF und den `drawMotion`-Code aus den Kotlin-Dateien.
  Erzeugt außerdem `web/js/wiki-data.js` aus `app/src/main/assets/wiki/*.json` (Logik in `web/js/wiki.js`)
  und `web/js/body-data.js` aus `BodyMap.kt` (Logik in `web/js/bodymap.js`).
  **Nach jeder Änderung an Catalog.kt / Model.kt / Pictograms.kt / BodyMap.kt / Wiki-JSON erneut ausführen.**
- Dateien spiegeln die Kotlin-Struktur: `core.js` (I18n/Format/Model), `store.js` (GymStore),
  `stats.js` (Stats/Progression/Dashboard-Berechnungen), `pictograms.js`, `ui.js` (`h()`-DOM-Helfer,
  Icons, Diagramme, Dialog), `nav.js` (Navigation = GymApp-State + Browser-History), `filter.js`,
  `screens/{home,exercise,workout,training}.js`, `app.js` (Bildschirmauswahl + `render()`).
- Prinzip: jede Änderung → kompletter Neuaufbau des Bildschirms (`render()`); Fokus/Scroll werden
  über Element-`id`/`data-scroll` wiederhergestellt → **Eingabefelder brauchen eine stabile `id`**.
  Uhren/Timer aktualisieren nur einzelne Elemente über `onTick()`.
- Neue Dateien in `web/sw.js` (`FILES`) eintragen und `CACHE`-Version erhöhen.
- Testen: `node web/tools/serve.mjs` → im Emulator-Chrome `http://10.0.2.2:8080`
  (`adb shell am start -a android.intent.action.VIEW -d http://10.0.2.2:8080/ com.android.chrome`).
  JS im Emulator-Browser ausführen: `adb forward tcp:9222 localabstract:chrome_devtools_remote`,
  dann per DevTools-Protokoll (WebSocket von `http://localhost:9222/json`).
- iOS-Grenzen: keine Vibration (stattdessen Ton per WebAudio, muss per Tipp „entsperrt“ werden),
  Timer laufen im Hintergrund nicht, Wake Lock nur ab iOS 16.4. Für „Zum Home-Bildschirm“ +
  Offline braucht es **HTTPS** (z. B. GitHub Pages).

## Ideen-Backlog (vom Nutzer noch nicht umgesetzt)
Ernährungstagebuch, Körpermaße, Fortschrittsfotos, Satz-Typen (Aufwärmen/Drop), Notizen,
Supersätze, Trainingsplan mit Wochentagen + Erinnerung, Pausen-Timer als Benachrichtigung
(Foreground Service), Homescreen-Widget. (Automatisches Backup: umgesetzt in 1.1, nur Android.)
- **KI-Coach/Chatbot** (besprochen 19.11.2026, zurückgestellt): Chat mit Trainingsdaten-Zusammenfassung als Kontext.
  Offene Punkte: Kosten/Datenschutz (Daten gehen an Anbieter), Modellwahl, API-Schlüssel nur lokal in den
  Einstellungen (nie im öffentlichen Repo), Android ohne neue Abhängigkeit per HTTP vs. offizielles SDK.
- **Genauere kcal** (besprochen 19.11.2026, zurückgestellt): korrigierte MET-Methode mit Grundumsatz nach Mifflin-St-Jeor
  → optionale Einstellungen Geschlecht, Geburtsjahr, Größe; ohne Angaben weiter einfache MET-Formel.
