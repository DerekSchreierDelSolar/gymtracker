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
| `Catalog.kt` | `CATALOG` (207 Übungen: DE-Name = Schlüssel, Grundübung, Gruppe, Gerät, EN-Name), `RENAMED_EXERCISES`, MET-Werte, `WeightEntry` |
| `I18n.kt` | `appLanguage`, `tr()`, `appLocale()` |
| `Format.kt` | `fmtW`, `fmtDate`, `fmtDateTime`, `fmtDuration`, `plural` |
| `Stats.kt` | `ExerciseResult`, `SessionStats`, `sessionStats()`, `estimateKcal()`, `allSessions()`; `Profile` (optional Geschlecht/Geburtsjahr/Größe, im JSON `profile` = { sex: m/f, birthYear, height } nur wenn gesetzt, Eingabe `ProfileSettings` im Einstellungen-Dialog), `bmrKcalPerDay` (Mifflin-St-Jeor), `kcalPerMetHour` = Grundumsatz/24 statt kg, sobald das Profil vollständig ist (Web: stats.js, Validierung `validYear`/`validHeight` in core.js) |
| `Filter.kt` | `ExerciseFilter` (Kategorie/Gerät/Suche), `FilterUiState`, `FilterActions`, `FilterBar` |
| `HomeScreen.kt` | Startseite: `ExerciseTile` zeigt den Namen zweigeteilt (`splitExerciseName`: Grundübung, einzelnes Wort per autoSize einzeilig, + Gerät klein; Web: `tileName`, CSS `--chars`); TopBar mit Buch (Wiki) + Sprechblase (Fragen) immer sichtbar, ⋮-Menü (Einstellungen/Export/Import), NavigationBar, **HorizontalPager mit 5 Tabs**, `SettingsDialog`, `ExerciseGrid`, `TileGrid`, `ExerciseTile` |
| `Review.kt` | Karte „Rückblick“ auf der Startseite (unter den Wochen-Kennzahlen): letzte abgeschlossene Woche bzw. letzter Kalendermonat (`ReviewPeriod`, `reviewStart(period, back)`) vs. Zeitraum davor – Trainings, Sätze, Volumen, Trainingszeit (nur gestartete Workouts), kcal mit Δ (grün/rot), Rekorde, `topMuscles`, `neglected` (in den 3 Zeiträumen davor trainiert, jetzt < 20 % des Spitzenmuskels). Web: `review.js`, `nav.reviewPeriod` |
| `DashboardTab.kt` | Tab 0 „Start“: Wochenziel, Serie, Schnellstart, Rückblick, Muskel-Heatmap, Diagramme (Volumen und `weeklyKcal` pro Woche, x-Achse Kalenderwoche `fmtWeek` = ISO 8601, „KW 47“ / „W47“) (Zeitraum-Umschalter), Rekorde |
| `WorkoutScreens.kt` | Tab 2 Liste, `NewWorkoutDialog` (+Vorlagen), `WorkoutScreen` (Übungen als Liste `WorkoutExerciseRow`, Drag & Drop am Griff sofort bzw. Zeile gedrückt halten, Auto-Scroll am Rand per `LaunchedEffect(dragKey)` + `requestScrollToItem` gegen Mitscrollen beim Tausch des obersten Eintrags; Web: `exerciseRow`/`enableDrag` mit FLIP-Animation + rAF-Auto-Scroll), `WorkoutPickerScreen` (Workout bearbeiten: Liste, oben die Übungen des Workouts; antippen → `WorkoutExercisePage` = Info wie Reiter „Info“ + feste Leiste mit Hinzufügen bzw. Ziel-Sätzen/Entfernen; Web: `nav.pickDetail`) |
| `TrainingScreen.kt` | Trainingsmodus (Pager pro Übung, Name + Eingabe + heutige Sätze + Verlauf früherer Tage per `HistoryDayCard`), Titel „1/6 · Workout“ + Dauer; Seite: Name + ⓘ, `RecordLine`, drei `CircleStat` (Wdh.-Ziel per `repsGoal`, Pause: antippen = Länge ändern bzw. Countdown überspringen, Sätze dieser Übung), Eingabe, heutige Sätze, Verlauf; Vibration |
| (Training minimieren) | Zurück bzw. Pfeil oben links im Training = minimieren, nicht beenden (beenden nur über „Beenden“). Training hängt an `GymApp.trainingWorkoutId` (unabhängig von `workoutId` = angezeigtes Workout), `trainingOpen = false` = minimiert → `TrainingBar` („Training läuft · Dauer“, Pausen-Countdown; Startseite über der NavigationBar per `HomeScreen(trainingBar)`, sonst unten mit `consumeWindowInsets`). Pause (`restEnd`/`restTotal`) liegt in GymApp, Ablauf/Vibration/Benachrichtigung über `RestTimerEffect`. Anderes Workout starten, während eins läuft → Hinweis „Training läuft bereits“; gleiches Workout → fortsetzen. Minimiert auf der Startseite: Zurück = `moveTaskToBack`. Web: `nav.trainingWorkoutId`/`nav.trainingOpen` (in `gym_training` gespeichert), `trainingBar()` + `restWatch()` (im globalen Takt) in training.js, `addTrainingBar` in app.js |
| (Workout im Training bearbeiten) | Stift in der Trainings-Kopfzeile → `WorkoutPickerScreen` über dem laufenden Training (`GymApp.editInTraining` = vorher angezeigte Übung; Web: `nav.editInTraining`, Zurück-Logik in app.js). Änderungen gelten dauerhaft fürs Workout; beim Zurückkehren springt der Index auf dieselbe Übung. |
| (Alternative im Training) | ⇄ neben dem Übungsnamen → `AlternativesSheet` (TrainingScreen.kt) mit `similarExercises()` (WorkoutWizard.kt, Kosinus-Ähnlichkeit der Muskel-Beiträge, max. 2 Varianten je Grundübung). Tausch gilt nur fürs laufende Training: `GymApp.swaps` (Original → Alternative, rememberSaveable) bzw. `nav.swaps` (Web, in `gym_training` gespeichert); `sessionWorkout()` ersetzt Übungen + Ziele, `finishTraining` speichert die getauschten Übungen. Das Workout selbst bleibt unverändert. |
| `SummaryScreen.kt` | Übersicht nach dem Training, `ComparisonLine`, `StatBox` |
| `CalendarTab.kt` | Tab 3: Monatskalender mit Abschluss-Ringen, `dayKey()`, `CompletionRing` |
| `BodyTab.kt` | Tab 4: Körpergewicht, Zeile „Gewicht fließt in die kcal-Schätzung ein“ + ⓘ (MET-Erklärung im Dialog; Wochen-kcal nur auf der Startseite), `startOfWeek()`; Gewichtsverlauf ohne Liste: Punkt antippen (`LineChart(onTapPoint)`) → `WeightEditDialog` (Gewicht + Datum, Speichern = `updateWeight(entry, kg, time)`, Löschen mit Undo); `DayPickerDialog` = Material-Kalender in App-Sprache/-Format (LocalConfiguration mit `appLocale()`, eigener Titel/Kopfzeile, ohne Texteingabe), liefert 12:00 Ortszeit; Web: `lineChart(values, unit, onTapPoint)`, `weightEditDialog`, `dateField` (ui.js: Text im App-Format, unsichtbares natives Datumsfeld darüber) |
| `ExerciseDetail.kt` | `SetInput` (inkl. Stoppuhr), `SetRow`/`EditSetDialog`, `ExerciseDetailScreen` mit Reitern Training (`RecordLine`, Verlauf neueste zuerst, Satz antippen = bearbeiten)/Fortschritt/Info (`MuscleSideCard` Vorder-/Rückseite, Figur mittig groß, Muskelliste nur in der letzten Karte + Wiki), `glassCardColors()`, `ExerciseProgress` (Rekord, Zuwachs, Kennzahl + Tage/Wochen/Monate) |
| `CatalogScreen.kt` | Katalog + `CustomExerciseDialog` |
| `Plan.kt` | Trainingsplan: `Workout.days` (1 = Mo … 7 = So, im JSON `days` nur wenn nicht leer), `weekdayOf`, `dueToday`, `planPick` (Startseite „Heute dran“/„Morgen dran“/„Als Nächstes · Tag“, sonst „Weiter mit“), `WorkoutDaysRow` (Chips im Workout); `Reminder` (AlarmManager inexakt, SharedPreferences gerätegebunden, `ReminderReceiver` auch nach Neustart), `ReminderSettings` im Einstellungen-Dialog. Web: `planPick` etc. in stats.js, Erinnerung nur Android |
| `Progression.kt` | 💡 Gewichts-Vorschlag (doppelte Progression) |
| `Bot.kt` | Fragen-Bot ohne KI: lädt `assets/bot.json` (`Bot.load`), `Bot.answer(frage)` → Übung (Ausführung/Tipps/Muskeln aus dem Wiki), Muskel/Region (passende Übungen nach Stufe) oder feste App-Frage; Wörter-Vergleich mit Tippfehler-Toleranz (Levenshtein), `Bot.history` = Verlauf |
| `WorkoutWizard.kt` | Workout-Assistent im Bot („Erstelle mir ein Workout“, Erkennung in `Bot.wantsWorkout`; genannte Muskeln/Gruppen wie „Brust-Workout“, „Beintraining“ werden per `presetMuscles` vorausgewählt: `targets` + `workoutGroups` in bot.json, zusammengesetzte Wörter per `splitCompounds`): `WizardMuscleStep` (Figur antippbar über `BodyFigure(onTapMuscle)` + Muskel-Chips; 1× Hauptfokus / 2× mittel / 3× leicht / 4× aus), `WizardEquipmentStep`, `WorkoutWizard.pick()` (gierig, Bedarf 3 / 1,5 / 0,6 Hauptübungen, 4–8 Übungen, eine Variante je Grundübung), `create()` mit 3 Ziel-Sätzen. Schritte als `BotMessage.widget`, Ergebnis mit `workoutId` → „Workout öffnen“. Web: `wizard.js` + bot.js, antippbare SVG-Ebene `tappableFigure` in bodymap.js |
| `BotTab.kt` | `BotScreen` „Fragen“ (eigener Bildschirm über die Sprechblase oben rechts und im Wiki, `GymApp.showBot`; Web: `nav.bot`/`botScreen`): Chat-Blasen, Übungs-Chips öffnen `WikiSheet`, Vorschläge (zufällig aus Pool, erster = zuletzt trainierte Übung), Eingabefeld |
| `Wiki.kt` | Übungs-Wiki: lädt `assets/wiki/*.json` (`Wiki.load`), `wikiFor(name)`, `WikiButton` (ⓘ), `WikiSheet` (Bottom Sheet), `WikiScreen` (Übersicht + Suche) |
| `MuscleWiki.kt` | Muskel-Wissensdatenbank: lädt `assets/muscles.json` (`MuscleWiki.load`, Schlüssel = Muskel-ID aus `MUSCLES`; je Muskel `latin`, `parts`, `origin`, `insertion`, `nerve`, `everyday` als [de, en], `function`/`problems`/`stretch`/`facts` als Listen), `MuscleSheet`/`MuscleContent` (Figur mit rot markiertem Muskel, Anatomie, Funktion, Beschwerden mit Warnzeichen, Dehnen, `mainExercisesFor()` = Übungen mit Hauptarbeit), Figur + Muskelnamen in `MuscleSideCard`/`MuscleMap` (Übungs-Info, Wiki-Blatt) antippbar → `MuscleSheet` (`levelEntries`, `MuscleNames`; im Muskel-Editor `tappable = false`; Web: `muscleMap(levels, { tap: true })`, `muscleNames`, `.muscle-link`), `muscleWikiItems` = Reiter „Muskeln“ im `WikiScreen` (SegmentChip Übungen/Muskeln, antippbare Figur, Suche). Bot: `anatomyWords` in bot.json (anatomy/function/problems/stretch/general) → `Bot.muscleAnswer` (nur gefragte Abschnitte). Web: `muscle-data.js` (gen-data), `openMuscle`/`muscleList` in wiki.js, `nav.wikiMuscles`, `muscleAnswer` in bot.js |
| `SupplementWiki.kt` | Supplement-Wissensdatenbank (Wiki-Reiter „Supplements“, `WikiScreen` hat `tab` 0 Übungen / 1 Muskeln / 2 Supplements; Web `nav.wikiTab`): `assets/supplements.json` (18 Einträge, Schlüssel = ID; `name`/`category`/`summary`/`what`/`dose`/`timing` [de, en], `effects`/`risks`/`tips` Listen, `keys` = Suchbegriffe ohne Umlaute für Suche + Bot, `evidence` 0–3, optional `deficiencyOnly`), Liste nach Evidenz gruppiert (`evidenceLabel`/`evidenceColor`), `SupplementSheet`. Bot: `findSupplement` → `supplementAnswer` (vor Muskel-/Übungsfragen, außer eine Übung passt länger). Web: `supplement-data.js` (gen-data prüft Felder), `supplementList`/`openSupplement` in wiki.js, `supplementAnswer` in bot.js. Inhalte nicht fachlich geprüft – vor Veröffentlichung gegenlesen lassen |
| `BodyMap.kt` | Körperdiagramm (Meerschweinchen, vorne + hinten): `MUSCLES` (Muskel-IDs + Namen), `BODY_SHAPES` (SVG-Pfade, linke Hälfte + Spiegelung; je Muskel `fiber` = Faserrichtung und `detail` = anatomische Fugen in 0..1 des Rahmens; `BodyFill.TISSUE` = Rumpf/Arme/Beine als Muskelgewebe, `BodyFill.DECO` = Zier-Muskeln ohne Heatmap), `MuscleMap(primary, secondary)`. Optik „plastisch“: `BodyFigure` zeichnet Licht-Ebenen (`lightOf`: Muskel-Polster, Rumpf/Glieder als Zylinder, Kopf als Kugel), Fasern, Fugen, Muskeln nahtlos (Rand in eigener Farbe, auf `silhouette` beschnitten) – Web identisch in `bodymap.js` (`svg()`). Muskeln: `musclesOf(name)` = Wiki-JSON (`primary`/`secondary`, alle Grundübungen) bzw. bei eigenen Übungen `CustomMeta.muscles` (gespeichert als `customMuscles`); `MusclePicker`/`MuscleEditDialog`. Heatmap: `muscleScore(sets)` (Hauptmuskel 1, Hilfsmuskel 0,5 pro Satz), `relativeLoad`, `HEAT_LEVELS`, `StatPeriod`, `MuscleHeatCard` (Startseite in %, Trainingsübersicht mit Satz-Summe) |
| `ExerciseImages.kt` | Übungsbilder statt Piktogrammen: `assets/exercises/<schlüssel>.jpg`, `exerciseImageKey(name)` („Bankdrücken (Langhantel)“ → `bankdruecken-langhantel`, Grundübung-Bild gilt für alle Varianten), `ExerciseImage`; `ExerciseIcon` nimmt automatisch das Foto (abgerundetes Quadrat, leicht gezoomt); `ExerciseBackdrop` = Bild als durchsichtiger Hintergrund (Übungsdetail, Trainingsseite; Web: `screen({ backdrop })`) |
| `Icons.kt` | `AppIcon`/`Ico` (Linien-Icons für Buttons/Navigation) |
| `Hints.kt` | Bedienhinweise nur bis zur ersten Nutzung: `Hints.show(key)` / `Hints.used(key)` (SharedPreferences `hints`, gerätegebunden; Schlüssel `list`, `drag`, `rest`, `weekGoal`, `weight`, `set`). Web: `showHint`/`hintUsed` in ui.js (localStorage `gym_hints`). Neue „Tippen = …“-Hinweise immer darüber |
| `Undo.kt` | „Gelöscht – Rückgängig“ (auch für „Fortschritt zurücksetzen“ in den Einstellungen: `resetProgress`/`restoreProgress`, Kopie `gym_data_before_reset.json`): `Undo.show(text) { … }` statt Nachfrage-Dialog, `UndoBar` 8 s über allen Bildschirmen (in MainActivity um `GymApp`); Web: `undoToast()` in ui.js. Wiederherstellen über `restoreSet`/`restoreWeight`/`restoreToList` |
| `HealthConnect.kt` | Health Connect (nur Android 14+, System-API `android.health.connect`, **keine Bibliothek**): `HealthSync` gleicht Körpergewicht in beide Richtungen ab. Eigene Einträge → `WeightRecord` mit `clientRecordId` „gymtracker-weight-<time>“ (Upsert, `clientRecordVersion` = jetzt); fremde Einträge (andere `dataOrigin`) → `store.importWeights()` (ohne Hook), Duplikat-Check ±1 min / 0,05 kg. SharedPreferences `health_connect` (gerätegebunden): `enabled`, `seen` (übernommene Record-IDs, kommen nach Löschen nicht wieder), `imported` (Zeitpunkte übernommener Einträge → nie zurückschreiben). Auslöser: `GymStore.onWeightChange` (in MainActivity gesetzt) und `syncIfDue` in `onStart` (max. alle 15 min) bzw. „Jetzt abgleichen“. `HealthSettings` im Einstellungen-Dialog (Berechtigung per `RequestMultiplePermissions`, Verwalten über `ACTION_MANAGE_HEALTH_PERMISSIONS`). Pflicht: `HealthPrivacyActivity` + `activity-alias` mit `VIEW_PERMISSION_USAGE` im Manifest, sonst keine Berechtigungsanfrage. Web: nicht möglich (Apple Health für Web-Apps gesperrt). Weitere Ideen (Training als `ExerciseSession`, Puls, Körperfett, Schritte/Schlaf, Ernährung) noch offen |
| `RestTimer.kt` | Pause als Benachrichtigung (nur Android): Countdown (Chronometer) + „Pause vorbei“ per Handler, wenn die App nicht sichtbar ist (`appVisible` aus onStart/onStop); Erlaubnis wird beim ersten Training einmal erfragt, vorher erklärt ein Dialog wofür (TrainingScreen `explainNotify`) |
| `Pictograms.kt` | Übungs-Piktogramme: `Motion` (~47 Bewegungen), `MOTION_OF` (Katalog-Name → Bewegung), `ExerciseIcon(name, group)`, `MuscleIcon(group)`, `drawMotion()` |
| `Components.kt` | `GroupHeader`, `ChartCard`, `HorizontalBars`, `HistoryBars`, `LineChart`, `ProgressChart` |
| `assets/bot.json` | Wissen des Bots: `faq` (je `keys` = Stichwörter ohne Umlaute, `a` = [de, en]), `targets` (Region/Muskel → Begriffe), Wortlisten für Übungs-/Muskel-/Wie-/Tipp-Fragen. **Bei neuen App-Funktionen hier eine Antwort ergänzen.** Web: `bot-data.js` (gen-data) + `web/js/bot.js` (gleiche Logik) |
| `assets/wiki/*.json` | Wiki-Inhalte pro Muskelgruppe, Schlüssel = Grundübung (`base`): `muscles`, `how`, `tips` (je `[de, en]`), `primary`/`secondary` (Muskel-IDs), optional `regions` (Region → Stufe 1 / 0.6 / 0.3, z. B. `"chest.upper": 1` bei Schrägbankdrücken; ersetzt für die genannten Muskeln primary/secondary), optional `variants` (Schlüssel = Klammertext des Katalog-Namens) |
| `res/mipmap-*/` | App-Icon (Meerschweinchen): `ic_launcher_foreground.png` (108 dp, Adaptive Icon) + Legacy-`ic_launcher(_round).png` (48 dp), erzeugt per PowerShell/System.Drawing aus dem Original-PNG |
| `ui/theme/` | Farben (Indigo), Typografie, Formen; `GymTrackerTheme(dynamicColor = false)` |

