# Übungsbilder per KI – Prompt-Liste

Ein Bild pro **Grundübung** (181 Stück) – es gilt automatisch für alle Varianten (Langhantel, Kurzhantel, einarmig …).
Stil = das vorhandene Bild `app/src/main/assets/exercises/bankdruecken.jpg` (Meerschweinchen-Bodybuilder, wie das App-Icon).
Erzeugt aus `CATALOG` (Catalog.kt); kommen neue Grundübungen dazu, hier ergänzen.

## Ablauf

1. **Neuen Chat** im Bildgenerator öffnen (z. B. ChatGPT), das Referenzbild `bankdruecken.jpg` hochladen
   und den **Stil-Block** unten senden.
2. Danach je Übung nur noch die Zeile aus dem Kasten senden („Next exercise …“).
3. Bild als PNG/JPG unter dem angegebenen Dateinamen in **einen Ordner** speichern (z. B. `Downloads\uebungsbilder`).
4. Spätestens alle ~10 Bilder einen neuen Chat beginnen (Referenzbild + Stil-Block erneut) – sonst driftet der Stil.
5. Einspielen (PowerShell, im Projektordner):
   ```powershell
   .\tools\import-exercise-images.ps1 -Folder "$env:USERPROFILE\Downloads\uebungsbilder"
   .\tools\import-exercise-images.ps1 -Status     # welche Grundübungen haben noch kein Bild?
   ```
   Danach `node web/tools/gen-data.mjs` (Web-Kopie), bauen, ansehen.

**Prüfen vor dem Speichern:** Stimmt die Bewegung (Griff, Gelenkwinkel, Gerät)? Ist alles im Bild (nichts
abgeschnitten, etwas Rand)? Kein Text im Bild? Sonst neu erzeugen lassen und den Fehler benennen
(„the bar must be on the upper back, not the neck“).

## Stil-Block (einmal pro Chat, mit Referenzbild)

```
I will ask you for a series of fitness exercise illustrations for an app. Use the attached image as the
reference for character and style, and keep them identical in every image:

Character: the same muscular, cute orange-and-white guinea pig bodybuilder with big dark eyes, pink nose,
black headband, black wristbands, black tank top with a white dumbbell logo, black shorts with white side
stripes, bare guinea pig feet.
Style: high-detail 3D render, soft fluffy fur, warm studio lighting from the front, plain dark brown
background with a soft orange glow, dark floor, realistic gym equipment in black and steel.
Composition: square 1:1, the character and all equipment fully visible and centred with some margin on all
sides (the image is also shown as a small rounded icon), one single character, no text, no letters,
no numbers, no extra logos.
Technique: show the exercise with correct, safe form exactly as described (body position, grip, joint
angles), at the most recognisable moment of the movement.

Reply with the image only. The first exercise follows in the next message.
```

## Übungen

### Brust

**001 · Bankdrücken** (Bench Press) → `bankdruecken.png`
```
Next exercise, same character and style. The guinea pig, lying on a flat bench, pressing a loaded barbell up from the chest with a medium grip, feet flat on the floor (front three-quarter view).
```

**002 · Brustpresse** (Chest Press Machine) → `brustpresse.png`
```
Next exercise, same character and style. The guinea pig, sitting in a chest press machine, pushing both handles forward at chest height, back against the pad (side view).
```

**003 · Schrägbankdrücken** (Incline Bench Press) → `schraegbankdruecken.png`
```
Next exercise, same character and style. The guinea pig, lying on an incline bench (about 30 degrees), pressing a barbell up from the upper chest (side three-quarter view).
```

**004 · Negativbankdrücken** (Decline Bench Press) → `negativbankdruecken.png`
```
Next exercise, same character and style. The guinea pig, lying on a decline bench with legs hooked under the pads, head lower than hips, pressing a barbell up (side view).
```

**005 · Floor Press** (Floor Press) → `floor-press.png`
```
Next exercise, same character and style. The guinea pig, lying on the floor with knees bent, pressing a barbell up, upper arms touching the floor at the bottom (side three-quarter view).
```

**006 · Fliegende** (Dumbbell Fly) → `fliegende.png`
```
Next exercise, same character and style. The guinea pig, lying on a flat bench, arms spread wide with slightly bent elbows, a dumbbell in each hand, opening the chest (front three-quarter view).
```

**007 · Schrägbank-Fliegende** (Incline Dumbbell Fly) → `schraegbank-fliegende.png`
```
Next exercise, same character and style. The guinea pig, lying on an incline bench, arms spread wide with slightly bent elbows, a dumbbell in each hand (side three-quarter view).
```

**008 · Butterfly** (Pec Deck / Butterfly Machine) → `butterfly.png`
```
Next exercise, same character and style. The guinea pig, sitting in a pec deck machine, bringing both padded arms together in front of the chest (front view).
```

**009 · Cable Crossover** (Cable Crossover) → `cable-crossover.png`
```
Next exercise, same character and style. The guinea pig, standing between two high cable pulleys, one foot forward, pulling both handles down and together in front of the hips (front view).
```

**010 · Pullover** (Dumbbell Pullover) → `pullover.png`
```
Next exercise, same character and style. The guinea pig, lying across a flat bench with the upper back on the bench, holding one dumbbell with both hands behind the head with slightly bent arms (side view).
```

**011 · Dips** (Chest Dips) → `dips.png`
```
Next exercise, same character and style. The guinea pig, on parallel dip bars, torso leaning forward, arms bent at the bottom of the dip, knees bent and feet crossed (side view).
```

**012 · Liegestütze** (Push-ups) → `liegestuetze.png`
```
Next exercise, same character and style. The guinea pig, in a push-up position on the floor, body in a straight line, chest lowered close to the floor (side view).
```

### Rücken

