package com.example.gymtracker

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/*
 * Piktogramme pro Bewegungsmuster.
 * Jede Katalog-Übung ist einer Bewegung zugeordnet (MOTION_OF); eigene Übungen und
 * unbekannte Namen bekommen die Standard-Bewegung ihrer Muskelgruppe.
 *
 * Gezeichnet wird in einem 0..1-Koordinatensystem (x nach rechts, y nach unten),
 * das auf die Icon-Größe skaliert wird. Seitenansichten blicken nach links.
 */

enum class Motion {
    // Brust
    BENCH_PRESS, INCLINE_PRESS, CHEST_PRESS, FLY, PUSH_UP, DIP,
    // Rücken
    PULL_UP, PULLDOWN, ROW_BENT, ROW_SEATED, DEADLIFT, BACK_EXTENSION,
    // Beine
    SQUAT, LUNGE, LEG_PRESS, LEG_EXTENSION, LEG_CURL, CALF_RAISE, HIP_THRUST, ABDUCTION, KICKBACK,
    // Schultern
    OVERHEAD_PRESS, LATERAL_RAISE, FRONT_RAISE, SHRUG, FACE_PULL,
    // Arme
    CURL, PUSHDOWN, OVERHEAD_EXTENSION,
    // Bauch
    CRUNCH, PLANK, HANGING_RAISE, TWIST,
    // Ganzkörper
    KETTLEBELL_SWING, JUMP, CARRY, OLYMPIC,
    // Cardio
    RUN, WALK, BIKE, ROWER, SWIM, JUMP_ROPE, STAIRS, BOXING, BATTLE_ROPES,
    // Sonstige
    DUMBBELL
}

/** Standard-Bewegung je Muskelgruppe (für eigene Übungen und Kategorie-Symbole). */
fun MuscleGroup.defaultMotion(): Motion = when (this) {
    MuscleGroup.BRUST -> Motion.BENCH_PRESS
    MuscleGroup.RUECKEN -> Motion.PULL_UP
    MuscleGroup.BEINE -> Motion.SQUAT
    MuscleGroup.GLUTEUS -> Motion.HIP_THRUST
    MuscleGroup.SCHULTERN -> Motion.OVERHEAD_PRESS
    MuscleGroup.BIZEPS -> Motion.CURL
    MuscleGroup.TRIZEPS -> Motion.PUSHDOWN
    MuscleGroup.UNTERARM -> Motion.CURL
    MuscleGroup.BAUCH -> Motion.CRUNCH
    MuscleGroup.GANZKOERPER -> Motion.JUMP
    MuscleGroup.CARDIO -> Motion.RUN
    MuscleGroup.SONSTIGE -> Motion.DUMBBELL
}

