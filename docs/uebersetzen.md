# Übersetzungsauftrag GymTracker

Fitness-/Trainingstagebuch-App (Android + Web). Maskottchen: ein Meerschweinchen (Sprüche sind witzig, locker).
Jede Datei `i18n-todo/<lang>-NNN.json` ist eine Liste von `{ "de": …, "en": …, "src": … }`.
`de` = deutscher Originaltext (Schlüssel), `en` = englische Fassung (zur Orientierung), `src` = Herkunft
(`*.kt`/`js/*` = Oberfläche, `catalog` = Übungsname, `catalog-base` = Grundübung, `wiki/*` = Übungsanleitung,
`muscles.json` = Muskel-Lexikon, `supplements.json`, `bot.json` = Antworten des Hilfe-Bots, `Quotes.kt` = Meerschweinchen-Sprüche).

## Ausgabe
Für jede Eingabedatei `i18n-todo/<lang>-NNN.json` eine Datei `i18n-todo/out/<lang>-NNN.json` schreiben:
ein JSON-Objekt `{ "<de exakt wie in der Eingabe>": "<Übersetzung>", … }` mit ALLEN Einträgen der Eingabedatei.
- Schlüssel zeichengenau übernehmen (inkl. Leerzeichen am Anfang/Ende, „“, …, \n, geschützte Leerzeichen U+00A0).
- Gültiges JSON (Anführungszeichen in Werten escapen: `\"`, Zeilenumbrüche als `\n`). Nach dem Schreiben mit
  `node -e "JSON.parse(require('fs').readFileSync('<datei>','utf8'))"` prüfen.
- Jede Datei sofort nach dem Übersetzen schreiben (nicht alles am Ende).

## Regeln
- **Platzhalter** `{0}`, `{1}` … genau so übernehmen (alle, jeder mindestens einmal), Reihenfolge darf sich ändern.
  Sie stehen für eingesetzte Werte (Zahlen, Namen, schon übersetzte Wortgruppen wie „3 Sätze“). Text drumherum so formulieren,
  dass er mit eingesetztem Wert grammatisch passt.