**013 · Kreuzheben** (Deadlift) → `kreuzheben.png`
```
Next exercise, same character and style. The guinea pig, deadlift: gripping a loaded barbell on the floor, flat back, hips hinged, arms straight, bar close to the shins, starting to lift (side view).
```

**014 · Rudern vorgebeugt** (Barbell Row) → `rudern-vorgebeugt.png`
```
Next exercise, same character and style. The guinea pig, bent-over barbell row: torso hinged forward about 45 degrees, flat back, pulling the barbell to the belly button (side view).
```

**015 · Einarmiges Rudern** (Single-Arm Dumbbell Row) → `einarmiges-rudern.png`
```
Next exercise, same character and style. The guinea pig, one knee and one hand on a flat bench, flat back, pulling a dumbbell up to the hip with the other arm (side view).
```

**016 · T-Bar-Rudern** (T-Bar Row) → `t-bar-rudern.png`
```
Next exercise, same character and style. The guinea pig, standing bent over a landmine T-bar row (barbell anchored at one end, plates on the other), pulling the V-handle to the chest (side view).
```

**017 · Brustgestütztes Rudern** (Chest-Supported Dumbbell Row) → `brustgestuetztes-rudern.png`
```
Next exercise, same character and style. The guinea pig, lying chest-down on an incline bench, pulling two dumbbells up towards the ribs (side view).
```

**018 · Rudern sitzend** (Seated Cable Row) → `rudern-sitzend.png`
```
Next exercise, same character and style. The guinea pig, sitting at a low cable row station, feet on the platform, upright torso, pulling a V-handle to the belly (side view).
```

**019 · Rack Pulls** (Rack Pulls) → `rack-pulls.png`
```
Next exercise, same character and style. The guinea pig, standing in a power rack, lifting a barbell from safety pins set at knee height, flat back (side view).
```

**020 · Good Mornings** (Good Mornings) → `good-mornings.png`
```
Next exercise, same character and style. The guinea pig, standing with a barbell on the upper back, knees slightly bent, hinging forward at the hips with a flat back until the torso is almost parallel to the floor (side view).
```

**021 · Latziehen** (Lat Pulldown) → `latziehen.png`
```
Next exercise, same character and style. The guinea pig, sitting at a lat pulldown machine, thighs under the pads, pulling a wide bar down to the upper chest (front three-quarter view).
```

**022 · Überzüge** (Straight-Arm Pulldown) → `ueberzuege.png`
```
Next exercise, same character and style. The guinea pig, straight-arm pulldown: standing in front of a high cable pulley, slight forward lean, pulling a straight bar down to the thighs with straight arms (side view).
```

**023 · Klimmzüge** (Pull-ups) → `klimmzuege.png`
```
Next exercise, same character and style. The guinea pig, pull-up: hanging from a pull-up bar with an overhand grip, chin above the bar, legs slightly bent (front view).
```

**024 · Australische Klimmzüge** (Inverted Rows) → `australische-klimmzuege.png`
```
Next exercise, same character and style. The guinea pig, inverted row: hanging underneath a bar set at hip height, body straight, heels on the floor, pulling the chest up to the bar (side view).
```

**025 · Hyperextensions** (Back Extensions) → `hyperextensions.png`
```
Next exercise, same character and style. The guinea pig, back extension: on a 45-degree hyperextension bench, hips on the pad, feet locked, body straight, arms crossed over the chest (side view).
```

**026 · Superman** (Superman) → `superman.png`
```
Next exercise, same character and style. The guinea pig, lying face-down on a mat, lifting arms and legs off the floor at the same time, arms stretched forward (side view).
```

**027 · Bird-Dog** (Bird Dog) → `bird-dog.png`
```
Next exercise, same character and style. The guinea pig, on hands and knees on a mat, extending the right arm forward and the left leg back, back flat (side view).
```

### Beine

**028 · Kniebeugen** (Squat) → `kniebeugen.png`
```
Next exercise, same character and style. The guinea pig, back squat: barbell on the upper back, squatting down until the thighs are parallel to the floor, chest up (side view).
```

**029 · Frontkniebeugen** (Front Squat) → `frontkniebeugen.png`
```
Next exercise, same character and style. The guinea pig, front squat: barbell resting on the front of the shoulders, elbows high, squatting deep with an upright torso (side view).
```

**030 · Box Squat** (Box Squat) → `box-squat.png`
```
Next exercise, same character and style. The guinea pig, squatting with a barbell on the back, sitting back onto a low box behind, shins vertical (side view).
```

**031 · Zercher Squat** (Zercher Squat) → `zercher-squat.png`
```
Next exercise, same character and style. The guinea pig, squatting while holding a barbell in the crook of the bent elbows in front of the body (side view).
```

**032 · Sumo Squat** (Dumbbell Sumo Squat) → `sumo-squat.png`
```
Next exercise, same character and style. The guinea pig, very wide stance with toes pointed out, holding one dumbbell with both hands between the legs, squatting down (front view).
```

**033 · Einbeinige Kniebeuge** (Dumbbell Single-Leg Squat) → `einbeinige-kniebeuge.png`
```
Next exercise, same character and style. The guinea pig, single-leg squat on one leg, the other leg stretched forward off the floor, a dumbbell in each hand (side view).
```

**034 · Cossack Squat** (Cossack Squat) → `cossack-squat.png`
```
Next exercise, same character and style. The guinea pig, very wide stance, shifting into a deep squat over one leg while the other leg stays straight with toes pointing up, arms forward for balance (front view).
```

**035 · Goblet Squat** (Goblet Squat) → `goblet-squat.png`
```
Next exercise, same character and style. The guinea pig, holding one dumbbell vertically at the chest with both hands, squatting deep, elbows inside the knees (side three-quarter view).
```

**036 · Hackenschmidt-Kniebeugen** (Hack Squat) → `hackenschmidt-kniebeugen.png`
```
Next exercise, same character and style. The guinea pig, in a hack squat machine: back against the angled back pad, shoulders under the pads, squatting down on the sled (side view).
```