/** Zuordnung Katalog-Übung → Bewegung. Schlüssel = deutscher Katalog-Name. */
val MOTION_OF: Map<String, Motion> = mapOf(
    // Brust
    "Bankdrücken (Langhantel)" to Motion.BENCH_PRESS,
    "Bankdrücken (Kurzhantel, einarmig)" to Motion.BENCH_PRESS,
    "Schrägbankdrücken (Langhantel)" to Motion.INCLINE_PRESS,
    "Schrägbankdrücken (Kurzhantel, einarmig)" to Motion.INCLINE_PRESS,
    "Negativbankdrücken (Langhantel)" to Motion.BENCH_PRESS,
    "Floor Press (Langhantel)" to Motion.BENCH_PRESS,
    "Bankdrücken (Kurzhantel)" to Motion.BENCH_PRESS,
    "Schrägbankdrücken (Kurzhantel)" to Motion.INCLINE_PRESS,
    "Fliegende (Kurzhantel)" to Motion.FLY,
    "Fliegende (Kabelzug, einarmig)" to Motion.FLY,
    "Schrägbank-Fliegende (Kurzhantel)" to Motion.FLY,
    "Pullover (Kurzhantel)" to Motion.BENCH_PRESS,
    "Fliegende (Maschine / Butterfly)" to Motion.FLY,
    "Bankdrücken (Maschine)" to Motion.CHEST_PRESS,
    "Schrägbankdrücken (Maschine)" to Motion.CHEST_PRESS,
    "Bankdrücken (Multipresse)" to Motion.BENCH_PRESS,
    "Fliegende (Kabelzug / Crossover)" to Motion.FLY,
    "Fliegende von unten (Kabelzug)" to Motion.FLY,
    "Dips (Körpergewicht)" to Motion.DIP,
    "Liegestütze (Körpergewicht)" to Motion.PUSH_UP,
    "Schräge Liegestütze (Körpergewicht)" to Motion.PUSH_UP,
    // Rücken
    "Kreuzheben (Langhantel)" to Motion.DEADLIFT,
    "Rudern vorgebeugt (Langhantel)" to Motion.ROW_BENT,
    "T-Bar-Rudern (Langhantel)" to Motion.ROW_BENT,
    "Rack Pulls (Langhantel)" to Motion.DEADLIFT,
    "Good Mornings (Langhantel)" to Motion.DEADLIFT,
    "Einarmiges Rudern (Kurzhantel)" to Motion.ROW_BENT,
    "Brustgestütztes Rudern (Kurzhantel)" to Motion.ROW_BENT,
    "Brustgestütztes Rudern (Maschine, einarmig)" to Motion.ROW_BENT,
    "Latziehen (Kabelzug)" to Motion.PULLDOWN,
    "Latziehen (Kabelzug, einarmig)" to Motion.PULLDOWN,
    "Latziehen eng (Kabelzug)" to Motion.PULLDOWN,
    "Rudern sitzend (Kabelzug)" to Motion.ROW_SEATED,
    "Rudern sitzend (Kabelzug, einarmig)" to Motion.ROW_SEATED,
    "Überzüge (Kabelzug)" to Motion.PUSHDOWN,
    "Brustgestütztes Rudern (Maschine)" to Motion.ROW_SEATED,
    "Klimmzüge (Maschine / unterstützt)" to Motion.PULL_UP,
    "Klimmzüge (Körpergewicht)" to Motion.PULL_UP,
    "Klimmzüge Untergriff (Körpergewicht)" to Motion.PULL_UP,
    "Australische Klimmzüge (Körpergewicht)" to Motion.ROW_SEATED,
    "Hyperextensions (Körpergewicht)" to Motion.BACK_EXTENSION,
    // Beine & Gesäß
    "Kniebeugen (Langhantel)" to Motion.SQUAT,
    "Kniebeugen (Körpergewicht, einbeinig)" to Motion.SQUAT,
    "Frontkniebeugen (Langhantel)" to Motion.SQUAT,
    "Rumänisches Kreuzheben (Langhantel)" to Motion.DEADLIFT,
    "Rumänisches Kreuzheben (Kurzhantel, einbeinig)" to Motion.DEADLIFT,
    "Sumo-Kreuzheben (Langhantel)" to Motion.DEADLIFT,
    "Hip Thrust (Langhantel)" to Motion.HIP_THRUST,
    "Hip Thrust (Körpergewicht, einbeinig)" to Motion.HIP_THRUST,
    "Ausfallschritte (Kurzhantel)" to Motion.LUNGE,
    "Ausfallschritte gehend (Kurzhantel)" to Motion.LUNGE,
    "Bulgarische Kniebeugen (Kurzhantel)" to Motion.LUNGE,
    "Goblet Squat (Kurzhantel)" to Motion.SQUAT,
    "Step-ups (Kurzhantel)" to Motion.STAIRS,
    "Beinpresse (Maschine)" to Motion.LEG_PRESS,
    "Beinpresse (Maschine, einbeinig)" to Motion.LEG_PRESS,
    "Hackenschmidt-Kniebeugen (Maschine)" to Motion.SQUAT,
    "Kniebeugen (Multipresse)" to Motion.SQUAT,
    "Beinstrecker (Maschine)" to Motion.LEG_EXTENSION,
    "Beinstrecker (Maschine, einbeinig)" to Motion.LEG_EXTENSION,
    "Beinbeuger (Maschine)" to Motion.LEG_CURL,
    "Beinbeuger (Maschine, einbeinig)" to Motion.LEG_CURL,
    "Adduktoren (Maschine)" to Motion.ABDUCTION,
    "Abduktoren (Maschine)" to Motion.ABDUCTION,
    "Wadenheben stehend (Maschine)" to Motion.CALF_RAISE,
    "Wadenheben stehend (Körpergewicht, einbeinig)" to Motion.CALF_RAISE,
    "Wadenheben stehend (Kurzhantel, einbeinig)" to Motion.CALF_RAISE,
    "Wadenheben sitzend (Maschine)" to Motion.CALF_RAISE,
    "Glute Kickbacks (Kabelzug)" to Motion.KICKBACK,
    "Kniebeugen (Körpergewicht)" to Motion.SQUAT,
    "Beckenheben (Körpergewicht)" to Motion.HIP_THRUST,
    "Beckenheben (Körpergewicht, einbeinig)" to Motion.HIP_THRUST,
    "Nordic Curls (Körpergewicht)" to Motion.LEG_CURL,
    "Wandsitzen (Körpergewicht)" to Motion.SQUAT,
    "Seitliches Gehen (Widerstandsband)" to Motion.WALK,
    // Schultern
    "Schulterdrücken (Kurzhantel)" to Motion.OVERHEAD_PRESS,
    "Schulterdrücken (Kurzhantel, einarmig)" to Motion.OVERHEAD_PRESS,
    "Arnold Press (Kurzhantel)" to Motion.OVERHEAD_PRESS,
    "Seitheben (Kurzhantel)" to Motion.LATERAL_RAISE,
    "Seitheben (Kurzhantel, einarmig)" to Motion.LATERAL_RAISE,
    "Frontheben (Kurzhantel)" to Motion.FRONT_RAISE,
    "Frontheben (Kurzhantel, einarmig)" to Motion.FRONT_RAISE,
    "Reverse Flys (Kurzhantel)" to Motion.LATERAL_RAISE,
    "Reverse Flys (Kabelzug, einarmig)" to Motion.LATERAL_RAISE,
    "Schulterheben (Kurzhantel)" to Motion.SHRUG,
    "Schulterdrücken (Langhantel / Military Press)" to Motion.OVERHEAD_PRESS,
    "Aufrechtes Rudern (Langhantel)" to Motion.SHRUG,
    "Schulterheben (Langhantel)" to Motion.SHRUG,
    "Schulterdrücken (Maschine)" to Motion.OVERHEAD_PRESS,
    "Seitheben (Maschine)" to Motion.LATERAL_RAISE,
    "Reverse Flys (Maschine / Reverse Butterfly)" to Motion.LATERAL_RAISE,
    "Face Pulls (Kabelzug)" to Motion.FACE_PULL,
    "Seitheben (Kabelzug)" to Motion.LATERAL_RAISE,
    "Pike-Liegestütze (Körpergewicht)" to Motion.PUSH_UP,
    "Band Pull-Aparts (Widerstandsband)" to Motion.LATERAL_RAISE,
    // Arme
    "Bizepscurls (Kurzhantel)" to Motion.CURL,
    "Bizepscurls (Kabelzug, einarmig)" to Motion.CURL,
    "Bizepscurls (Kurzhantel, einarmig)" to Motion.CURL,
    "Hammercurls (Kurzhantel)" to Motion.CURL,
    "Hammercurls (Kurzhantel, einarmig)" to Motion.CURL,
    "Konzentrationscurls (Kurzhantel)" to Motion.CURL,
    "Schrägbank-Curls (Kurzhantel)" to Motion.CURL,
    "Schrägbank-Curls (Kurzhantel, einarmig)" to Motion.CURL,
    "Trizepsdrücken über Kopf (Kurzhantel)" to Motion.OVERHEAD_EXTENSION,
    "Trizepsdrücken über Kopf (Kabelzug, einarmig)" to Motion.OVERHEAD_EXTENSION,
    "Trizepsdrücken über Kopf (Kurzhantel, einarmig)" to Motion.OVERHEAD_EXTENSION,
    "Trizeps-Kickbacks (Kurzhantel)" to Motion.PUSHDOWN,
    "Bizepscurls (Langhantel)" to Motion.CURL,
    "Bizepscurls (SZ-Stange)" to Motion.CURL,
    "Scott-Curls (SZ-Stange)" to Motion.CURL,
    "Scott-Curls (Kurzhantel, einarmig)" to Motion.CURL,
    "Reverse Curls (SZ-Stange)" to Motion.CURL,
    "Unterarm-Curls (Langhantel)" to Motion.CURL,
    "Unterarm-Curls (Kurzhantel, einarmig)" to Motion.CURL,
    "French Press (SZ-Stange)" to Motion.OVERHEAD_EXTENSION,
    "Enges Bankdrücken (Langhantel)" to Motion.BENCH_PRESS,
    "Trizepsdrücken (Kabelzug)" to Motion.PUSHDOWN,
    "Trizepsdrücken (Kabelzug, einarmig)" to Motion.PUSHDOWN,
    "Trizepsdrücken über Kopf (Kabelzug)" to Motion.OVERHEAD_EXTENSION,
    "Bizepscurls (Kabelzug)" to Motion.CURL,
    "Bizepscurls (Maschine)" to Motion.CURL,
    "Trizepsdrücken (Maschine)" to Motion.PUSHDOWN,
    "Bankdips (Körpergewicht)" to Motion.DIP,
    "Diamond-Liegestütze (Körpergewicht)" to Motion.PUSH_UP,
    "Bizepscurls (Widerstandsband)" to Motion.CURL,
    // Bauch & Rumpf
    "Crunches (Körpergewicht)" to Motion.CRUNCH,
    "Sit-ups (Körpergewicht)" to Motion.CRUNCH,
    "Fahrrad-Crunches (Körpergewicht)" to Motion.CRUNCH,
    "Unterarmstütz / Plank (Körpergewicht)" to Motion.PLANK,
    "Seitstütz (Körpergewicht)" to Motion.PLANK,
    "Beinheben liegend (Körpergewicht)" to Motion.CRUNCH,
    "Hängendes Beinheben (Körpergewicht)" to Motion.HANGING_RAISE,
    "Knieheben an der Dip-Station (Körpergewicht)" to Motion.HANGING_RAISE,
    "Russian Twists (Körpergewicht)" to Motion.TWIST,
    "Mountain Climbers (Körpergewicht)" to Motion.PUSH_UP,
    "Dead Bug (Körpergewicht)" to Motion.CRUNCH,
    "V-Ups (Körpergewicht)" to Motion.CRUNCH,
    "Ab-Roller (Körpergewicht)" to Motion.PLANK,
    "Crunches (Kabelzug)" to Motion.CRUNCH,
    "Holzhacker (Kabelzug)" to Motion.TWIST,
    "Pallof Press (Kabelzug)" to Motion.TWIST,
    "Crunches (Maschine)" to Motion.CRUNCH,
    // Ganzkörper
    "Swing (Kettlebell)" to Motion.KETTLEBELL_SWING,
    "Swing (Kettlebell, einarmig)" to Motion.KETTLEBELL_SWING,
    "Turkish Get-up (Kettlebell)" to Motion.OLYMPIC,
    "Clean & Press (Kettlebell)" to Motion.OLYMPIC,
    "Thruster (Langhantel)" to Motion.OLYMPIC,
    "Umsetzen / Power Clean (Langhantel)" to Motion.OLYMPIC,
    "Reißen / Snatch (Langhantel)" to Motion.OLYMPIC,
    "Farmer's Walk (Kurzhantel)" to Motion.CARRY,
    "Farmer's Walk (Kurzhantel, einarmig)" to Motion.CARRY,
    "Burpees (Körpergewicht)" to Motion.JUMP,
    "Box Jumps (Körpergewicht)" to Motion.JUMP,
    "Hampelmann (Körpergewicht)" to Motion.JUMP,
    // Cardio
    "Laufband" to Motion.RUN,
    "Laufband Gehen mit Steigung" to Motion.WALK,
    "Crosstrainer" to Motion.WALK,
    "Fahrradergometer" to Motion.BIKE,
    "Spinning" to Motion.BIKE,
    "Air Bike" to Motion.BIKE,
    "Rudergerät" to Motion.ROWER,
    "Ski-Ergometer" to Motion.ROWER,
    "Stepper" to Motion.STAIRS,
    "Laufen (draußen)" to Motion.RUN,
    "Gehen" to Motion.WALK,
    "Wandern" to Motion.WALK,
    "Radfahren (draußen)" to Motion.BIKE,
    "Schwimmen" to Motion.SWIM,
    "Seilspringen" to Motion.JUMP_ROPE,
    "HIIT" to Motion.JUMP,
    "Boxen (Sandsack)" to Motion.BOXING,
    "Battle Ropes" to Motion.BATTLE_ROPES,

    // Neue Varianten (übernehmen die Bewegung ihrer Grundübung)
    "Schrägbankdrücken (Multipresse)" to Motion.INCLINE_PRESS,
    "Negativbankdrücken (Kurzhantel)" to Motion.BENCH_PRESS,
    "Negativbankdrücken (Maschine)" to Motion.BENCH_PRESS,
    "Floor Press (Kurzhantel)" to Motion.BENCH_PRESS,
    "Schrägbank-Fliegende (Kabelzug)" to Motion.FLY,
    "Dips (Maschine)" to Motion.DIP,
    "Kreuzheben (Kurzhantel)" to Motion.DEADLIFT,
    "Rudern vorgebeugt (Kurzhantel)" to Motion.ROW_BENT,
    "Einarmiges Rudern (Kabelzug)" to Motion.ROW_BENT,
    "T-Bar-Rudern (Maschine)" to Motion.ROW_BENT,
    "Latziehen (Maschine)" to Motion.PULLDOWN,
    "Klimmzüge (Widerstandsband)" to Motion.PULL_UP,
    "Goblet Squat (Kettlebell)" to Motion.SQUAT,
    "Rumänisches Kreuzheben (Kurzhantel)" to Motion.DEADLIFT,
    "Hip Thrust (Maschine)" to Motion.HIP_THRUST,
    "Ausfallschritte (Langhantel)" to Motion.LUNGE,
    "Ausfallschritte (Körpergewicht)" to Motion.LUNGE,
    "Bulgarische Kniebeugen (Multipresse)" to Motion.LUNGE,
    "Bulgarische Kniebeugen (Körpergewicht)" to Motion.LUNGE,
    "Wadenheben stehend (Kurzhantel)" to Motion.CALF_RAISE,
    "Schulterdrücken (Multipresse)" to Motion.OVERHEAD_PRESS,
    "Frontheben (Kabelzug)" to Motion.FRONT_RAISE,
    "Reverse Flys (Kabelzug)" to Motion.LATERAL_RAISE,
    "Aufrechtes Rudern (Kurzhantel)" to Motion.SHRUG,
    "Aufrechtes Rudern (Kabelzug)" to Motion.SHRUG,
    "Hammercurls (Kabelzug)" to Motion.CURL,
    "Scott-Curls (Kurzhantel)" to Motion.CURL,
    "Scott-Curls (Maschine)" to Motion.CURL,
    "Unterarm-Curls (Kurzhantel)" to Motion.CURL,
    "Trizeps-Kickbacks (Kabelzug)" to Motion.PUSHDOWN,
    "French Press (Kurzhantel)" to Motion.OVERHEAD_EXTENSION,
    "Russian Twists (Kurzhantel)" to Motion.TWIST,
    "Pallof Press (Widerstandsband)" to Motion.TWIST,
    "Thruster (Kurzhantel)" to Motion.OLYMPIC,
    "Farmer's Walk (Kettlebell)" to Motion.CARRY
)

