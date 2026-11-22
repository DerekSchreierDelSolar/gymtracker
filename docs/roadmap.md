# Roadmap – Feature-Ideen vom 22.11.2026

Reihenfolge so gewählt, dass jeder Schritt auf dem vorherigen aufbaut. Jeder Schritt = Android **und** Web,
neue Funktion → Antwort in `assets/bot.json`, neue gespeicherte Felder → `readFrom`/`toJson`/`importJson` (beide Plattformen).

**Grundsatz Motivation:** positive Verstärkung, keine Schuld-/Verlustangst-Mechanik (kein „Du hast versagt“,
Ruhetage nie bestrafen). Kein Vergleich mit anderen Menschen / Bevölkerungsstatistiken (bewusst verworfen).

## Phase 0 – Aufräumen
0. ✅ **Bot-Fehler „einen“ ≈ „Eisen“** (erledigt 22.11.2026: Supplement-Suche `has(…, strict = true)` = Wörter unter 6 Buchstaben nur exakt; FAQ-Stichwörter „einen satz“/„satz ein“).

## Phase 1 – Ziele + Erfolgserlebnisse (Grundlage für alles Weitere)
1. ✅ (22.11.2026, Karte auf der Startseite statt im Profil-Dialog) **Langfristige Ziele im Profil.** Typen: Kraft (Übung + kg, über 1RM/Rekord), Ausdauer (Cardio-Übung + Minuten am Stück),
   Körpergewicht (Ziel-kg, Startwert merken), Gewohnheit („Ausdauer erhöhen“ = Cardio-Minuten/Woche,
   „gesund bleiben“ = Wochenziel-Quote + alle Muskelgruppen). JSON `goals`. Fortschritt in % als Karte auf der Startseite.
2. ✅ (22.11.2026) **Erfolgs-Berechnung** (eine gemeinsame Funktion, z. B. `achievements(session)`): Rekorde, bester Vergleich mit
   letztem Mal (Volumen, Sätze, Gewicht), x. Training der Woche, Muskel nach Pause wieder dabei, Ziel-Fortschritt.
   Liefert immer mindestens einen Erfolg. Wird von 3, 5, 7 und 10 wiederverwendet.
3. ✅ (22.11.2026) **Feier im SummaryScreen (Peak-End):** Konfetti (selbst gezeichnet: Compose-Canvas / CSS) + Vibration bei Rekorden,
   Erfolge aus 2 groß oben, Bezug zum Ziel („Noch 7,5 kg bis 100 kg – vor 3 Monaten waren es noch 20“).
4. ✅ (22.11.2026) **Meilensteine auf der Startseite:** nächste erreichbare Schritte („noch 2,5 kg bis 100 kg Kniebeuge“,
   „noch 1 Training bis Wochenziel“), bevorzugt passend zum Ziel.
5. ✅ (22.11.2026) **Der Morgen danach:** Zeile auf der Startseite zum letzten Training („Gestern: 12 Sätze, Rekord bei Rudern 💪“);
   Erinnerung mit Vorfreude statt Pflicht („Heute Beine – letztes Mal 90 kg, schaffst du 92,5?“ über `Progression.kt`);
   Neuanfang-Effekt (Montag / Monatsanfang / Neujahr: „Neue Woche, neues Ziel“).

## Phase 2 – Belohnungssystem
6. ✅ (22.11.2026) **Level-System:** Punkte für Trainings/Sätze/Ziel-Fortschritt → Level + Balken (Startseite/Profil), rückwirkend
   aus vorhandenen Daten berechnet, Level-Aufstieg wird im Summary gefeiert (nutzt 3).
7. ✅ (22.11.2026) **Abzeichen:** Übersicht (alle sichtbar außer „geheimen“), rückwirkend berechnet. Meilensteine (10./50./100. Training,
   1 t an einem Tag, Ziel erreicht) + seltene Überraschungen (Frühaufsteher < 7 Uhr, alle Muskelgruppen in einer Woche …).
   Neues Abzeichen im Summary (nutzt 2 + 3).