## Datenmodell & wichtige Konventionen
- **Übungsnamen sind Schlüssel** (deutscher Katalog-Name). Anzeige immer über `exName(name)` (übersetzt).
  Katalog-Namen haben die Form „Grundübung (Gerät)“ (`CatalogExercise.base` = Grundübung); einseitige Varianten
  „Grundübung (Gerät, einarmig/einbeinig)“ mit derselben Grundübung (gleiche Muskeln, Wiki, Bild). Listen sind alphabetisch
  (`sortedByName` in I18n.kt / `sortByName` in core.js; einseitige Variante direkt unter der normalen).
  Umbenennen nur mit Eintrag alt → neu in `RENAMED_EXERCISES` (Catalog.kt); `readFrom()` wendet
  `migrateName()` auf alle gespeicherten Namen an (auch bei Import alter Backups). Einträge nie löschen.
- `GymSet.reps` ist je nach Übung: Wiederholungen | **Minuten** (Cardio, `group.isCardio`) | **Sekunden** (`isTimed(name)`).
  Cardio/Zeit zählen nicht ins Volumen (kg × Wdh.).
- **Satz-Typen** (`SetType`: NORMAL, WARMUP, DROP, FAILURE; im JSON `"type"` nur bei ≠ NORMAL, Web: `s.type` fehlt = NORMAL).
  Im Training ist beim ersten Satz einer Übung (noch kein Satz seit Trainingsstart, nicht Cardio/Zeit) „Aufwärmen“ vorausgewählt (`SetInput(startWithWarmup)`, Web: `setInput(name, onAdded, warmupKey, goalReps)`). Vorbelegung je Typ über `setPrefill` (Aufwärmen: letzter Aufwärmsatz bzw. halbes Arbeitsgewicht × 10; sonst letzter Arbeitssatz mit Wdh.-Ziel); Typ wechseln ändert nur unveränderte Felder, nach einem Aufwärmsatz springen sie auf den Arbeitssatz. `isWork` (alles außer Aufwärmen) → Volumen, kcal, Ziel-Sätze, Satzzahlen, Heatmap; `isTop` (Normal + Versagen) →
  Rekorde, 1RM, Fortschritt, 💡-Vorschlag, Wdh.-Ziel, Vergleich „letztes Mal“. Neue Auswertungen immer entsprechend filtern
  (`work()`/`top()` bzw. `isWork`/`isTop` in core.js). Satzliste: `setLabels()` (Arbeitssätze 1., 2. …, Aufwärmen „A“).