**037 · Wandsitzen** (Wall Sit) → `wandsitzen.png`
```
Next exercise, same character and style. The guinea pig, wall sit: back flat against a wall, thighs parallel to the floor, knees at 90 degrees, arms crossed (side view).
```

**038 · Beinpresse** (Leg Press) → `beinpresse.png`
```
Next exercise, same character and style. The guinea pig, sitting in a 45-degree leg press machine, feet on the platform, knees bent, pushing the platform away (side view).
```

**039 · Rumänisches Kreuzheben** (Romanian Deadlift) → `rumaenisches-kreuzheben.png`
```
Next exercise, same character and style. The guinea pig, Romanian deadlift: holding a barbell, knees slightly bent, hips pushed back, bar lowered to mid-shin along the legs, flat back (side view).
```

**040 · Gestrecktes Kreuzheben** (Stiff-Leg Deadlift) → `gestrecktes-kreuzheben.png`
```
Next exercise, same character and style. The guinea pig, stiff-leg deadlift: holding a barbell, legs almost straight, hinging forward with a flat back, bar near the shins (side view).
```

**041 · Sumo-Kreuzheben** (Sumo Deadlift) → `sumo-kreuzheben.png`
```
Next exercise, same character and style. The guinea pig, sumo deadlift: very wide stance, toes out, hands gripping a barbell inside the knees, lifting it from the floor (front three-quarter view).
```

**042 · Ausfallschritte** (Dumbbell Lunges) → `ausfallschritte.png`
```
Next exercise, same character and style. The guinea pig, forward lunge with a dumbbell in each hand, back knee close to the floor, front knee at 90 degrees (side view).
```

**043 · Ausfallschritte gehend** (Walking Lunges) → `ausfallschritte-gehend.png`
```
Next exercise, same character and style. The guinea pig, walking lunges with a dumbbell in each hand, mid-stride in a deep lunge, moving forward (side view).
```

**044 · Reverse Lunge** (Dumbbell Reverse Lunge) → `reverse-lunge.png`
```
Next exercise, same character and style. The guinea pig, stepping backwards into a lunge with a dumbbell in each hand, back knee close to the floor (side view).
```

**045 · Seitliche Ausfallschritte** (Dumbbell Side Lunges) → `seitliche-ausfallschritte.png`
```
Next exercise, same character and style. The guinea pig, side lunge: stepping wide to one side and bending that knee, the other leg straight, a dumbbell in each hand (front view).
```

**046 · Cross Lunge** (Dumbbell Curtsy Lunge) → `cross-lunge.png`
```
Next exercise, same character and style. The guinea pig, curtsy lunge: stepping one leg diagonally behind the other, bending both knees, a dumbbell in each hand (front view).
```

**047 · Split Squat** (Dumbbell Split Squat) → `split-squat.png`
```
Next exercise, same character and style. The guinea pig, split stance, one foot forward and one back, lowering straight down until the back knee almost touches the floor, a dumbbell in each hand (side view).
```

**048 · Bulgarian Split Squat** (Dumbbell Bulgarian Split Squat) → `bulgarian-split-squat.png`
```
Next exercise, same character and style. The guinea pig, rear foot resting on a flat bench behind, front leg in a deep lunge, a dumbbell in each hand (side view).
```

**049 · Step-Ups** (Dumbbell Step-ups) → `step-ups.png`
```
Next exercise, same character and style. The guinea pig, stepping up onto a plyo box with one foot, a dumbbell in each hand (side view).
```

**050 · Beinstrecken** (Leg Extension) → `beinstrecken.png`
```
Next exercise, same character and style. The guinea pig, sitting in a leg extension machine, extending the legs straight against the padded roller (side view).
```

**051 · Beincurls** (Seated Leg Curl) → `beincurls.png`
```
Next exercise, same character and style. The guinea pig, sitting in a seated leg curl machine, bending the knees and pulling the padded roller down and back (side view).
```

**052 · Nordic Hamstring Curls** (Nordic Hamstring Curls) → `nordic-hamstring-curls.png`
```
Next exercise, same character and style. The guinea pig, kneeling on a mat with the ankles held down by a pad, slowly lowering the straight body forward towards the floor, hands ready to catch (side view).
```

**053 · Glute Ham Raises** (Glute Ham Raises) → `glute-ham-raises.png`
```
Next exercise, same character and style. The guinea pig, on a glute ham developer (GHD), knees on the pad, ankles locked, body raised to vertical by bending the knees (side view).
```

**054 · Adduktion** (Hip Adduction Machine) → `adduktion.png`
```
Next exercise, same character and style. The guinea pig, sitting in a hip adduction machine, pressing the legs together against the inner thigh pads (front view).
```

**055 · Wadenheben stehend** (Standing Calf Raise) → `wadenheben-stehend.png`
```
Next exercise, same character and style. The guinea pig, in a standing calf raise machine: shoulders under the pads, balls of the feet on a step, rising high onto the toes (side view).
```

**056 · Wadenheben sitzend** (Seated Calf Raise) → `wadenheben-sitzend.png`
```
Next exercise, same character and style. The guinea pig, in a seated calf raise machine: knees under the pad, balls of the feet on the platform, raising the heels (side view).
```

**057 · Wadenheben an der Beinpresse** (Leg Press Calf Raise) → `wadenheben-an-der-beinpresse.png`
```
Next exercise, same character and style. The guinea pig, sitting in a leg press with straight legs, only the balls of the feet on the bottom edge of the platform, pushing with the toes (side view).
```

**058 · Donkey Calf Raises** (Donkey Calf Raise Machine) → `donkey-calf-raises.png`
```
Next exercise, same character and style. The guinea pig, in a donkey calf raise machine: bent forward at the hips, hips under the pad, balls of the feet on a block, rising onto the toes (side view).
```

