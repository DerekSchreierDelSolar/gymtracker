# Play-Store-Veröffentlichung (Notizen, Stand 22.11.2026)

Noch nicht umgesetzt. Fazit: **Die App dürfte veröffentlicht werden.** Sie hat keine Internet-Berechtigung,
erfasst keine Daten, zeigt keine Werbung und hat keine In-App-Käufe, ist richtlinienseitig also unkompliziert.

## 1. Technische Änderungen

| Punkt | Stand | To-do |
|---|---|---|
| Package-Name | `com.example.gymtracker` | Play lehnt `com.example.*` ab. Eigenen Namen wählen (z. B. `de.<name>.gymtracker`), `applicationId` + `namespace` in `app/build.gradle.kts` ändern. **Danach nie mehr änderbar.** |
| Format | APK | App Bundle: `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab` |
| Signierung | `signing/gymtracker-release.jks` | Weiter als „Upload-Schlüssel“ nutzen. Google signiert die App selbst (Play App Signing). |
| targetSdk | 37 | ✅ Play verlangt ein aktuelles targetSdk (höchstens etwa 1 Jahr alt), das ist erfüllt. |
| Berechtigungen | VIBRATE, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED | ✅ Unkritisch. Die Erinnerung plant inexakt, daher ist keine Berechtigung für exakte Alarme nötig. |

Ein neuer Package-Name bedeutet eine **andere App**. Die per USB installierte Version wird nicht aktualisiert.
Die Daten kommen per Export/Import in die neue App.

## 2. Ablauf in der Play Console

1. Entwicklerkonto anlegen: einmalig 25 US-$, mit Ausweis- und Adressprüfung.
2. **Neue private Konten:** geschlossener Test mit **mindestens 12 Testern über 14 Tage**, erst dann wird die Produktion freigeschaltet.
3. Store-Eintrag: Name, Kurz- und Langbeschreibung, Icon 512×512, Feature-Grafik 1024×500, mindestens 2 Screenshots.
4. Abschnitt „App-Inhalte“:
   - **Datenschutzerklärung (URL) ist Pflicht**, auch ohne Datenerfassung. Eine einfache Seite auf GitHub Pages reicht
     („Alle Daten bleiben lokal auf dem Gerät, keine Übertragung“).
   - Datensicherheit: „Keine Daten erfasst oder geteilt“.
   - Gesundheits-Apps-Erklärung: Fitness/Training, keine medizinische App.
   - Inhaltsbewertung (Fragebogen), Zielgruppe (keine Kinder), Werbung: nein.
5. Prüfung durch Google: einige Tage, beim ersten Mal oft länger.

## 3. Rechtliches (selbst prüfen)

- **Bildrechte:** Für die Übungsfotos (`assets/exercises/`) und das Original-PNG des Meerschweinchen-Icons braucht man
  die Rechte. Eigene oder KI-generierte Bilder sind in Ordnung, Bilder aus dem Netz meist nicht. Das ist ein häufiger Ablehnungsgrund.
- **App-Name:** Es gibt schon mehrere Apps namens „GymTracker“. Ein eindeutiger Name vermeidet Ärger mit Markenrechten
  und dem Verbot, andere Apps nachzuahmen.
- **Hinweis „Keine medizinische Beratung“** für Bot und Wiki: empfohlen, keine Pflicht.
- **Kontaktdaten:** Google zeigt die Entwickler-E-Mail öffentlich an (die Adresse nur bei Monetarisierung), daher eine eigene Adresse nehmen.
  Die Impressumspflicht für kostenlose private Apps ist in Deutschland umstritten.

## 4. Ohne Play Store

Die APK aus `release/` oder die Web-Version reichen weiterhin. Google führt schrittweise eine
Entwickler-Verifizierung auch für Apps außerhalb des Play Stores ein (ab 2026 in einzelnen Ländern, ab 2027 weltweit).
Installationen per USB/ADB sollen laut Google ausgenommen bleiben.