- Kategorien (`MuscleGroup`): Brust, Rücken, Beine, Gluteus, Schultern, Bizeps, Trizeps, Unterarm, Bauch, Ganzkörper, Cardio, Sonstige. Früher gab es „Arme“ (`ARME`): beim Laden wird das für eigene Übungen per `migrateArmGroup()` nach Hauptmuskel auf Bizeps/Trizeps/Unterarm verteilt (Web: core.js). `groupOf()` nimmt für Katalog-Übungen immer die Katalog-Kategorie (gespeicherte Werte werden ignoriert). Neue Kategorie = enum in Model.kt + `MUSCLE_GROUPS` in core.js + `defaultMotion()`.
- Übungen hinzufügen (Katalog, Workout bearbeiten): erst Kategorie-Übersicht (`CategoryRow` / `categoryRow`), Antippen zeigt deren Übungen; mit Suche/Filter direkt alle Treffer. Zustand `openGroup` (Android) bzw. `nav.catalogGroup`/`nav.pickGroup` (Web).
- Eigene Übungen: Gruppe in `GymStore.groups`, Gerät/Zeit in globalem `CustomMeta`.
- „Meine Übungen“ (`exercises`) ist nur die Liste im Tab Übungen: `removeFromList()` (Kachel gedrückt halten) entfernt nur dort – Workouts, Sätze, Notizen, Gruppe bleiben; `deleteExercise()` (Papierkorb in der Übung) löscht alles. Eigene Übungen für Katalog/Auswahl daher über `customNames` (Liste + Workouts + Sätze), nicht `exercises`.
- `TrainingSession` speichert nur Start/Ende + Kopie von Name/Übungen/Zielen; die Sätze sind
  alle `sets` der Übungen im Zeitraum. **Negative Session-ID = „freies Training“** (Sätze ohne
  Workout, pro Tag virtuell berechnet, `freeOnly`-Filter gegen Doppelzählung).