**059 · Zehenheben** (Toe Raises) → `zehenheben.png`
```
Next exercise, same character and style. The guinea pig, standing, lifting the toes and front of the feet off the floor, balancing on the heels (side view).
```

### Gluteus

**060 · Hip Thrust** (Barbell Hip Thrust) → `hip-thrust.png`
```
Next exercise, same character and style. The guinea pig, upper back resting on a flat bench, a padded barbell over the hips, driving the hips up until the body is a straight line from shoulders to knees (side view).
```

**061 · Beckenheben** (Glute Bridge) → `beckenheben.png`
```
Next exercise, same character and style. The guinea pig, glute bridge: lying on the back on a mat, knees bent, feet flat, lifting the hips up (side view).
```

**062 · Frog Pumps** (Frog Pumps) → `frog-pumps.png`
```
Next exercise, same character and style. The guinea pig, lying on the back on a mat, soles of the feet pressed together, knees falling out to the sides, lifting the hips (front three-quarter view).
```

**063 · Abduktion** (Hip Abduction Machine) → `abduktion.png`
```
Next exercise, same character and style. The guinea pig, sitting in a hip abduction machine, pushing the knees apart against the outer thigh pads (front view).
```

**064 · Beinspreizen** (Lying Leg Abduction) → `beinspreizen.png`
```
Next exercise, same character and style. The guinea pig, lying on the side on a mat, head resting on the lower arm, lifting the top leg straight up (front view).
```

**065 · Seitliches Beinheben** (Side-Lying Leg Raise) → `seitliches-beinheben.png`
```
Next exercise, same character and style. The guinea pig, lying on the side on a mat, propped on one elbow, lifting the straight top leg sideways (front view).
```

**066 · Clamshell** (Clamshell) → `clamshell.png`
```
Next exercise, same character and style. The guinea pig, lying on the side with knees bent, a mini resistance band around the thighs, opening the top knee like a clamshell, feet together (front view).
```

**067 · Seitliches Gehen** (Lateral Band Walk) → `seitliches-gehen.png`
```
Next exercise, same character and style. The guinea pig, lateral band walk: a mini resistance band around the legs above the knees, half squat, stepping sideways (front view).
```

**068 · Monster Walk** (Monster Walk) → `monster-walk.png`
```
Next exercise, same character and style. The guinea pig, a resistance band around the ankles, half squat, walking forward with wide diagonal steps (front three-quarter view).
```

**069 · Feuerhydrant** (Fire Hydrants) → `feuerhydrant.png`
```
Next exercise, same character and style. The guinea pig, fire hydrant: on hands and knees on a mat, lifting one bent knee out to the side (back three-quarter view).
```

**070 · Donkey Kicks** (Donkey Kicks) → `donkey-kicks.png`
```
Next exercise, same character and style. The guinea pig, on hands and knees on a mat, kicking one bent leg up and back with the sole facing the ceiling (side view).
```

**071 · Po-Kickbacks** (Cable Glute Kickbacks) → `po-kickbacks.png`
```
Next exercise, same character and style. The guinea pig, standing facing a low cable pulley with an ankle strap, slight forward lean holding the machine, kicking one leg straight back (side view).
```

**072 · Pull-Through** (Cable Pull-Through) → `pull-through.png`
```
Next exercise, same character and style. The guinea pig, standing with the back to a low cable pulley, rope between the legs, hips hinged back, about to drive them forward (side view).
```

**073 · Reverse Hyperextensions** (Reverse Hyperextension Machine) → `reverse-hyperextensions.png`
```
Next exercise, same character and style. The guinea pig, lying face-down on a reverse hyperextension machine, upper body on the pad, swinging straight legs up behind to hip height (side view).
```

### Schultern

**074 · Schulterdrücken** (Seated Dumbbell Shoulder Press) → `schulterdruecken.png`
```
Next exercise, same character and style. The guinea pig, sitting on an upright bench, pressing two dumbbells overhead from shoulder height (front view).
```

**075 · Military Press** (Military Press) → `military-press.png`
```
Next exercise, same character and style. The guinea pig, standing, pressing a barbell from the front of the shoulders straight overhead, core tight (side three-quarter view).
```

**076 · Arnold-Press** (Arnold Press) → `arnold-press.png`
```
Next exercise, same character and style. The guinea pig, sitting on an upright bench, rotating the palms outward while pressing two dumbbells overhead (front view).
```

**077 · Handstand Push-Up** (Handstand Push-up) → `handstand-push-up.png`
```
Next exercise, same character and style. The guinea pig, in a handstand against a wall, head lowered close to the floor, hands shoulder-width apart (side view).
```

**078 · Pike Push-Up** (Pike Push-ups) → `pike-push-up.png`
```
Next exercise, same character and style. The guinea pig, pike push-up: hips high in an inverted V, hands and feet on the floor, lowering the head towards the floor (side view).
```

**079 · Seitheben** (Lateral Raise) → `seitheben.png`
```
Next exercise, same character and style. The guinea pig, standing, raising two dumbbells out to the sides to shoulder height, elbows slightly bent (front view).
```

**080 · Frontheben** (Front Raise) → `frontheben.png`
```
Next exercise, same character and style. The guinea pig, standing, raising a dumbbell in each hand straight forward to shoulder height (side view).
```

**081 · Vorgebeugtes Seitheben** (Bent-Over Reverse Fly) → `vorgebeugtes-seitheben.png`
```
Next exercise, same character and style. The guinea pig, bent over at the hips with a flat back, raising two dumbbells out to the sides (front three-quarter view).
```

**082 · Butterfly Reverse** (Reverse Pec Deck) → `butterfly-reverse.png`
```
Next exercise, same character and style. The guinea pig, sitting facing a reverse pec deck machine, chest against the pad, pulling the handles back and apart with straight arms (back three-quarter view).
```