/** Bewegung für einen Übungsnamen (Katalog) oder die Standard-Bewegung der Gruppe. */
fun motionOf(name: String, group: MuscleGroup): Motion = MOTION_OF[name] ?: group.defaultMotion()

/** Rundes Symbol einer Übung: Kreis in Kategorie-Farbe mit Piktogramm der Bewegung. */
@Composable
fun ExerciseIcon(name: String, group: MuscleGroup, modifier: Modifier = Modifier) {
    // Foto, falls vorhanden (ExerciseImages.kt), sonst das gezeichnete Piktogramm
    // Abgerundetes Quadrat (zeigt mehr vom Bild als ein Kreis), leicht auf die Figur gezoomt
    val zoom = Modifier.clip(RoundedCornerShape(percent = 24)).graphicsLayer {
        scaleX = 1.15f; scaleY = 1.15f
        transformOrigin = TransformOrigin(0.5f, 0.45f)
    }
    if (!ExerciseImage(name, modifier.then(zoom))) MotionIcon(motionOf(name, group), group.color, modifier)
}

/** Rundes Symbol einer Muskelgruppe (Filter-Chips, Workout-Vorschau). */
@Composable
fun MuscleIcon(group: MuscleGroup, modifier: Modifier = Modifier) {
    MotionIcon(group.defaultMotion(), group.color, modifier)
}