- Ziel-Sätze: `Workout.targets`; Standard **0 = optional**. Prozent = `goalCompletion()`.
- kcal: MET-Methode, braucht Körpergewicht (`weightAt`), sonst `null`. Mit vollständigem Profil: kcal = MET × Grundumsatz/24 × h (`kcalPerMetHour`).
- Neue gespeicherte Felder: in `readFrom()` mit `opt…`-Default lesen (alte Dateien bleiben gültig)
  (Web: im `#readFrom` Typ prüfen, Ungültiges überspringen, Schlüssel wie `__proto__` über `entries()` filtern;
  `importJson` ist atomar – schlägt das Einlesen fehl, wird der alte Stand wiederhergestellt, auf beiden Plattformen)
  und in `toJson()` schreiben. Bei Import zusätzlich in `importJson()` leeren.
- **Kotlin-Falle:** Properties in `GymStore`, die `load()` befüllt, müssen **vor** `init { load() }` stehen.
- `HomeScreen`-Tabs: 0 Start, 1 Übungen, 2 Workouts, 3 Kalender, 4 Körper (Pager-Größe 5; Web: `TABS` in home.js). „Fragen“ ist seit der UX-Runde kein Tab mehr.
- Reihen gleich breiter Auswahl-Knöpfe: `SegmentChip` mit `Modifier.weight(1f)` (Components.kt; Web: `.segments`).
- Alle UI-Texte über `tr(...)`; Zahlen/Datum über `fmtW`/`fmtDate` (locale-abhängig). Volumen-Anzeigen über `fmtVolume` (ab 1000 mit Tausenderpunkt, gerundet) – nie in Eingabefelder, dort `fmtW`. Datum immer europäisch: DE TT.MM.JJJJ, EN TT/MM/JJJJ + 24 h (`appLocale()` = Locale.UK bzw. en-GB im Web). Datumsauswahl nur über `DayPickerDialog` / `dateField`, nie das rohe Systemformat zeigen.
- **Neue Katalog-Übung** = Zeile in `CATALOG` (Catalog.kt) **und** Eintrag in `MOTION_OF` (Pictograms.kt),
  bei Cardio zusätzlich MET in `CARDIO_MET`, bei Zeitübung in `TIMED_CATALOG`.
  Prüfen, dass `MOTION_OF` alle Katalog-Namen abdeckt (Vergleich per grep/comm).
  Neue Grundübung zusätzlich in `assets/wiki/<gruppe>.json` beschreiben (`gen-data.mjs` warnt, wenn ein Eintrag fehlt,
  und bricht bei unbekannten Grundübungen/Varianten ab).