**083 · Face Pulls** (Face Pulls) → `face-pulls.png`
```
Next exercise, same character and style. The guinea pig, standing in front of a cable pulley at face height, pulling a rope towards the face, elbows high and wide (side three-quarter view).
```

**084 · Band Pull-Aparts** (Band Pull-Aparts) → `band-pull-aparts.png`
```
Next exercise, same character and style. The guinea pig, standing, holding a resistance band in front of the chest with straight arms, pulling it apart to the sides (front view).
```

**085 · Aufrechtes Rudern** (Upright Row) → `aufrechtes-rudern.png`
```
Next exercise, same character and style. The guinea pig, standing, pulling a barbell with a narrow grip up along the body to chest height, elbows pointing up and out (front view).
```

**086 · Shrugs** (Barbell Shrugs) → `shrugs.png`
```
Next exercise, same character and style. The guinea pig, standing, holding a heavy barbell in front of the thighs with straight arms, shrugging the shoulders up towards the ears (front view).
```

### Bizeps

**087 · Bizeps-Curls** (Dumbbell Curl) → `bizeps-curls.png`
```
Next exercise, same character and style. The guinea pig, standing, curling two dumbbells up with palms facing up, elbows close to the body (front view).
```

**088 · Hammer Curls** (Hammer Curls) → `hammer-curls.png`
```
Next exercise, same character and style. The guinea pig, standing, curling two dumbbells with palms facing each other (neutral grip), elbows at the sides (front three-quarter view).
```

**089 · Konzentrationscurls** (Concentration Curls) → `konzentrationscurls.png`
```
Next exercise, same character and style. The guinea pig, sitting on a bench, elbow braced against the inner thigh, curling one dumbbell up (front three-quarter view).
```

**090 · Schrägbank-Curls** (Incline Dumbbell Curl) → `schraegbank-curls.png`
```
Next exercise, same character and style. The guinea pig, sitting back on an incline bench, arms hanging straight down behind the body, curling two dumbbells (side view).
```

**091 · Scott-Curls** (Barbell Preacher Curl) → `scott-curls.png`
```
Next exercise, same character and style. The guinea pig, preacher curl: sitting at a preacher bench, upper arms on the angled pad, curling a barbell up (side view).
```

### Unterarm

**092 · Reverse Curls** (Reverse EZ-Bar Curl) → `reverse-curls.png`
```
Next exercise, same character and style. The guinea pig, standing, curling an EZ curl bar with an overhand grip, palms facing down (front view).
```

**093 · Unterarm-Curls** (Barbell Wrist Curl) → `unterarm-curls.png`
```
Next exercise, same character and style. The guinea pig, wrist curl: sitting on a bench, forearms resting on the thighs, palms up, curling a barbell with the wrists only (side view).
```

**094 · Reverse Unterarm-Curls** (Barbell Reverse Wrist Curl) → `reverse-unterarm-curls.png`
```
Next exercise, same character and style. The guinea pig, reverse wrist curl: sitting on a bench, forearms on the thighs, palms down, lifting a barbell with the wrists only (side view).
```

**095 · Finger-Curls** (Barbell Finger Curls) → `finger-curls.png`
```
Next exercise, same character and style. The guinea pig, sitting on a bench, forearms on the thighs, palms up, letting a barbell roll down to the fingertips and curling it back into the hand (close side view).
```

**096 · Wrist Roller** (Wrist Roller) → `wrist-roller.png`
```
Next exercise, same character and style. The guinea pig, standing with straight arms forward, holding a wrist roller handle, rolling up a rope with a weight plate hanging from it (front three-quarter view).
```

### Trizeps

**097 · Trizepsdrücken** (Triceps Pushdown) → `trizepsdruecken.png`
```
Next exercise, same character and style. The guinea pig, standing at a high cable pulley, elbows pinned at the sides, pushing a straight bar down until the arms are straight (side view).
```

**098 · Trizepsdrücken über Kopf** (Overhead Triceps Extension) → `trizepsdruecken-ueber-kopf.png`
```
Next exercise, same character and style. The guinea pig, sitting upright, holding one dumbbell with both hands behind the head, extending the arms overhead (side view).
```

**099 · Trizeps-Kickbacks** (Triceps Kickbacks) → `trizeps-kickbacks.png`
```
Next exercise, same character and style. The guinea pig, bent over with one knee on a bench, upper arm parallel to the torso, extending a dumbbell straight back (side view).
```

**100 · French Press** (Skull Crushers) → `french-press.png`
```
Next exercise, same character and style. The guinea pig, skull crusher: lying on a flat bench, lowering an EZ curl bar towards the forehead, upper arms vertical (side view).
```

**101 · Enges Bankdrücken** (Close-Grip Bench Press) → `enges-bankdruecken.png`
```
Next exercise, same character and style. The guinea pig, lying on a flat bench, pressing a barbell with a narrow shoulder-width grip, elbows close to the body (front three-quarter view).
```

**102 · Trizeps-Dips** (Triceps Dips) → `trizeps-dips.png`
```
Next exercise, same character and style. The guinea pig, on parallel dip bars with an upright torso, arms bending at the elbows, legs straight down (side view).
```

**103 · Arnold Dips** (Arnold Dips) → `arnold-dips.png`
```
Next exercise, same character and style. The guinea pig, on parallel dip bars, upright torso, lowering only a short way with the elbows bent behind the body (side view).
```

**104 · Bankdips** (Bench Dips) → `bankdips.png`
```
Next exercise, same character and style. The guinea pig, bench dips: hands on the edge of a flat bench behind the body, legs straight forward with heels on the floor, lowering the hips (side view).
```

**105 · Enge Liegestütze** (Close-Grip Push-ups) → `enge-liegestuetze.png`
```
Next exercise, same character and style. The guinea pig, push-up position with the hands close together under the chest, elbows close to the body, lowered towards the floor (side view).
```

### Bauch