8. ✅ (22.11.2026) **Serie mit Joker:** Serie bleibt wochenbasiert, 1 Joker pro Monat (Urlaub/krank), damit sie nicht an einer Woche zerbricht.
9. ✅ Sprüche umgesetzt 22.11.2026 (Quotes.kt / quotes.js: 22 Sprüche, Auswahl nach Training, pro Training fest, ~5 % selten mit ✨). **OFFEN – Bilder (macht der Nutzer per KI, wenn er kann):** Spezialbilder für die seltenen Sprüche (Meerschweinchen mit goldener Gurke 🌟, vornehmes Meerschweinchen mit Zylinder 🎩) und Level-Optik (ab Level 5 Stirnband, 10 Hantel, 20 Gürtel/Kraftpose, 30 Eisen-Look, 40 Muskel-Meerschwein, 50 Krone) im Stil des App-Icons; danach in QuoteBubble / LevelCard einbauen. **Meerschweinchen:** zufällige Sprüche im Summary (Pool DE/EN, selten Spezialbild); Optik ändert sich mit dem Level
   (Stirnband, Hantel …). Braucht Bilder → zusammen mit den Übungsbildern per KI erzeugen.
10. ✅ (22.11.2026) **Identität im Rückblick:** „9 von 10 Wochen Ziel geschafft – das ist Routine“, Statistik über dich
    (Lieblingsübung, typische Trainingszeit, stärkste Muskelgruppe) in `Review.kt`.

## Phase 3 – Training verbessern
11. **Scheibenrechner** (Zielgewicht → Scheiben pro Seite; Stange + vorhandene Scheiben in den Einstellungen; in `SetInput`
    bei Langhantel) + **Aufwärm-Rampe** (aus Arbeitsgewicht 2–3 Aufwärmsätze, z. B. 40 % × 10, 60 % × 5, 80 % × 2).
12. **RPE/RIR pro Satz** (optional) → genauerer 💡-Vorschlag in `Progression.kt`; **%-Tabelle zum 1RM** im Reiter Fortschritt.
13. **Supersätze:** zwei Übungen im Workout verbinden, im Training automatischer Wechsel nach jedem Satz, Pause erst nach dem Paar.
14. **Mehrwöchige Programme:** Blöcke (z. B. 4 Wochen Aufbau + 1 Woche Deload mit automatisch reduzierten Zielen),
    Erweiterung von `Plan.kt`; Deload-Hinweis kann RPE (12) und Erholungs-Check nutzen.

## Phase 4 – Körper + Auswertung
15. **Körpermaße** (Taille, Arm, Brust …) als weitere Kurven im Tab „Körper“ + **Körperfett/Muskelmasse** aus Health Connect.
    Passt auch als Zieltyp in 1 (z. B. Taillenumfang).
16. **Relative Kraft** (eigener Rekord × Körpergewicht, nur für dich, kein Vergleich mit anderen).
17. **Jahresrückblick („Wrapped“):** Gesamtvolumen, häufigste Übung, größter Rekord-Sprung, längste Serie, erreichte Ziele,
    Abzeichen + Level des Jahres (nutzt 1–10 und `Review.kt`).

## Phase 5 – Große Brocken
18. **Homescreen-Widget** (klassische `RemoteViews`, ohne Glance): Wochenziel, Serie, Level, „Heute dran“ + Schnellstart.
19. **Workout teilen per QR-Code** (offline, ohne `INTERNET`; QR-Erzeugung ohne Bibliothek selbst schreiben).
20. **Fortschrittsfotos** lokal in `filesDir`, Vorher/Nachher-Schieberegler (Backup-Frage klären, nicht ins JSON).
21. **Ernährung** aus Health Connect lesen (Eiweiß, kcal-Bilanz) statt eigenem Tagebuch.

Weiterhin separat geplant (siehe CLAUDE.md): KI-Coach, weitere Health-Connect-Ideen, Play-Store-Veröffentlichung.