- **Übungsbild hinzufügen:** `.	oolsdd-exercise-image.ps1 -Source <bild> -Exercise "<Katalog- oder Grundübungsname>"`
  (verkleinert auf 512 × 512 JPEG), danach `node web/tools/gen-data.mjs` (kopiert nach `web/img/exercises/`).
- **Muskel-Regionen:** Muskeln sind in Regionen geteilt (`REGION_NAMES` in BodyMap.kt, Schlüssel „muskel.teil“, z. B. `chest.upper`; ungeteilte Muskeln: Regionsschlüssel = Muskel-ID). Flächen: `BodyShape.region` (ganze Form) bzw. `regions` (Teilflächen als Polygon in 0..1 des Rahmens). `MuscleSet.levels()` / `levelsOf()` = Region → Stufe; Heatmap zählt je Region (`regionScore`, Hauptarbeit 1 / mittel 0,5 / leicht 0,25 Satz), Liste je Muskel = stärkste Region. `gen-data.mjs` prüft Regionsschlüssel und Stufen im Wiki.
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
  Erzeugt außerdem `web/js/wiki-data.js` aus `app/src/main/assets/wiki/*.json` (Logik in `web/js/wiki.js`), `web/js/muscle-data.js` aus `assets/muscles.json`, `web/js/supplement-data.js` aus `assets/supplements.json`
  und `web/js/body-data.js` aus `BodyMap.kt` (Logik in `web/js/bodymap.js`).
  **Nach jeder Änderung an Catalog.kt / Model.kt / Pictograms.kt / BodyMap.kt / Wiki-JSON / muscles.json / supplements.json erneut ausführen.**
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
Ernährungstagebuch, Körpermaße, Fortschrittsfotos,
Supersätze,
Homescreen-Widget. (Automatisches Backup: umgesetzt in 1.1, nur Android. Notizen pro Übung + Tag: umgesetzt in 1.2, `notes` im JSON = { Übung: { dayKey: Text } }. Satz-Typen: umgesetzt nach 1.2. Trainingsplan mit Wochentagen + Erinnerung: umgesetzt nach 1.5, Erinnerung nur Android. Pausen-Timer als Benachrichtigung: umgesetzt (UX-Runde), nur Android. Genauere kcal mit Profil, Wochen-/Monatsrückblick, Muskel-Wiki: umgesetzt 22.11.2026.)
- **KI-Coach/Chatbot** (besprochen 19.11.2026, zurückgestellt): Chat mit Trainingsdaten-Zusammenfassung als Kontext.
  Offene Punkte: Kosten/Datenschutz (Daten gehen an Anbieter), Modellwahl, API-Schlüssel nur lokal in den
  Einstellungen (nie im öffentlichen Repo), Android ohne neue Abhängigkeit per HTTP vs. offizielles SDK.
- **UX-Analyse 22.11.2026 – noch offen** (erledigt nach 1.9: Erklärung vor der Benachrichtigungs-Erlaubnis, Trainings-Chips mit Stand `trainingChipLabel`, Pause-Kreis „tippen zum Ändern“, Kachel-Menü `ExerciseMenuSheet` / `exerciseMenu`, Knopf „Übungen hinzufügen / bearbeiten“ statt Stift, antippbare Ziel-Pille + `TargetStepper` in der Workout-Liste, `Hints`, Training minimieren; erledigt in 1.9: Vorbelegung Aufwärmen/Wdh.-Ziel, „Diese Woche“ über Kennzahlen,
  Tausenderpunkt `fmtVolume`, Kachel-Name zweigeteilt, ▶ in der Workout-Liste, verpasst = roter Ring):
  - *Klein:* weitere Übungsfotos (Mischung Foto/Piktogramm wirkt uneinheitlich; braucht Bilder vom Nutzer)
- **Play-Store-Veröffentlichung** (besprochen 22.11.2026, nicht umgesetzt): Anforderungen und To-dos in `docs/play-store.md`
  (u. a. Package-Name statt `com.example`, `.aab`, Datenschutzerklärung, 12 Tester × 14 Tage, Bildrechte).