**106 · Crunches** (Crunches) → `crunches.png`
```
Next exercise, same character and style. The guinea pig, lying on the back on a mat, knees bent, hands at the temples, curling the shoulders off the floor (side view).
```

**107 · Butterfly-Crunches** (Butterfly Crunches) → `butterfly-crunches.png`
```
Next exercise, same character and style. The guinea pig, lying on the back on a mat, soles of the feet together and knees falling outward, curling the upper body up (front three-quarter view).
```

**108 · Reverse Crunches** (Reverse Crunches) → `reverse-crunches.png`
```
Next exercise, same character and style. The guinea pig, lying on the back on a mat, arms at the sides, pulling the bent knees towards the chest and lifting the hips (side view).
```

**109 · Side Crunches** (Side Crunches) → `side-crunches.png`
```
Next exercise, same character and style. The guinea pig, lying on the side on a mat with bent knees, hand behind the head, crunching the upper body sideways (front view).
```

**110 · V-Crunches** (V-Crunches) → `v-crunches.png`
```
Next exercise, same character and style. The guinea pig, sitting balanced on the buttocks on a mat, pulling the bent knees and upper body together into a V (side view).
```

**111 · Bicycle Crunches** (Bicycle Crunches) → `bicycle-crunches.png`
```
Next exercise, same character and style. The guinea pig, lying on the back on a mat, hands at the temples, bringing the right elbow to the left knee while the other leg is extended (front three-quarter view).
```

**112 · Heel-Touches** (Heel Touches) → `heel-touches.png`
```
Next exercise, same character and style. The guinea pig, lying on the back on a mat with knees bent, shoulders slightly lifted, reaching sideways to touch one heel with the hand (front view).
```

**113 · Toe-Touches** (Toe Touches) → `toe-touches.png`
```
Next exercise, same character and style. The guinea pig, lying on the back on a mat, legs pointing straight up, reaching up with both hands to touch the toes (side view).
```

**114 · Side Plank Crunches** (Side Plank Crunches) → `side-plank-crunches.png`
```
Next exercise, same character and style. The guinea pig, in a side plank on one forearm, top hand behind the head, crunching the top elbow towards the top knee (front view).
```

**115 · Klappmesser** (Jackknife Sit-ups) → `klappmesser.png`
```
Next exercise, same character and style. The guinea pig, jackknife sit-up: lying on the back on a mat, lifting straight arms and straight legs at the same time to meet above the hips (side view).
```

**116 · V-Ups** (V-Ups) → `v-ups.png`
```
Next exercise, same character and style. The guinea pig, V-up: balancing on the buttocks on a mat, straight legs and straight upper body forming a V, hands reaching for the toes (side view).
```

**117 · Sit-Ups** (Sit-ups) → `sit-ups.png`
```
Next exercise, same character and style. The guinea pig, lying on the back on a mat with knees bent, sitting all the way up, arms crossed over the chest (side view).
```

**118 · Unterarmstütz / Plank** (Plank) → `unterarmstuetz-plank.png`
```
Next exercise, same character and style. The guinea pig, forearm plank on a mat, body in a straight line from head to heels (side view).
```

**119 · Seitstütz** (Side Plank) → `seitstuetz.png`
```
Next exercise, same character and style. The guinea pig, side plank on one forearm on a mat, body straight, top hand on the hip (front view).
```

**120 · Spiderman Plank** (Spiderman Plank) → `spiderman-plank.png`
```
Next exercise, same character and style. The guinea pig, in a forearm plank, bringing one knee out to the side towards the elbow (side three-quarter view).
```

**121 · Seitliches Hüftheben** (Side Plank Hip Raises) → `seitliches-hueftheben.png`
```
Next exercise, same character and style. The guinea pig, in a side plank on one forearm, lowering and raising the hips (front view).
```

**122 · Hüftheben** (Hip Raises) → `hueftheben.png`
```
Next exercise, same character and style. The guinea pig, lying on the back on a mat, legs pointing straight up, lifting the hips off the floor towards the ceiling (side view).
```

**123 · Beinheben liegend** (Lying Leg Raises) → `beinheben-liegend.png`
```
Next exercise, same character and style. The guinea pig, lying on the back on a mat, hands under the hips, raising straight legs up to vertical (side view).
```

**124 · Knieheben im Liegen** (Lying Knee Raises) → `knieheben-im-liegen.png`
```
Next exercise, same character and style. The guinea pig, lying on the back on a mat, pulling bent knees towards the chest (side view).
```

**125 · Flutter Kicks** (Flutter Kicks) → `flutter-kicks.png`
```
Next exercise, same character and style. The guinea pig, lying on the back on a mat, legs straight and slightly raised, kicking alternately up and down in small movements (side view).
```

**126 · Beinschere** (Scissor Kicks) → `beinschere.png`
```
Next exercise, same character and style. The guinea pig, lying on the back on a mat, straight legs raised slightly, crossing them over each other like scissors (front three-quarter view).
```

**127 · Beinheben hängend** (Hanging Leg Raises) → `beinheben-haengend.png`
```
Next exercise, same character and style. The guinea pig, hanging from a pull-up bar with straight arms, raising straight legs to hip height (side view).
```