@Composable
fun MotionIcon(motion: Motion, background: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawCircle(background)
        drawMotion(motion, Color.White)
    }
}

/**
 * Zeichnet eine Bewegung. Hilfsfunktionen (alle Koordinaten 0..1):
 *  head(x, y)          Kopf (gefüllter Kreis)
 *  torso(a, b)         Rumpf (dicke Linie)
 *  limb(a, b, c?)      Arm/Bein über 2–3 Gelenkpunkte
 *  thin(…)             Geräte-Linie (dünner): Bank, Stange, Kabel, Boden
 *  plate(x, y, r)      Hantelscheibe (gefüllter Kreis)
 *  dumbbell(x, y, v)   kleine Kurzhantel (waagerecht, oder senkrecht bei v = true)
 */
fun DrawScope.drawMotion(m: Motion, c: Color) {
    val u = size.minDimension
    fun p(x: Float, y: Float) = Offset(x * size.width, y * size.height)
    fun line(w: Float, vararg xy: Float) {
        for (i in 0 until xy.size - 2 step 2) {
            drawLine(c, p(xy[i], xy[i + 1]), p(xy[i + 2], xy[i + 3]), w * u, StrokeCap.Round)
        }
    }
    fun limb(vararg xy: Float) = line(0.062f, *xy)
    fun torso(vararg xy: Float) = line(0.085f, *xy)
    fun thin(vararg xy: Float) = line(0.034f, *xy)
    fun head(x: Float, y: Float, r: Float = 0.068f) = drawCircle(c, r * u, p(x, y))
    fun plate(x: Float, y: Float, r: Float = 0.075f) = drawCircle(c, r * u, p(x, y))
    fun ring(x: Float, y: Float, r: Float) = drawCircle(c, r * u, p(x, y), style = Stroke(0.034f * u))
    fun dumbbell(x: Float, y: Float, vertical: Boolean = false) {
        if (vertical) {
            thin(x, y - 0.06f, x, y + 0.06f)
            line(0.07f, x - 0.03f, y - 0.06f, x + 0.03f, y - 0.06f)
            line(0.07f, x - 0.03f, y + 0.06f, x + 0.03f, y + 0.06f)
        } else {
            thin(x - 0.06f, y, x + 0.06f, y)
            line(0.07f, x - 0.06f, y - 0.03f, x - 0.06f, y + 0.03f)
            line(0.07f, x + 0.06f, y - 0.03f, x + 0.06f, y + 0.03f)
        }
    }
    /** Langhantel von vorne: Stange mit Scheiben an beiden Enden. */
    fun barbellFront(x1: Float, x2: Float, y: Float) {
        thin(x1, y, x2, y)
        line(0.07f, x1 + 0.03f, y - 0.06f, x1 + 0.03f, y + 0.06f)
        line(0.07f, x2 - 0.03f, y - 0.06f, x2 - 0.03f, y + 0.06f)
    }
    fun floor(y: Float = 0.84f) = thin(0.2f, y, 0.8f, y)

    when (m) {
        // ---------- Brust ----------
        Motion.BENCH_PRESS -> { // flach auf der Bank, Hantel über der Brust
            thin(0.16f, 0.66f, 0.78f, 0.66f); thin(0.28f, 0.66f, 0.28f, 0.80f); thin(0.68f, 0.66f, 0.68f, 0.80f)
            head(0.21f, 0.56f)
            torso(0.30f, 0.58f, 0.56f, 0.58f)
            limb(0.56f, 0.58f, 0.70f, 0.50f, 0.74f, 0.80f)
            limb(0.36f, 0.58f, 0.38f, 0.32f)
            plate(0.38f, 0.27f, 0.09f)
        }
        Motion.INCLINE_PRESS -> { // Schrägbank
            thin(0.24f, 0.36f, 0.44f, 0.72f); thin(0.44f, 0.72f, 0.62f, 0.72f); thin(0.48f, 0.72f, 0.48f, 0.84f)
            head(0.27f, 0.30f)
            torso(0.34f, 0.40f, 0.48f, 0.66f)
            limb(0.48f, 0.66f, 0.64f, 0.60f, 0.66f, 0.84f)
            limb(0.36f, 0.42f, 0.52f, 0.24f)
            plate(0.55f, 0.20f, 0.085f)
        }
        Motion.CHEST_PRESS -> { // sitzend an der Maschine, Griffe nach vorne
            thin(0.30f, 0.28f, 0.30f, 0.66f); thin(0.30f, 0.66f, 0.52f, 0.66f); thin(0.40f, 0.66f, 0.40f, 0.84f)
            head(0.38f, 0.24f)
            torso(0.37f, 0.34f, 0.38f, 0.62f)
            limb(0.38f, 0.62f, 0.60f, 0.62f, 0.60f, 0.84f)
            limb(0.39f, 0.38f, 0.56f, 0.40f, 0.74f, 0.40f)
            thin(0.76f, 0.32f, 0.76f, 0.48f)  // Griff
            thin(0.76f, 0.40f, 0.84f, 0.40f)
        }
        Motion.FLY -> { // von vorne, Arme im Bogen nach vorne zusammen (Butterfly/Kabelzug)
            head(0.50f, 0.22f)
            limb(0.38f, 0.35f, 0.62f, 0.35f)
            torso(0.50f, 0.34f, 0.50f, 0.60f)
            limb(0.39f, 0.36f, 0.22f, 0.40f, 0.40f, 0.52f)
            limb(0.61f, 0.36f, 0.78f, 0.40f, 0.60f, 0.52f)
            limb(0.46f, 0.60f, 0.42f, 0.84f)
            limb(0.54f, 0.60f, 0.58f, 0.84f)
        }
        Motion.PUSH_UP -> {
            floor(0.80f)
            head(0.24f, 0.46f)
            torso(0.31f, 0.52f, 0.56f, 0.63f)
            limb(0.56f, 0.63f, 0.82f, 0.76f)
            limb(0.33f, 0.53f, 0.33f, 0.79f)
        }
        Motion.DIP -> { // von vorne zwischen zwei Holmen
            thin(0.26f, 0.46f, 0.26f, 0.86f); thin(0.74f, 0.46f, 0.74f, 0.86f)
            head(0.50f, 0.20f)
            limb(0.40f, 0.31f, 0.60f, 0.31f)
            limb(0.40f, 0.31f, 0.27f, 0.46f)
            limb(0.60f, 0.31f, 0.73f, 0.46f)
            torso(0.50f, 0.31f, 0.50f, 0.56f)
            limb(0.47f, 0.56f, 0.42f, 0.70f, 0.52f, 0.78f)
            limb(0.53f, 0.56f, 0.58f, 0.70f, 0.50f, 0.78f)
        }

        // ---------- Rücken ----------
        Motion.PULL_UP -> {
            thin(0.16f, 0.16f, 0.84f, 0.16f)
            limb(0.32f, 0.16f, 0.30f, 0.30f, 0.40f, 0.40f)
            limb(0.68f, 0.16f, 0.70f, 0.30f, 0.60f, 0.40f)
            head(0.50f, 0.30f)
            limb(0.40f, 0.40f, 0.60f, 0.40f)
            torso(0.50f, 0.40f, 0.50f, 0.64f)
            limb(0.47f, 0.64f, 0.44f, 0.86f)
            limb(0.53f, 0.64f, 0.56f, 0.86f)
        }
        Motion.PULLDOWN -> { // Latzug von vorne: Stange am Kabel, sitzend
            thin(0.50f, 0.08f, 0.50f, 0.22f)
            thin(0.18f, 0.22f, 0.82f, 0.22f)
            limb(0.30f, 0.22f, 0.30f, 0.38f, 0.40f, 0.46f)
            limb(0.70f, 0.22f, 0.70f, 0.38f, 0.60f, 0.46f)
            head(0.50f, 0.36f)
            limb(0.40f, 0.46f, 0.60f, 0.46f)
            torso(0.50f, 0.46f, 0.50f, 0.66f)
            thin(0.30f, 0.70f, 0.70f, 0.70f)
            limb(0.45f, 0.68f, 0.40f, 0.86f)
            limb(0.55f, 0.68f, 0.60f, 0.86f)
        }
        Motion.ROW_BENT -> { // vorgebeugt, Hantel zum Bauch ziehen
            floor()
            head(0.27f, 0.36f)
            torso(0.34f, 0.40f, 0.62f, 0.50f)
            limb(0.62f, 0.50f, 0.56f, 0.66f, 0.60f, 0.84f)
            limb(0.36f, 0.42f, 0.50f, 0.44f, 0.44f, 0.58f)
            plate(0.44f, 0.62f, 0.08f)
        }
        Motion.ROW_SEATED -> { // sitzend am Kabel, Griff zum Bauch
            thin(0.20f, 0.74f, 0.80f, 0.74f)
            thin(0.20f, 0.56f, 0.20f, 0.76f)               // Fußplatte
            head(0.64f, 0.30f)
            torso(0.62f, 0.40f, 0.64f, 0.68f)
            limb(0.64f, 0.68f, 0.42f, 0.58f, 0.24f, 0.68f)
            limb(0.62f, 0.42f, 0.74f, 0.52f, 0.54f, 0.54f)
            thin(0.54f, 0.54f, 0.22f, 0.54f)               // Kabel
        }
        Motion.DEADLIFT -> { // Hüfte gebeugt, Hantel nahe am Boden
            floor()
            head(0.33f, 0.34f)
            torso(0.40f, 0.38f, 0.64f, 0.50f)
            limb(0.64f, 0.50f, 0.50f, 0.62f, 0.56f, 0.84f)
            limb(0.42f, 0.40f, 0.44f, 0.68f)
            plate(0.44f, 0.75f, 0.09f)
        }
        Motion.BACK_EXTENSION -> { // auf dem Hyperextensions-Gerät
            thin(0.48f, 0.60f, 0.56f, 0.84f); thin(0.74f, 0.70f, 0.70f, 0.84f)
            thin(0.40f, 0.60f, 0.58f, 0.60f)               // Polster
            head(0.17f, 0.46f)
            torso(0.24f, 0.50f, 0.50f, 0.56f)
            limb(0.50f, 0.56f, 0.80f, 0.66f)
            limb(0.28f, 0.51f, 0.34f, 0.60f)
        }

        // ---------- Beine & Gesäß ----------
        Motion.SQUAT -> { // Kniebeuge mit Langhantel im Nacken
            floor()
            head(0.47f, 0.22f)
            thin(0.24f, 0.33f, 0.72f, 0.33f)
            plate(0.25f, 0.33f, 0.07f); plate(0.71f, 0.33f, 0.07f)
            torso(0.46f, 0.34f, 0.40f, 0.58f)
            limb(0.40f, 0.58f, 0.62f, 0.62f, 0.56f, 0.84f)
            limb(0.46f, 0.36f, 0.56f, 0.42f, 0.58f, 0.34f)
        }
        Motion.LUNGE -> { // Ausfallschritt mit Kurzhanteln
            floor()
            head(0.50f, 0.20f)
            torso(0.50f, 0.30f, 0.50f, 0.52f)
            limb(0.50f, 0.52f, 0.66f, 0.62f, 0.66f, 0.84f)   // vorderes Bein
            limb(0.50f, 0.52f, 0.40f, 0.74f, 0.24f, 0.80f)   // hinteres Bein
            limb(0.49f, 0.32f, 0.41f, 0.50f)
            dumbbell(0.40f, 0.55f, vertical = true)
        }
        Motion.LEG_PRESS -> { // schräg liegend, Beine drücken die Plattform
            thin(0.18f, 0.44f, 0.36f, 0.76f); thin(0.36f, 0.76f, 0.52f, 0.76f)
            head(0.20f, 0.38f)
            torso(0.26f, 0.48f, 0.42f, 0.70f)
            limb(0.42f, 0.70f, 0.56f, 0.50f, 0.72f, 0.42f)
            thin(0.66f, 0.30f, 0.82f, 0.56f)               // Plattform
            limb(0.28f, 0.50f, 0.40f, 0.62f)
        }
        Motion.LEG_EXTENSION -> { // sitzend, Unterschenkel nach vorne strecken
            thin(0.28f, 0.30f, 0.30f, 0.64f); thin(0.30f, 0.64f, 0.56f, 0.64f); thin(0.42f, 0.64f, 0.42f, 0.84f)
            head(0.36f, 0.22f)
            torso(0.35f, 0.32f, 0.36f, 0.60f)
            limb(0.36f, 0.60f, 0.58f, 0.60f, 0.80f, 0.52f)
            plate(0.78f, 0.58f, 0.035f)                     // Polster an der Fessel
            limb(0.37f, 0.36f, 0.48f, 0.60f)
        }
        Motion.LEG_CURL -> { // auf dem Bauch liegend, Unterschenkel anwinkeln
            thin(0.14f, 0.64f, 0.70f, 0.64f); thin(0.24f, 0.64f, 0.24f, 0.80f); thin(0.62f, 0.64f, 0.62f, 0.80f)
            head(0.20f, 0.54f)
            torso(0.27f, 0.56f, 0.52f, 0.56f)
            limb(0.52f, 0.56f, 0.72f, 0.56f, 0.66f, 0.34f)
            limb(0.30f, 0.57f, 0.18f, 0.66f)
        }
        Motion.CALF_RAISE -> { // auf den Zehenspitzen auf einer Stufe
            floor()
            drawRoundRect(c, p(0.38f, 0.74f), Size(0.28f * u, 0.10f * u), CornerRadius(0.02f * u))
            head(0.52f, 0.16f)
            torso(0.52f, 0.26f, 0.52f, 0.48f)
            limb(0.52f, 0.48f, 0.53f, 0.66f)
            limb(0.53f, 0.66f, 0.47f, 0.73f)                 // Fuß auf Zehenspitzen
            limb(0.53f, 0.28f, 0.57f, 0.46f)
            line(0.04f, 0.74f, 0.44f, 0.74f, 0.30f); line(0.04f, 0.70f, 0.35f, 0.74f, 0.30f, 0.78f, 0.35f) // Pfeil hoch
        }
        Motion.HIP_THRUST -> { // Schultern auf der Bank, Hüfte hoch
            floor()
            thin(0.14f, 0.52f, 0.30f, 0.52f); thin(0.22f, 0.52f, 0.22f, 0.84f)
            head(0.21f, 0.43f)
            torso(0.30f, 0.50f, 0.54f, 0.48f)
            limb(0.54f, 0.48f, 0.70f, 0.50f, 0.72f, 0.84f)
            plate(0.52f, 0.38f, 0.075f)
            limb(0.34f, 0.49f, 0.46f, 0.42f)
        }
        Motion.ABDUCTION -> { // sitzend von vorne, Beine nach außen
            thin(0.32f, 0.60f, 0.68f, 0.60f)
            head(0.50f, 0.20f)
            limb(0.40f, 0.31f, 0.60f, 0.31f)
            torso(0.50f, 0.31f, 0.50f, 0.58f)
            limb(0.40f, 0.31f, 0.36f, 0.56f)
            limb(0.60f, 0.31f, 0.64f, 0.56f)
            limb(0.46f, 0.60f, 0.26f, 0.70f, 0.24f, 0.86f)
            limb(0.54f, 0.60f, 0.74f, 0.70f, 0.76f, 0.86f)
            line(0.04f, 0.14f, 0.70f, 0.20f, 0.70f); line(0.04f, 0.86f, 0.70f, 0.80f, 0.70f)
        }
        Motion.KICKBACK -> { // vorgebeugt, Bein nach hinten oben (Kabel am Fuß)
            floor()
            thin(0.86f, 0.20f, 0.86f, 0.84f)
            head(0.24f, 0.32f)
            torso(0.30f, 0.38f, 0.52f, 0.48f)
            limb(0.52f, 0.48f, 0.50f, 0.66f, 0.50f, 0.84f)   // Standbein
            limb(0.52f, 0.48f, 0.68f, 0.40f, 0.80f, 0.34f)   // gestrecktes Bein
            thin(0.80f, 0.34f, 0.86f, 0.30f)                 // Kabel
            limb(0.32f, 0.40f, 0.32f, 0.62f)
        }

        // ---------- Schultern ----------
        Motion.OVERHEAD_PRESS -> {
            barbellFront(0.22f, 0.78f, 0.18f)
            head(0.50f, 0.34f)
            limb(0.38f, 0.45f, 0.62f, 0.45f)
            limb(0.38f, 0.45f, 0.30f, 0.32f, 0.33f, 0.19f)
            limb(0.62f, 0.45f, 0.70f, 0.32f, 0.67f, 0.19f)
            torso(0.50f, 0.45f, 0.50f, 0.67f)
            limb(0.47f, 0.67f, 0.43f, 0.88f)
            limb(0.53f, 0.67f, 0.57f, 0.88f)
        }
        Motion.LATERAL_RAISE -> { // von vorne, Arme seitlich gestreckt
            head(0.50f, 0.22f)
            limb(0.38f, 0.34f, 0.62f, 0.34f)
            limb(0.38f, 0.34f, 0.18f, 0.37f)
            limb(0.62f, 0.34f, 0.82f, 0.37f)
            dumbbell(0.16f, 0.37f, vertical = true); dumbbell(0.84f, 0.37f, vertical = true)
            torso(0.50f, 0.34f, 0.50f, 0.60f)
            limb(0.47f, 0.60f, 0.43f, 0.86f)
            limb(0.53f, 0.60f, 0.57f, 0.86f)
        }
        Motion.FRONT_RAISE -> { // seitlich, Arm gestreckt nach vorne
            head(0.52f, 0.22f)
            torso(0.52f, 0.32f, 0.52f, 0.58f)
            limb(0.51f, 0.35f, 0.26f, 0.35f)
            dumbbell(0.23f, 0.35f, vertical = true)
            limb(0.52f, 0.58f, 0.48f, 0.86f)
            limb(0.52f, 0.58f, 0.58f, 0.86f)
        }
        Motion.SHRUG -> { // von vorne, Schultern hochziehen (Pfeile)
            head(0.50f, 0.26f)
            limb(0.36f, 0.36f, 0.64f, 0.36f)
            limb(0.36f, 0.36f, 0.33f, 0.58f)
            limb(0.64f, 0.36f, 0.67f, 0.58f)
            dumbbell(0.33f, 0.63f); dumbbell(0.67f, 0.63f)
            torso(0.50f, 0.36f, 0.50f, 0.62f)
            limb(0.47f, 0.62f, 0.44f, 0.86f)
            limb(0.53f, 0.62f, 0.56f, 0.86f)
            line(0.04f, 0.30f, 0.26f, 0.30f, 0.14f); line(0.04f, 0.26f, 0.18f, 0.30f, 0.14f, 0.34f, 0.18f)
            line(0.04f, 0.70f, 0.26f, 0.70f, 0.14f); line(0.04f, 0.66f, 0.18f, 0.70f, 0.14f, 0.74f, 0.18f)
        }
        Motion.FACE_PULL -> { // Seil am Kabel zum Gesicht ziehen
            thin(0.84f, 0.22f, 0.84f, 0.78f)
            head(0.42f, 0.24f)
            torso(0.44f, 0.34f, 0.46f, 0.60f)
            limb(0.46f, 0.60f, 0.40f, 0.86f)
            limb(0.46f, 0.60f, 0.54f, 0.86f)
            limb(0.45f, 0.37f, 0.58f, 0.24f, 0.52f, 0.30f)
            thin(0.54f, 0.29f, 0.84f, 0.29f)
        }

        // ---------- Arme ----------
        Motion.CURL -> { // von vorne, ein Arm gestreckt, einer gebeugt
            head(0.50f, 0.20f)
            limb(0.38f, 0.32f, 0.62f, 0.32f)
            torso(0.50f, 0.32f, 0.50f, 0.60f)
            limb(0.47f, 0.60f, 0.43f, 0.86f)
            limb(0.53f, 0.60f, 0.57f, 0.86f)
            limb(0.38f, 0.32f, 0.34f, 0.48f, 0.34f, 0.62f)
            dumbbell(0.34f, 0.66f)
            limb(0.62f, 0.32f, 0.67f, 0.50f, 0.76f, 0.36f)
            dumbbell(0.77f, 0.32f)
        }
        Motion.PUSHDOWN -> { // Trizepsdrücken am Kabel (seitlich)
            thin(0.82f, 0.20f, 0.82f, 0.80f)
            thin(0.82f, 0.24f, 0.66f, 0.56f)               // Kabel
            head(0.44f, 0.20f)
            torso(0.45f, 0.30f, 0.47f, 0.58f)
            limb(0.47f, 0.58f, 0.42f, 0.86f)
            limb(0.47f, 0.58f, 0.55f, 0.86f)
            limb(0.46f, 0.33f, 0.50f, 0.48f, 0.66f, 0.56f)
        }
        Motion.OVERHEAD_EXTENSION -> { // Trizeps über Kopf, Unterarm hinter dem Kopf
            head(0.40f, 0.30f)
            torso(0.46f, 0.40f, 0.48f, 0.64f)
            limb(0.48f, 0.64f, 0.44f, 0.88f)
            limb(0.48f, 0.64f, 0.56f, 0.88f)
            limb(0.47f, 0.40f, 0.52f, 0.14f, 0.64f, 0.26f)
            dumbbell(0.67f, 0.29f, vertical = true)
        }

        // ---------- Bauch & Rumpf ----------
        Motion.CRUNCH -> {
            floor(0.82f)
            limb(0.52f, 0.76f, 0.66f, 0.56f, 0.80f, 0.80f)
            torso(0.52f, 0.76f, 0.34f, 0.60f)
            head(0.28f, 0.52f)
            limb(0.36f, 0.61f, 0.44f, 0.56f)
        }
        Motion.PLANK -> { // Unterarmstütz
            floor(0.80f)
            head(0.22f, 0.54f)
            torso(0.30f, 0.60f, 0.56f, 0.66f)
            limb(0.56f, 0.66f, 0.84f, 0.76f)
            limb(0.31f, 0.61f, 0.31f, 0.77f, 0.18f, 0.77f)
        }
        Motion.HANGING_RAISE -> { // hängend, Beine nach vorne hoch
            thin(0.24f, 0.12f, 0.70f, 0.12f)
            limb(0.48f, 0.14f, 0.48f, 0.38f)
            head(0.40f, 0.30f)
            torso(0.48f, 0.38f, 0.48f, 0.62f)
            limb(0.48f, 0.62f, 0.82f, 0.58f)
        }
        Motion.TWIST -> { // sitzend, Oberkörper zurück, Ball zur Seite
            floor()
            torso(0.52f, 0.76f, 0.38f, 0.52f)
            head(0.33f, 0.43f)
            limb(0.52f, 0.76f, 0.64f, 0.58f, 0.78f, 0.66f)
            limb(0.40f, 0.55f, 0.58f, 0.52f)
            plate(0.63f, 0.50f, 0.055f)
        }

        // ---------- Ganzkörper ----------
        Motion.KETTLEBELL_SWING -> {
            floor()
            head(0.45f, 0.20f)
            torso(0.47f, 0.30f, 0.55f, 0.52f)
            limb(0.55f, 0.52f, 0.50f, 0.68f, 0.54f, 0.84f)
            limb(0.48f, 0.33f, 0.74f, 0.38f)
            plate(0.79f, 0.44f, 0.06f)
            ring(0.77f, 0.37f, 0.025f)                      // Griff
        }
        Motion.JUMP -> { // Hampelmann / Sprung
            head(0.50f, 0.22f)
            torso(0.50f, 0.32f, 0.50f, 0.58f)
            limb(0.50f, 0.36f, 0.36f, 0.26f, 0.26f, 0.14f)
            limb(0.50f, 0.36f, 0.64f, 0.26f, 0.74f, 0.14f)
            limb(0.50f, 0.58f, 0.38f, 0.72f, 0.30f, 0.86f)
            limb(0.50f, 0.58f, 0.62f, 0.72f, 0.70f, 0.86f)
        }
        Motion.CARRY -> { // Farmer's Walk: gehend mit schweren Gewichten
            head(0.50f, 0.18f)
            torso(0.50f, 0.28f, 0.50f, 0.54f)
            limb(0.50f, 0.54f, 0.42f, 0.70f, 0.38f, 0.86f)
            limb(0.50f, 0.54f, 0.57f, 0.70f, 0.62f, 0.86f)
            limb(0.51f, 0.31f, 0.55f, 0.52f)
            plate(0.56f, 0.60f, 0.07f)
            ring(0.56f, 0.51f, 0.03f)
        }
        Motion.OLYMPIC -> { // Hantel über Kopf in der Hocke (Reißen/Umsetzen/Thruster)
            barbellFront(0.20f, 0.80f, 0.19f)
            head(0.50f, 0.30f)
            limb(0.38f, 0.41f, 0.62f, 0.41f)
            limb(0.38f, 0.41f, 0.30f, 0.20f)
            limb(0.62f, 0.41f, 0.70f, 0.20f)
            torso(0.50f, 0.41f, 0.50f, 0.60f)
            limb(0.46f, 0.60f, 0.30f, 0.68f, 0.34f, 0.86f)
            limb(0.54f, 0.60f, 0.70f, 0.68f, 0.66f, 0.86f)
        }

        // ---------- Cardio ----------
        Motion.RUN -> {
            head(0.57f, 0.22f)
            torso(0.54f, 0.33f, 0.46f, 0.56f)
            limb(0.46f, 0.56f, 0.62f, 0.64f, 0.58f, 0.84f)
            limb(0.46f, 0.56f, 0.36f, 0.70f, 0.22f, 0.68f)
            limb(0.52f, 0.38f, 0.64f, 0.46f, 0.72f, 0.36f)
            limb(0.52f, 0.38f, 0.40f, 0.44f, 0.34f, 0.36f)
        }
        Motion.WALK -> {
            head(0.50f, 0.18f)
            torso(0.50f, 0.28f, 0.50f, 0.54f)
            limb(0.50f, 0.54f, 0.44f, 0.70f, 0.40f, 0.86f)
            limb(0.50f, 0.54f, 0.56f, 0.70f, 0.62f, 0.86f)
            limb(0.51f, 0.31f, 0.42f, 0.48f)
            limb(0.51f, 0.31f, 0.60f, 0.48f)
        }
        Motion.BIKE -> {
            ring(0.27f, 0.68f, 0.13f); ring(0.73f, 0.68f, 0.13f)
            thin(0.27f, 0.68f, 0.44f, 0.50f, 0.66f, 0.50f, 0.73f, 0.68f)
            thin(0.44f, 0.50f, 0.50f, 0.68f, 0.27f, 0.68f)
            head(0.64f, 0.20f)
            torso(0.42f, 0.44f, 0.60f, 0.28f)
            limb(0.42f, 0.44f, 0.56f, 0.54f, 0.50f, 0.68f)
            limb(0.58f, 0.31f, 0.66f, 0.46f)
        }
        Motion.ROWER -> {
            thin(0.12f, 0.76f, 0.88f, 0.76f)
            ring(0.80f, 0.66f, 0.07f)                       // Schwungrad
            head(0.26f, 0.38f)
            torso(0.32f, 0.46f, 0.42f, 0.70f)
            limb(0.42f, 0.70f, 0.58f, 0.56f, 0.70f, 0.68f)
            limb(0.33f, 0.48f, 0.26f, 0.58f, 0.40f, 0.56f)
            thin(0.40f, 0.56f, 0.76f, 0.64f)                 // Zugseil
        }
        Motion.SWIM -> {
            thin(0.14f, 0.68f, 0.24f, 0.64f, 0.34f, 0.68f, 0.44f, 0.64f, 0.54f, 0.68f, 0.64f, 0.64f, 0.74f, 0.68f, 0.84f, 0.64f)
            head(0.24f, 0.50f)
            torso(0.32f, 0.53f, 0.62f, 0.55f)
            limb(0.62f, 0.55f, 0.84f, 0.50f)
            limb(0.34f, 0.53f, 0.30f, 0.36f, 0.16f, 0.42f)
        }
        Motion.JUMP_ROPE -> {
            drawArc(c, 180f, 180f, false, p(0.24f, 0.08f), Size(0.52f * u, 0.88f * u), style = Stroke(0.03f * u))
            head(0.50f, 0.24f)
            torso(0.50f, 0.34f, 0.50f, 0.58f)
            limb(0.48f, 0.38f, 0.30f, 0.50f)
            limb(0.52f, 0.38f, 0.70f, 0.50f)
            limb(0.48f, 0.58f, 0.44f, 0.74f, 0.47f, 0.82f)
            limb(0.52f, 0.58f, 0.56f, 0.74f, 0.53f, 0.82f)
        }
        Motion.STAIRS -> { // Treppe / Stepper / Step-ups
            thin(0.20f, 0.84f, 0.34f, 0.84f, 0.34f, 0.72f, 0.56f, 0.72f, 0.56f, 0.60f, 0.80f, 0.60f)
            head(0.48f, 0.16f)
            torso(0.47f, 0.26f, 0.44f, 0.48f)
            limb(0.44f, 0.48f, 0.54f, 0.58f, 0.52f, 0.71f)   // vorderes Bein auf der höheren Stufe
            limb(0.44f, 0.48f, 0.38f, 0.64f, 0.30f, 0.83f)
            limb(0.46f, 0.30f, 0.56f, 0.42f)
            limb(0.46f, 0.30f, 0.36f, 0.40f)
        }
        Motion.BOXING -> {
            head(0.38f, 0.22f)
            torso(0.42f, 0.32f, 0.46f, 0.58f)
            limb(0.46f, 0.58f, 0.34f, 0.84f)
            limb(0.46f, 0.58f, 0.60f, 0.84f)
            limb(0.43f, 0.35f, 0.74f, 0.33f)
            plate(0.77f, 0.33f, 0.05f)                      // Handschuh
            limb(0.43f, 0.36f, 0.52f, 0.44f, 0.40f, 0.30f)
            plate(0.38f, 0.30f, 0.045f)
        }
        Motion.BATTLE_ROPES -> {
            floor()
            head(0.28f, 0.26f)
            torso(0.31f, 0.36f, 0.36f, 0.58f)
            limb(0.36f, 0.58f, 0.48f, 0.66f, 0.42f, 0.84f)
            limb(0.36f, 0.58f, 0.28f, 0.72f, 0.24f, 0.84f)
            limb(0.33f, 0.40f, 0.48f, 0.52f)
            thin(0.48f, 0.52f, 0.56f, 0.44f, 0.64f, 0.58f, 0.72f, 0.44f, 0.82f, 0.56f)
        }

        // ---------- Sonstige ----------
        Motion.DUMBBELL -> {
            line(0.06f, 0.30f, 0.50f, 0.70f, 0.50f)
            drawRoundRect(c, p(0.20f, 0.34f), Size(0.12f * u, 0.32f * u), CornerRadius(0.04f * u))
            drawRoundRect(c, p(0.68f, 0.34f), Size(0.12f * u, 0.32f * u), CornerRadius(0.04f * u))
        }
    }
}