- Leerzeichen/Satzzeichen am Anfang und Ende beibehalten (Texte werden zusammengesetzt, z. B. `" · "`, `", "`, `": "`).
- Emojis, Symbole (✓ × · → ⓘ ＋ ★ 🔥), Zahlen, Einheiten (kg, lbs, min, s, kcal, bpm, cm, %, t) und Abkürzungen wie RIR, 1RM, RPE, HRV, MET, XP, BMI unverändert.
- Eigennamen bleiben: GymTracker, Health Connect, Apple Health, Google, Android, iPhone, Safari, Keytel, Mifflin-St Jeor.
- Datums-/Zeitformate (dd.MM.yyyy usw.) unverändert lassen.
- Anrede: informell (FR „tu“, IT „tu“, ES „tú“, TR „sen“), freundlich, kurz – Knopf-/Chip-Texte möglichst knapp (wenig Platz).
- Anführungszeichen in der Zielsprache: FR « … » (mit geschütztem Leerzeichen), IT « … » oder “…”, ES « … » oder “…”, TR “…”.
- Abkürzung „Wdh.“ (Wiederholungen): FR „rép.“, IT „rip.“, ES „rep.“, TR „tekr.“
- Übungsnamen (`catalog`): Form „Grundübung (Gerät, Variante)“ beibehalten, gebräuchlicher Fitness-Begriff der Sprache
  (englische Lehnwörter wie „Hip Thrust“, „Face Pull“, „Burpees“ sind ok, wenn so üblich). Die Grundübung (`catalog-base`)
  muss exakt dem Teil vor „ (“ der zugehörigen Katalog-Namen entsprechen (gleiche Übersetzung!).
- Medizinisch/anatomisch korrekte Fachbegriffe (Muskel-Lexikon), lateinische Namen unverändert.
- Inhalt NICHT kürzen oder ergänzen – vollständige, natürliche Übersetzung, keine Kommentare.

## Glossar (verbindlich)
| Deutsch | FR | IT | ES | TR |
|---|---|---|---|---|
| Satz / Sätze | série(s) | serie | serie(s) | set |
| Wiederholung(en) | répétition(s) | ripetizione/i | repetición(es) | tekrar |
| Training (Einheit) | séance | allenamento | entrenamiento | antrenman |
| Workout (Plan) | workout | workout | rutina | antrenman programı / program |
| Übung | exercice | esercizio | ejercicio | egzersiz |
| Gewicht | poids | peso | peso | ağırlık |
| Volumen | volume | volume | volumen | hacim |
| Pause | repos | recupero | descanso | dinlenme |
| Aufwärmen / Aufwärmsatz | échauffement / série d'échauffement | riscaldamento / serie di riscaldamento | calentamiento / serie de calentamiento | ısınma / ısınma seti |
| Arbeitssatz | série de travail | serie allenante | serie efectiva | çalışma seti |
| Drop-Satz | drop set | drop set | drop set | drop set |
| bis Versagen | jusqu'à l'échec | a cedimento | al fallo | tükenişe kadar |
| Supersatz / Riesensatz | superset / giant set | superserie / giant set | superserie / serie gigante | süperset / dev set |
| Rekord / Bestleistung | record | record | récord | rekor |
| Wochenziel | objectif hebdo | obiettivo settimanale | objetivo semanal | haftalık hedef |
| Serie (Wochen in Folge) | série | serie | racha | seri |
| Joker | joker | jolly | comodín | joker |
| Ziel | objectif | obiettivo | objetivo | hedef |
| Ziel-Sätze | séries cibles | serie obiettivo | series objetivo | hedef set |
| Abzeichen | badge | badge | insignia | rozet |
| Level / Stufe | niveau | livello | nivel | seviye |
| Erfolg | succès | traguardo | logro | başarı |
| Meilenstein | étape | traguardo | hito | kilometre taşı |
| Rückblick | bilan | riepilogo | resumen | özet |
| Trainingsblock / Deload | bloc d'entraînement / décharge (deload) | blocco / scarico (deload) | bloque / descarga (deload) | antrenman bloğu / deload |
| Erholung | récupération | recupero | recuperación | toparlanma |
| Ruhepuls / Puls | fréquence cardiaque au repos / pouls | frequenza a riposo / battito | pulso en reposo / pulso | dinlenik nabız / nabız |
| Körpergewicht | poids corporel | peso corporeo | peso corporal | vücut ağırlığı |
| Körpermaße | mensurations | misure corporee | medidas corporales | vücut ölçüleri |
| Einstellungen | Paramètres | Impostazioni | Ajustes | Ayarlar |
| Startseite / Start | Accueil | Home | Inicio | Ana sayfa |
| Kalender | Calendrier | Calendario | Calendario | Takvim |
| Körper (Tab) | Corps | Corpo | Cuerpo | Vücut |
| Übungen (Tab) | Exercices | Esercizi | Ejercicios | Egzersizler |
| Workouts (Tab) | Workouts | Workout | Rutinas | Programlar |
| Fragen (Bot) | Questions | Domande | Preguntas | Sorular |
| Wiki | Wiki | Wiki | Wiki | Wiki |
| Rückgängig | Annuler | Annulla | Deshacer | Geri al |
| Speichern / Abbrechen / Löschen | Enregistrer / Annuler / Supprimer | Salva / Annulla / Elimina | Guardar / Cancelar / Eliminar | Kaydet / İptal / Sil |
| Langhantel | barre | bilanciere | barra | halter |
| Kurzhantel | haltères | manubri | mancuernas | dambıl |
| Kabelzug | poulie | cavo | polea | kablo |
| Maschine | machine | macchina | máquina | makine |
| Körpergewicht (Gerät) | poids du corps | corpo libero | peso corporal | vücut ağırlığı |
| Kettlebell | kettlebell | kettlebell | kettlebell | kettlebell |
| Cardio-Gerät | appareil cardio | attrezzo cardio | máquina de cardio | kardiyo aleti |
| Widerstandsband | élastique | elastico | banda elástica | direnç bandı |
| einarmig / einbeinig | unilatéral (un bras) / (une jambe) | a un braccio / a una gamba | a una mano / a una pierna | tek kol / tek bacak |
| Brust, Rücken, Beine, Gluteus, Schultern, Bizeps, Trizeps, Unterarm, Bauch, Ganzkörper, Cardio, Sonstige | Pectoraux, Dos, Jambes, Fessiers, Épaules, Biceps, Triceps, Avant-bras, Abdos, Corps entier, Cardio, Autres | Petto, Schiena, Gambe, Glutei, Spalle, Bicipiti, Tricipiti, Avambracci, Addome, Total body, Cardio, Altro | Pecho, Espalda, Piernas, Glúteos, Hombros, Bíceps, Tríceps, Antebrazo, Abdomen, Cuerpo completo, Cardio, Otros | Göğüs, Sırt, Bacak, Kalça, Omuz, Biseps, Triseps, Ön kol, Karın, Tüm vücut, Kardiyo, Diğer |
| Meerschweinchen | cochon d'Inde | porcellino d'India | cobaya | kobay |