**128 · Knieheben am Gerät** (Captain's Chair Knee Raises) → `knieheben-am-geraet.png`
```
Next exercise, same character and style. The guinea pig, in a captain's chair: forearms on the padded arm rests, back against the pad, raising bent knees towards the chest (side view).
```

**129 · Mountain Climbers** (Mountain Climbers) → `mountain-climbers.png`
```
Next exercise, same character and style. The guinea pig, in a high plank on the hands, driving one knee quickly towards the chest, running motion (side view).
```

**130 · Dead Bug** (Dead Bug) → `dead-bug.png`
```
Next exercise, same character and style. The guinea pig, lying on the back on a mat, arms pointing up, knees bent at 90 degrees, stretching the right arm back and the left leg forward (side view).
```

**131 · Bauchroller** (Ab Wheel Rollout) → `bauchroller.png`
```
Next exercise, same character and style. The guinea pig, kneeling on a mat, rolling an ab wheel forward with straight arms, body extended (side view).
```

**132 · Russian Twist** (Russian Twists) → `russian-twist.png`
```
Next exercise, same character and style. The guinea pig, sitting on a mat leaning back, feet lifted, rotating the upper body to one side with clasped hands (front view).
```

**133 · Hüftrollen** (Hip Rolls) → `hueftrollen.png`
```
Next exercise, same character and style. The guinea pig, lying on the back on a mat, arms spread out, bent knees together lowered to one side (front three-quarter view).
```

**134 · Rumpfrotation** (Cable Woodchopper) → `rumpfrotation.png`
```
Next exercise, same character and style. The guinea pig, cable woodchopper: standing sideways to a high cable pulley, pulling the handle diagonally down across the body with both hands, rotating the torso (front view).
```

**135 · Seitliches Oberkörperbeugen** (Dumbbell Side Bend) → `seitliches-oberkoerperbeugen.png`
```
Next exercise, same character and style. The guinea pig, standing, holding a dumbbell in one hand, bending the upper body sideways towards that side (front view).
```

**136 · Pallof Press** (Pallof Press) → `pallof-press.png`
```
Next exercise, same character and style. The guinea pig, standing sideways to a cable pulley at chest height, pressing the handle straight forward from the chest and resisting the rotation (front three-quarter view).
```

### Ganzkörper

**137 · Swing** (Kettlebell Swing) → `swing.png`
```
Next exercise, same character and style. The guinea pig, kettlebell swing: swinging a kettlebell up to chest height with straight arms, hips fully extended (side view).
```

**138 · Turkish Get-up** (Turkish Get-up) → `turkish-get-up.png`
```
Next exercise, same character and style. The guinea pig, half-way through a Turkish get-up: kneeling on one knee, holding a kettlebell straight overhead with one locked arm (front three-quarter view).
```

**139 · Clean & Press** (Kettlebell Clean & Press) → `clean-press.png`
```
Next exercise, same character and style. The guinea pig, pressing one kettlebell from the shoulder straight overhead, standing tall (front three-quarter view).
```

**140 · Thruster** (Thruster) → `thruster.png`
```
Next exercise, same character and style. The guinea pig, coming out of a front squat with a barbell on the shoulders and pressing it overhead in one movement (side view).
```

**141 · Umsetzen / Power Clean** (Power Clean) → `umsetzen-power-clean.png`
```
Next exercise, same character and style. The guinea pig, power clean: catching a barbell on the front of the shoulders in a quarter squat, elbows high (side view).
```

**142 · Reißen / Snatch** (Snatch) → `reissen-snatch.png`
```
Next exercise, same character and style. The guinea pig, snatch: catching a barbell overhead with a wide grip and locked arms in a deep squat (front three-quarter view).
```

**143 · Farmer's Walk** (Farmer's Walk) → `farmer-s-walk.png`
```
Next exercise, same character and style. The guinea pig, walking upright, carrying a heavy dumbbell in each hand at the sides (side view).
```

**144 · Burpees** (Burpees) → `burpees.png`
```
Next exercise, same character and style. The guinea pig, burpee: jumping up with arms overhead, feet off the floor (front three-quarter view).
```

**145 · Box Jumps** (Box Jumps) → `box-jumps.png`
```
Next exercise, same character and style. The guinea pig, jumping onto a tall plyo box, landing in a half squat on top (side view).
```

**146 · Hampelmann** (Jumping Jacks) → `hampelmann.png`
```
Next exercise, same character and style. The guinea pig, jumping jack: jumping with legs spread and arms raised above the head in an X shape (front view).
```

### Cardio

**147 · Laufband** (Treadmill) → `laufband.png`
```
Next exercise, same character and style. The guinea pig, running on a treadmill (side view).
```

**148 · Laufband Gehen mit Steigung** (Incline Treadmill Walk) → `laufband-gehen-mit-steigung.png`
```
Next exercise, same character and style. The guinea pig, walking uphill on a treadmill with a steep incline (side view).
```

**149 · Crosstrainer** (Elliptical) → `crosstrainer.png`
```
Next exercise, same character and style. The guinea pig, working out on an elliptical cross trainer, holding the moving handles (side view).
```

**150 · Fahrradergometer** (Stationary Bike) → `fahrradergometer.png`
```
Next exercise, same character and style. The guinea pig, riding an upright stationary exercise bike (side view).
```

**151 · Spinning** (Spinning) → `spinning.png`
```
Next exercise, same character and style. The guinea pig, riding an indoor spinning bike out of the saddle, leaning forward (side view).
```

**152 · Air Bike** (Air Bike) → `air-bike.png`
```
Next exercise, same character and style. The guinea pig, riding an air bike (fan bike with moving handles and a large front fan) (side view).
```

**153 · Rudergerät** (Rowing Machine) → `rudergeraet.png`
```
Next exercise, same character and style. The guinea pig, rowing on an indoor rowing machine at the finish of the stroke, legs straight, handle pulled to the chest (side view).
```

**154 · Ski-Ergometer** (Ski Erg) → `ski-ergometer.png`
```
Next exercise, same character and style. The guinea pig, standing at a ski erg machine, pulling both handles down in a double-pole motion, slight crouch (side view).
```

**155 · Stepper** (Stair Stepper) → `stepper.png`
```
Next exercise, same character and style. The guinea pig, climbing on a stair stepper machine (side view).
```

**156 · Laufen (draußen)** (Running (outdoor)) → `laufen-draussen.png`
```
Next exercise, same character and style. The guinea pig, running in running shoes, mid-stride, only a hint of a path on the floor (side view).
```

**157 · Gehen** (Walking) → `gehen.png`
```
Next exercise, same character and style. The guinea pig, walking briskly with swinging arms (side view).
```

**158 · Wandern** (Hiking) → `wandern.png`
```
Next exercise, same character and style. The guinea pig, hiking with a small backpack and trekking poles, stepping up onto a rock (side view).
```

**159 · Radfahren (draußen)** (Cycling (outdoor)) → `radfahren-draussen.png`
```
Next exercise, same character and style. The guinea pig, riding a road bicycle wearing a helmet (side view).
```

**160 · Schwimmen** (Swimming) → `schwimmen.png`
```
Next exercise, same character and style. The guinea pig, swimming freestyle (front crawl) in water, wearing swimming goggles instead of the headband and no tank top, water splashing (three-quarter view).
```

**161 · Seilspringen** (Jump Rope) → `seilspringen.png`
```
Next exercise, same character and style. The guinea pig, jumping rope, feet just off the floor, rope passing under the feet (front view).
```

**162 · HIIT** (HIIT) → `hiit.png`
```
Next exercise, same character and style. The guinea pig, explosive jump squat with arms swinging, sweat drops, a stopwatch on the floor (front three-quarter view).
```

**163 · Boxen (Sandsack)** (Boxing (heavy bag)) → `boxen-sandsack.png`
```
Next exercise, same character and style. The guinea pig, wearing boxing gloves, punching a hanging heavy bag (side three-quarter view).
```

**164 · Battle Ropes** (Battle Ropes) → `battle-ropes.png`
```
Next exercise, same character and style. The guinea pig, half squat, whipping two heavy battle ropes in waves with both arms (front three-quarter view).
```

### Ergänzt (11/2026)

**165 · Renegade Row** (Renegade Row) → `renegade-row.png`
```
Next exercise, same character and style. The guinea pig, in a high plank with each hand on a hex dumbbell, rowing one dumbbell up to the hip while the other arm supports the body, hips level (front three-quarter view).
```

**166 · Jump Squat** (Jump Squats) → `jump-squat.png`
```
Next exercise, same character and style. The guinea pig, jumping straight up out of a squat, feet just off the floor, arms swung up overhead, no equipment (side view).
```

**167 · Windmühle** (Kettlebell Windmill) → `windmuehle.png`
```
Next exercise, same character and style. The guinea pig, holding a kettlebell overhead with one locked arm, hips pushed out to that side, torso bent sideways so the free hand slides down the front leg toward the foot, eyes on the kettlebell (front view).
```

**168 · Wall Balls** (Wall Balls) → `wall-balls.png`
```
Next exercise, same character and style. The guinea pig, standing up out of a deep squat in front of a wall, throwing a soft medicine ball up toward a target high on the wall, arms extended (side view).
```

**169 · Ball Slams** (Medicine Ball Slams) → `ball-slams.png`
```
Next exercise, same character and style. The guinea pig, slamming a heavy black slam ball down onto the floor in front of the feet with both arms, hips and knees bent, the ball just hitting the floor (side view).
```

**170 · McGill Curl-up** (McGill Curl-up) → `mcgill-curl-up.png`
```
Next exercise, same character and style. The guinea pig, lying on its back on a mat, one leg straight and one knee bent, hands under the lower back, lifting only the head and shoulders a few centimetres off the floor (side view).
```

**171 · Scheibenwischer** (Windshield Wipers) → `scheibenwischer.png`
```
Next exercise, same character and style. The guinea pig, lying on its back on a mat with arms stretched out to the sides, both straight legs raised and lowered toward one side, shoulders flat on the floor (front three-quarter view from above).
```

**172 · Bärengang** (Bear Crawl) → `baerengang.png`
```
Next exercise, same character and style. The guinea pig, crawling on hands and feet with the knees hovering just above the floor, back flat like a table, right hand and left foot stepping forward (side view).
```

**173 · Raupengang** (Inchworm) → `raupengang.png`
```
Next exercise, same character and style. The guinea pig, bent forward with nearly straight legs, hands on the floor walking forward toward a high plank position (side view).
```

**174 · Skater Jumps** (Skater Jumps) → `skater-jumps.png`
```
Next exercise, same character and style. The guinea pig, landing on one leg after a sideways jump like a speed skater, standing knee bent, free leg sweeping behind, arms swinging across the body (front view).
```

**175 · Tuck Jumps** (Tuck Jumps) → `tuck-jumps.png`
```
Next exercise, same character and style. The guinea pig, in mid-air at the top of a jump with both knees pulled up toward the chest, arms forward (side view).
```

**176 · Broad Jumps** (Broad Jumps) → `broad-jumps.png`
```
Next exercise, same character and style. The guinea pig, flying forward in a long standing jump, arms swung forward, legs reaching ahead for the landing (side view).
```

**177 · V-Sitz** (V-Sit Hold) → `v-sitz.png`
```
Next exercise, same character and style. The guinea pig, balancing on its bottom on a mat, straight legs and upper body raised so they form a V, arms reaching forward (side view).
```

**178 · Handstand** (Handstand Hold) → `handstand.png`
```
Next exercise, same character and style. The guinea pig, holding a straight handstand on the floor, arms locked, body in one vertical line, feet pointing up (side view).
```

**179 · Krähe** (Crow Pose) → `kraehe.png`
```
Next exercise, same character and style. The guinea pig, balancing on both hands on a mat in crow pose, elbows slightly bent, knees resting on the upper arms, feet lifted off the floor (side view).
```

**180 · High Knees** (High Knees) → `high-knees.png`
```
Next exercise, same character and style. The guinea pig, running in place, one knee driven up to hip height, arms swinging like a runner, on the balls of the feet (side view).
```

**181 · Anfersen** (Butt Kicks) → `anfersen.png`
```
Next exercise, same character and style. The guinea pig, running in place, one heel kicked up to touch its bottom, thigh pointing down, arms bent like a runner (side view).
```
