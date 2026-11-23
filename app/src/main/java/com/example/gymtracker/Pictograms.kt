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
    "Bankdrücken (Kurzhantel)" to Motion.BENCH_PRESS,
    "Bankdrücken (Kurzhantel, einarmig)" to Motion.BENCH_PRESS,
    "Bankdrücken (Multipresse)" to Motion.BENCH_PRESS,
    "Brustpresse (Maschine)" to Motion.CHEST_PRESS,
    "Schrägbankdrücken (Langhantel)" to Motion.INCLINE_PRESS,
    "Schrägbankdrücken (Kurzhantel)" to Motion.INCLINE_PRESS,
    "Schrägbankdrücken (Kurzhantel, einarmig)" to Motion.INCLINE_PRESS,
    "Schrägbankdrücken (Maschine)" to Motion.CHEST_PRESS,
    "Schrägbankdrücken (Multipresse)" to Motion.INCLINE_PRESS,
    "Negativbankdrücken (Langhantel)" to Motion.BENCH_PRESS,
    "Negativbankdrücken (Kurzhantel)" to Motion.BENCH_PRESS,
    "Negativbankdrücken (Maschine)" to Motion.CHEST_PRESS,
    "Negativbankdrücken (Multipresse)" to Motion.BENCH_PRESS,
    "Floor Press (Langhantel)" to Motion.BENCH_PRESS,
    "Floor Press (Kurzhantel)" to Motion.BENCH_PRESS,
    "Fliegende (Kurzhantel)" to Motion.FLY,
    "Fliegende (Kurzhantel, Negativbank)" to Motion.FLY,
    "Schrägbank-Fliegende (Kurzhantel)" to Motion.FLY,
    "Schrägbank-Fliegende (Kabelzug)" to Motion.FLY,
    "Butterfly (Maschine)" to Motion.FLY,
    "Cable Crossover (Kabelzug)" to Motion.FLY,
    "Cable Crossover (Kabelzug, von oben)" to Motion.FLY,
    "Cable Crossover (Kabelzug, von unten)" to Motion.FLY,
    "Cable Crossover (Kabelzug, einarmig)" to Motion.FLY,
    "Pullover (Kurzhantel)" to Motion.BENCH_PRESS,
    "Dips (Körpergewicht)" to Motion.DIP,
    "Liegestütze (Körpergewicht)" to Motion.PUSH_UP,
    "Liegestütze (Körpergewicht, breit)" to Motion.PUSH_UP,
    "Liegestütze (Körpergewicht, kniend)" to Motion.PUSH_UP,
    "Liegestütze (Körpergewicht, negativ)" to Motion.PUSH_UP,
    "Liegestütze (Körpergewicht, positiv)" to Motion.PUSH_UP,
    // Rücken
    "Kreuzheben (Langhantel)" to Motion.DEADLIFT,
    "Kreuzheben (Kurzhantel)" to Motion.DEADLIFT,
    "Kreuzheben (Kettlebell)" to Motion.DEADLIFT,
    "Kreuzheben (Multipresse)" to Motion.DEADLIFT,
    "Kreuzheben (Trap-Bar)" to Motion.DEADLIFT,
    "Rudern vorgebeugt (Langhantel)" to Motion.ROW_BENT,
    "Rudern vorgebeugt (Langhantel, Untergriff)" to Motion.ROW_BENT,
    "Rudern vorgebeugt (Kurzhantel)" to Motion.ROW_BENT,
    "Einarmiges Rudern (Kurzhantel)" to Motion.ROW_BENT,
    "Einarmiges Rudern (Kabelzug)" to Motion.ROW_BENT,
    "T-Bar-Rudern (Langhantel)" to Motion.ROW_BENT,
    "T-Bar-Rudern (Maschine)" to Motion.ROW_BENT,
    "Brustgestütztes Rudern (Kurzhantel)" to Motion.ROW_BENT,
    "Rudern sitzend (Kabelzug)" to Motion.ROW_SEATED,
    "Rudern sitzend (Kabelzug, breiter Griff)" to Motion.ROW_SEATED,
    "Rudern sitzend (Kabelzug, Untergriff)" to Motion.ROW_SEATED,
    "Rudern sitzend (Kabelzug, Seil)" to Motion.ROW_SEATED,
    "Rudern sitzend (Kabelzug, einarmig)" to Motion.ROW_SEATED,
    "Rudern sitzend (Maschine)" to Motion.ROW_SEATED,
    "Rudern sitzend (Maschine, einarmig)" to Motion.ROW_BENT,
    "Rack Pulls (Langhantel)" to Motion.DEADLIFT,
    "Good Mornings (Langhantel)" to Motion.DEADLIFT,
    "Good Mornings (Theraband)" to Motion.DEADLIFT,
    "Latziehen (Kabelzug)" to Motion.PULLDOWN,
    "Latziehen (Kabelzug, breiter Griff)" to Motion.PULLDOWN,
    "Latziehen (Kabelzug, enger Griff)" to Motion.PULLDOWN,
    "Latziehen (Kabelzug, V-Griff)" to Motion.PULLDOWN,
    "Latziehen (Kabelzug, Untergriff)" to Motion.PULLDOWN,
    "Latziehen (Kabelzug, einarmig)" to Motion.PULLDOWN,
    "Latziehen (Maschine)" to Motion.PULLDOWN,
    "Überzüge (Kabelzug)" to Motion.PUSHDOWN,
    "Klimmzüge (Körpergewicht)" to Motion.PULL_UP,
    "Klimmzüge (Körpergewicht, breiter Griff)" to Motion.PULL_UP,
    "Klimmzüge (Körpergewicht, Parallelgriff)" to Motion.PULL_UP,
    "Klimmzüge (Körpergewicht, Untergriff)" to Motion.PULL_UP,
    "Klimmzüge (Maschine / unterstützt)" to Motion.PULL_UP,
    "Klimmzüge (Widerstandsband)" to Motion.PULL_UP,
    "Australische Klimmzüge (Körpergewicht)" to Motion.ROW_SEATED,
    "Hyperextensions (Körpergewicht)" to Motion.BACK_EXTENSION,
    "Hyperextensions (Maschine)" to Motion.BACK_EXTENSION,
    "Superman (Körpergewicht)" to Motion.BACK_EXTENSION,
    "Bird-Dog (Körpergewicht)" to Motion.PLANK,
    // Beine
    "Kniebeugen (Langhantel)" to Motion.SQUAT,
    "Kniebeugen (Kurzhantel)" to Motion.SQUAT,
    "Kniebeugen (Kettlebell)" to Motion.SQUAT,
    "Kniebeugen (Multipresse)" to Motion.SQUAT,
    "Kniebeugen (Körpergewicht)" to Motion.SQUAT,
    "Frontkniebeugen (Langhantel)" to Motion.SQUAT,
    "Frontkniebeugen (Multipresse)" to Motion.SQUAT,
    "Box Squat (Langhantel)" to Motion.SQUAT,
    "Box Squat (Langhantel, Front)" to Motion.SQUAT,
    "Box Squat (Körpergewicht, einbeinig)" to Motion.SQUAT,
    "Zercher Squat (Langhantel)" to Motion.SQUAT,
    "Sumo Squat (Kurzhantel)" to Motion.SQUAT,
    "Sumo Squat (Langhantel)" to Motion.SQUAT,
    "Sumo Squat (Multipresse)" to Motion.SQUAT,
    "Sumo Squat (Körpergewicht)" to Motion.SQUAT,
    "Einbeinige Kniebeuge (Kurzhantel)" to Motion.SQUAT,
    "Einbeinige Kniebeuge (Körpergewicht, Pistol)" to Motion.SQUAT,
    "Einbeinige Kniebeuge (Körpergewicht, Hawaiian)" to Motion.SQUAT,
    "Einbeinige Kniebeuge (Körpergewicht, Skater)" to Motion.SQUAT,
    "Cossack Squat (Körpergewicht)" to Motion.LUNGE,
    "Goblet Squat (Kurzhantel)" to Motion.SQUAT,
    "Goblet Squat (Kettlebell)" to Motion.SQUAT,
    "Hackenschmidt-Kniebeugen (Maschine)" to Motion.SQUAT,
    "Hackenschmidt-Kniebeugen (Maschine, enger Stand)" to Motion.SQUAT,
    "Wandsitzen (Körpergewicht)" to Motion.SQUAT,
    "Beinpresse (Maschine)" to Motion.LEG_PRESS,
    "Beinpresse (Maschine, breiter Stand)" to Motion.LEG_PRESS,
    "Beinpresse (Maschine, enger Stand)" to Motion.LEG_PRESS,
    "Beinpresse (Maschine, einbeinig)" to Motion.LEG_PRESS,
    "Beinpresse (Maschine, horizontal)" to Motion.LEG_PRESS,
    "Beinpresse (Maschine, horizontal, einbeinig)" to Motion.LEG_PRESS,
    "Beinpresse (Multipresse, vertikal)" to Motion.LEG_PRESS,
    "Rumänisches Kreuzheben (Langhantel)" to Motion.DEADLIFT,
    "Rumänisches Kreuzheben (Langhantel, einbeinig)" to Motion.DEADLIFT,
    "Rumänisches Kreuzheben (Kurzhantel)" to Motion.DEADLIFT,
    "Rumänisches Kreuzheben (Kurzhantel, einbeinig)" to Motion.DEADLIFT,
    "Gestrecktes Kreuzheben (Langhantel)" to Motion.DEADLIFT,
    "Gestrecktes Kreuzheben (Kurzhantel)" to Motion.DEADLIFT,
    "Sumo-Kreuzheben (Langhantel)" to Motion.DEADLIFT,
    "Sumo-Kreuzheben (Kurzhantel)" to Motion.DEADLIFT,
    "Ausfallschritte (Kurzhantel)" to Motion.LUNGE,
    "Ausfallschritte (Langhantel)" to Motion.LUNGE,
    "Ausfallschritte (Körpergewicht)" to Motion.LUNGE,
    "Ausfallschritte (Körpergewicht, High Knee)" to Motion.LUNGE,
    "Ausfallschritte (Theraband)" to Motion.LUNGE,
    "Ausfallschritte gehend (Kurzhantel)" to Motion.LUNGE,
    "Reverse Lunge (Kurzhantel)" to Motion.LUNGE,
    "Reverse Lunge (Langhantel)" to Motion.LUNGE,
    "Reverse Lunge (Multipresse)" to Motion.LUNGE,
    "Reverse Lunge (Kabelzug)" to Motion.LUNGE,
    "Reverse Lunge (Körpergewicht)" to Motion.LUNGE,
    "Seitliche Ausfallschritte (Kurzhantel)" to Motion.LUNGE,
    "Seitliche Ausfallschritte (Langhantel)" to Motion.LUNGE,
    "Seitliche Ausfallschritte (Körpergewicht)" to Motion.LUNGE,
    "Cross Lunge (Kurzhantel)" to Motion.LUNGE,
    "Cross Lunge (Körpergewicht)" to Motion.LUNGE,
    "Split Squat (Kurzhantel)" to Motion.LUNGE,
    "Split Squat (Langhantel)" to Motion.LUNGE,
    "Split Squat (Körpergewicht)" to Motion.LUNGE,
    "Bulgarian Split Squat (Kurzhantel)" to Motion.LUNGE,
    "Bulgarian Split Squat (Langhantel)" to Motion.LUNGE,
    "Bulgarian Split Squat (Multipresse)" to Motion.LUNGE,
    "Bulgarian Split Squat (Körpergewicht)" to Motion.LUNGE,
    "Step-Ups (Kurzhantel)" to Motion.STAIRS,
    "Step-Ups (Kurzhantel, seitlich)" to Motion.STAIRS,
    "Step-Ups (Langhantel)" to Motion.STAIRS,
    "Step-Ups (Körpergewicht)" to Motion.STAIRS,
    "Step-Ups (Körpergewicht, seitlich)" to Motion.STAIRS,
    "Beinstrecken (Maschine)" to Motion.LEG_EXTENSION,
    "Beinstrecken (Maschine, einbeinig)" to Motion.LEG_EXTENSION,
    "Beinstrecken (Kabelzug)" to Motion.LEG_EXTENSION,
    "Beinstrecken (Theraband)" to Motion.LEG_EXTENSION,
    "Beincurls (Maschine)" to Motion.LEG_CURL,
    "Beincurls (Maschine, einbeinig)" to Motion.LEG_CURL,
    "Beincurls (Maschine, liegend)" to Motion.LEG_CURL,
    "Beincurls (Kabelzug, stehend)" to Motion.LEG_CURL,
    "Beincurls (Kurzhantel)" to Motion.LEG_CURL,
    "Beincurls (Theraband)" to Motion.LEG_CURL,
    "Nordic Hamstring Curls (Körpergewicht)" to Motion.LEG_CURL,
    "Nordic Hamstring Curls (Maschine)" to Motion.LEG_CURL,
    "Glute Ham Raises (Maschine)" to Motion.LEG_CURL,
    "Adduktion (Maschine)" to Motion.ABDUCTION,
    "Adduktion (Maschine, umgekehrt)" to Motion.ABDUCTION,
    "Adduktion (Kabelzug)" to Motion.ABDUCTION,
    "Wadenheben stehend (Maschine)" to Motion.CALF_RAISE,
    "Wadenheben stehend (Maschine, Hackenschmidt)" to Motion.CALF_RAISE,
    "Wadenheben stehend (Multipresse)" to Motion.CALF_RAISE,
    "Wadenheben stehend (Langhantel)" to Motion.CALF_RAISE,
    "Wadenheben stehend (Kurzhantel)" to Motion.CALF_RAISE,
    "Wadenheben stehend (Kurzhantel, einbeinig)" to Motion.CALF_RAISE,
    "Wadenheben stehend (Körpergewicht)" to Motion.CALF_RAISE,
    "Wadenheben stehend (Körpergewicht, einbeinig)" to Motion.CALF_RAISE,
    "Wadenheben sitzend (Maschine)" to Motion.CALF_RAISE,
    "Wadenheben sitzend (Multipresse)" to Motion.CALF_RAISE,
    "Wadenheben sitzend (Langhantel)" to Motion.CALF_RAISE,
    "Wadenheben sitzend (Kurzhantel)" to Motion.CALF_RAISE,
    "Wadenheben an der Beinpresse (Maschine)" to Motion.CALF_RAISE,
    "Wadenheben an der Beinpresse (Maschine, horizontal)" to Motion.CALF_RAISE,
    "Donkey Calf Raises (Maschine)" to Motion.CALF_RAISE,
    "Donkey Calf Raises (Körpergewicht, Partner)" to Motion.CALF_RAISE,
    "Donkey Calf Raises (Körpergewicht, einbeinig)" to Motion.CALF_RAISE,
    "Zehenheben (Körpergewicht)" to Motion.CALF_RAISE,
    // Gluteus
    "Hip Thrust (Langhantel)" to Motion.HIP_THRUST,
    "Hip Thrust (Multipresse)" to Motion.HIP_THRUST,
    "Hip Thrust (Maschine)" to Motion.HIP_THRUST,
    "Hip Thrust (Maschine, Beinstrecker)" to Motion.HIP_THRUST,
    "Hip Thrust (Körpergewicht)" to Motion.HIP_THRUST,
    "Hip Thrust (Körpergewicht, einbeinig)" to Motion.HIP_THRUST,
    "Hip Thrust (Theraband)" to Motion.HIP_THRUST,
    "Hip Thrust (Theraband, Kurzhantel)" to Motion.HIP_THRUST,
    "Beckenheben (Körpergewicht)" to Motion.HIP_THRUST,
    "Beckenheben (Körpergewicht, einbeinig)" to Motion.HIP_THRUST,
    "Beckenheben (Langhantel)" to Motion.HIP_THRUST,
    "Frog Pumps (Körpergewicht)" to Motion.HIP_THRUST,
    "Abduktion (Maschine)" to Motion.ABDUCTION,
    "Abduktion (Maschine, stehend)" to Motion.ABDUCTION,
    "Abduktion (Kabelzug)" to Motion.ABDUCTION,
    "Beinspreizen (Körpergewicht, liegend)" to Motion.ABDUCTION,
    "Beinspreizen (Theraband, sitzend)" to Motion.ABDUCTION,
    "Seitliches Beinheben (Körpergewicht, liegend)" to Motion.ABDUCTION,
    "Seitliches Beinheben (Körpergewicht, stehend)" to Motion.ABDUCTION,
    "Seitliches Beinheben (Körpergewicht, Seitstütz)" to Motion.ABDUCTION,
    "Clamshell (Theraband)" to Motion.ABDUCTION,
    "Seitliches Gehen (Widerstandsband)" to Motion.WALK,
    "Monster Walk (Theraband)" to Motion.WALK,
    "Feuerhydrant (Körpergewicht)" to Motion.KICKBACK,
    "Donkey Kicks (Körpergewicht)" to Motion.KICKBACK,
    "Donkey Kicks (Körpergewicht, gestrecktes Bein)" to Motion.KICKBACK,
    "Donkey Kicks (Kabelzug)" to Motion.KICKBACK,
    "Donkey Kicks (Multipresse)" to Motion.KICKBACK,
    "Donkey Kicks (Maschine)" to Motion.KICKBACK,
    "Po-Kickbacks (Kabelzug)" to Motion.KICKBACK,
    "Po-Kickbacks (Theraband)" to Motion.KICKBACK,
    "Po-Kickbacks (Maschine)" to Motion.KICKBACK,
    "Pull-Through (Kabelzug)" to Motion.DEADLIFT,
    "Reverse Hyperextensions (Maschine)" to Motion.BACK_EXTENSION,
    "Reverse Hyperextensions (Multipresse)" to Motion.BACK_EXTENSION,
    "Reverse Hyperextensions (Körpergewicht)" to Motion.BACK_EXTENSION,
    // Schultern
    "Schulterdrücken (Kurzhantel)" to Motion.OVERHEAD_PRESS,
    "Schulterdrücken (Kurzhantel, stehend)" to Motion.OVERHEAD_PRESS,
    "Schulterdrücken (Kurzhantel, einarmig)" to Motion.OVERHEAD_PRESS,
    "Schulterdrücken (Kettlebell)" to Motion.OVERHEAD_PRESS,
    "Schulterdrücken (Kettlebell, einarmig)" to Motion.OVERHEAD_PRESS,
    "Schulterdrücken (Maschine)" to Motion.OVERHEAD_PRESS,
    "Schulterdrücken (Theraband)" to Motion.OVERHEAD_PRESS,
    "Schulterdrücken (Theraband, stehend)" to Motion.OVERHEAD_PRESS,
    "Military Press (Langhantel)" to Motion.OVERHEAD_PRESS,
    "Military Press (Langhantel, sitzend)" to Motion.OVERHEAD_PRESS,
    "Military Press (Multipresse)" to Motion.OVERHEAD_PRESS,
    "Arnold-Press (Kurzhantel)" to Motion.OVERHEAD_PRESS,
    "Arnold-Press (Kettlebell, stehend)" to Motion.OVERHEAD_PRESS,
    "Handstand Push-Up (Körpergewicht)" to Motion.PUSH_UP,
    "Pike Push-Up (Körpergewicht)" to Motion.PUSH_UP,
    "Seitheben (Kurzhantel)" to Motion.LATERAL_RAISE,
    "Seitheben (Kurzhantel, sitzend)" to Motion.LATERAL_RAISE,
    "Seitheben (Kurzhantel, einarmig)" to Motion.LATERAL_RAISE,
    "Seitheben (Kabelzug)" to Motion.LATERAL_RAISE,
    "Seitheben (Kabelzug, einarmig)" to Motion.LATERAL_RAISE,
    "Seitheben (Maschine)" to Motion.LATERAL_RAISE,
    "Frontheben (Kurzhantel)" to Motion.FRONT_RAISE,
    "Frontheben (Kurzhantel, einarmig)" to Motion.FRONT_RAISE,
    "Frontheben (Langhantel)" to Motion.FRONT_RAISE,
    "Frontheben (Langhantel, sitzend)" to Motion.FRONT_RAISE,
    "Frontheben (Hantelscheibe)" to Motion.FRONT_RAISE,
    "Frontheben (Kabelzug)" to Motion.FRONT_RAISE,
    "Frontheben (Theraband)" to Motion.FRONT_RAISE,
    "Vorgebeugtes Seitheben (Kurzhantel)" to Motion.LATERAL_RAISE,
    "Vorgebeugtes Seitheben (Kurzhantel, sitzend)" to Motion.LATERAL_RAISE,
    "Vorgebeugtes Seitheben (Kurzhantel, Schrägbank)" to Motion.LATERAL_RAISE,
    "Butterfly Reverse (Maschine)" to Motion.LATERAL_RAISE,
    "Butterfly Reverse (Kabelzug)" to Motion.LATERAL_RAISE,
    "Butterfly Reverse (Kabelzug, von oben)" to Motion.LATERAL_RAISE,
    "Butterfly Reverse (Kabelzug, von unten)" to Motion.LATERAL_RAISE,
    "Butterfly Reverse (Kabelzug, einarmig)" to Motion.LATERAL_RAISE,
    "Face Pulls (Kabelzug)" to Motion.FACE_PULL,
    "Face Pulls (Theraband)" to Motion.FACE_PULL,
    "Band Pull-Aparts (Widerstandsband)" to Motion.LATERAL_RAISE,
    "Aufrechtes Rudern (Langhantel)" to Motion.SHRUG,
    "Aufrechtes Rudern (Langhantel, breiter Griff)" to Motion.SHRUG,
    "Aufrechtes Rudern (SZ-Stange)" to Motion.SHRUG,
    "Aufrechtes Rudern (Kurzhantel)" to Motion.SHRUG,
    "Aufrechtes Rudern (Kabelzug)" to Motion.SHRUG,
    "Aufrechtes Rudern (Theraband)" to Motion.SHRUG,
    "Shrugs (Langhantel)" to Motion.SHRUG,
    "Shrugs (Kurzhantel)" to Motion.SHRUG,
    "Shrugs (Multipresse)" to Motion.SHRUG,
    "Shrugs (Maschine)" to Motion.SHRUG,
    "Shrugs (Kabelzug)" to Motion.SHRUG,
    "Shrugs (Theraband)" to Motion.SHRUG,
    // Bizeps
    "Bizeps-Curls (Kurzhantel)" to Motion.CURL,
    "Bizeps-Curls (Kurzhantel, sitzend)" to Motion.CURL,
    "Bizeps-Curls (Kurzhantel, Bizeps-Isolator)" to Motion.CURL,
    "Bizeps-Curls (Kurzhantel, einarmig)" to Motion.CURL,
    "Bizeps-Curls (Langhantel)" to Motion.CURL,
    "Bizeps-Curls (Langhantel, breiter Griff)" to Motion.CURL,
    "Bizeps-Curls (Langhantel, enger Griff)" to Motion.CURL,
    "Bizeps-Curls (Langhantel, Arm Blaster)" to Motion.CURL,
    "Bizeps-Curls (SZ-Stange)" to Motion.CURL,
    "Bizeps-Curls (SZ-Stange, sitzend)" to Motion.CURL,
    "Bizeps-Curls (SZ-Stange, Arm Blaster)" to Motion.CURL,
    "Bizeps-Curls (Kabelzug)" to Motion.CURL,
    "Bizeps-Curls (Kabelzug, über Kopf)" to Motion.CURL,
    "Bizeps-Curls (Kabelzug, einarmig)" to Motion.CURL,
    "Bizeps-Curls (Maschine)" to Motion.CURL,
    "Bizeps-Curls (Theraband)" to Motion.CURL,
    "Bizeps-Curls (Theraband, sitzend)" to Motion.CURL,
    "Hammer Curls (Kurzhantel)" to Motion.CURL,
    "Hammer Curls (Kurzhantel, sitzend)" to Motion.CURL,
    "Hammer Curls (Kurzhantel, Schrägbank)" to Motion.CURL,
    "Hammer Curls (Kurzhantel, einarmig)" to Motion.CURL,
    "Hammer Curls (Trizepsstange)" to Motion.CURL,
    "Hammer Curls (Kabelzug)" to Motion.CURL,
    "Hammer Curls (Maschine)" to Motion.CURL,
    "Konzentrationscurls (Kurzhantel)" to Motion.CURL,
    "Konzentrationscurls (SZ-Stange)" to Motion.CURL,
    "Konzentrationscurls (Theraband)" to Motion.CURL,
    "Schrägbank-Curls (Kurzhantel)" to Motion.CURL,
    "Schrägbank-Curls (Kurzhantel, einarmig)" to Motion.CURL,
    "Scott-Curls (Langhantel)" to Motion.CURL,
    "Scott-Curls (SZ-Stange)" to Motion.CURL,
    "Scott-Curls (Kurzhantel)" to Motion.CURL,
    "Scott-Curls (Kurzhantel, einarmig)" to Motion.CURL,
    "Scott-Curls (Kabelzug)" to Motion.CURL,
    "Scott-Curls (Maschine)" to Motion.CURL,
    // Unterarm
    "Reverse Curls (SZ-Stange)" to Motion.CURL,
    "Reverse Curls (Langhantel)" to Motion.CURL,
    "Reverse Curls (Kurzhantel)" to Motion.CURL,
    "Reverse Curls (Kabelzug)" to Motion.CURL,
    "Reverse Curls (Maschine)" to Motion.CURL,
    "Unterarm-Curls (Langhantel)" to Motion.CURL,
    "Unterarm-Curls (Langhantel, hinter dem Rücken)" to Motion.CURL,
    "Unterarm-Curls (Kurzhantel)" to Motion.CURL,
    "Unterarm-Curls (Kurzhantel, einarmig)" to Motion.CURL,
    "Unterarm-Curls (Kabelzug)" to Motion.CURL,
    "Reverse Unterarm-Curls (Langhantel)" to Motion.CURL,
    "Reverse Unterarm-Curls (Kurzhantel)" to Motion.CURL,
    "Reverse Unterarm-Curls (Kabelzug)" to Motion.CURL,
    "Finger-Curls (Langhantel)" to Motion.CURL,
    "Finger-Curls (Langhantel, sitzend)" to Motion.CURL,
    "Finger-Curls (Hantelscheibe)" to Motion.CURL,
    "Wrist Roller (Hantelscheibe)" to Motion.CURL,
    // Trizeps
    "Trizepsdrücken (Kabelzug)" to Motion.PUSHDOWN,
    "Trizepsdrücken (Kabelzug, Seil)" to Motion.PUSHDOWN,
    "Trizepsdrücken (Kabelzug, V-Griff)" to Motion.PUSHDOWN,
    "Trizepsdrücken (Kabelzug, Untergriff)" to Motion.PUSHDOWN,
    "Trizepsdrücken (Kabelzug, einarmig)" to Motion.PUSHDOWN,
    "Trizepsdrücken (Maschine)" to Motion.PUSHDOWN,
    "Trizepsdrücken über Kopf (Kurzhantel)" to Motion.OVERHEAD_EXTENSION,
    "Trizepsdrücken über Kopf (Kurzhantel, einarmig)" to Motion.OVERHEAD_EXTENSION,
    "Trizepsdrücken über Kopf (Kabelzug)" to Motion.OVERHEAD_EXTENSION,
    "Trizepsdrücken über Kopf (Kabelzug, einarmig)" to Motion.OVERHEAD_EXTENSION,
    "Trizeps-Kickbacks (Kurzhantel)" to Motion.PUSHDOWN,
    "Trizeps-Kickbacks (Kurzhantel, einarmig)" to Motion.PUSHDOWN,
    "Trizeps-Kickbacks (Kabelzug)" to Motion.PUSHDOWN,
    "Trizeps-Kickbacks (Theraband)" to Motion.PUSHDOWN,
    "French Press (SZ-Stange)" to Motion.OVERHEAD_EXTENSION,
    "French Press (Langhantel)" to Motion.OVERHEAD_EXTENSION,
    "French Press (Kurzhantel)" to Motion.OVERHEAD_EXTENSION,
    "French Press (Kabelzug)" to Motion.OVERHEAD_EXTENSION,
    "Enges Bankdrücken (Langhantel)" to Motion.BENCH_PRESS,
    "Enges Bankdrücken (SZ-Stange)" to Motion.BENCH_PRESS,
    "Enges Bankdrücken (Multipresse)" to Motion.BENCH_PRESS,
    "Trizeps-Dips (Körpergewicht)" to Motion.DIP,
    "Trizeps-Dips (Zusatzgewicht)" to Motion.DIP,
    "Trizeps-Dips (Maschine)" to Motion.DIP,
    "Arnold Dips (Körpergewicht)" to Motion.DIP,
    "Arnold Dips (Zusatzgewicht)" to Motion.DIP,
    "Bankdips (Körpergewicht)" to Motion.DIP,
    "Enge Liegestütze (Körpergewicht)" to Motion.PUSH_UP,
    "Enge Liegestütze (Körpergewicht, Diamond)" to Motion.PUSH_UP,
    // Bauch
    "Crunches (Körpergewicht)" to Motion.CRUNCH,
    "Crunches (Körpergewicht, Bank)" to Motion.CRUNCH,
    "Crunches (Körpergewicht, Negativbank)" to Motion.CRUNCH,
    "Crunches (Zusatzgewicht)" to Motion.CRUNCH,
    "Crunches (Ball)" to Motion.CRUNCH,
    "Crunches (Kabelzug)" to Motion.CRUNCH,
    "Crunches (Kabelzug, stehend)" to Motion.CRUNCH,
    "Crunches (Kabelzug, sitzend)" to Motion.CRUNCH,
    "Crunches (Maschine)" to Motion.CRUNCH,
    "Crunches (Bauchtrainer)" to Motion.CRUNCH,
    "Butterfly-Crunches (Körpergewicht)" to Motion.CRUNCH,
    "Butterfly-Crunches (Ball)" to Motion.CRUNCH,
    "Reverse Crunches (Körpergewicht)" to Motion.CRUNCH,
    "Reverse Crunches (Körpergewicht, Bank)" to Motion.CRUNCH,
    "Reverse Crunches (Körpergewicht, Schrägbank)" to Motion.CRUNCH,
    "Side Crunches (Körpergewicht)" to Motion.CRUNCH,
    "V-Crunches (Körpergewicht)" to Motion.CRUNCH,
    "Bicycle Crunches (Körpergewicht)" to Motion.CRUNCH,
    "Bicycle Crunches (Körpergewicht, Cross)" to Motion.CRUNCH,
    "Heel-Touches (Körpergewicht)" to Motion.CRUNCH,
    "Toe-Touches (Körpergewicht)" to Motion.CRUNCH,
    "Toe-Touches (Körpergewicht, sitzend)" to Motion.CRUNCH,
    "Toe-Touches (Körpergewicht, Windmühle)" to Motion.TWIST,
    "Side Plank Crunches (Körpergewicht)" to Motion.PLANK,
    "Side Plank Crunches (Körpergewicht, Ellenbogen zum Knie)" to Motion.PLANK,
    "Klappmesser (Körpergewicht)" to Motion.CRUNCH,
    "Klappmesser (Körpergewicht, Split)" to Motion.CRUNCH,
    "Klappmesser (Ball)" to Motion.CRUNCH,
    "V-Ups (Körpergewicht)" to Motion.CRUNCH,
    "Sit-Ups (Körpergewicht)" to Motion.CRUNCH,
    "Sit-Ups (Körpergewicht, Butterfly)" to Motion.CRUNCH,
    "Sit-Ups (Körpergewicht, Negativbank)" to Motion.CRUNCH,
    "Sit-Ups (Körpergewicht, seitlich)" to Motion.CRUNCH,
    "Sit-Ups (Körpergewicht, Partner)" to Motion.CRUNCH,
    "Sit-Ups (Ball)" to Motion.CRUNCH,
    "Unterarmstütz / Plank (Körpergewicht)" to Motion.PLANK,
    "Seitstütz (Körpergewicht)" to Motion.PLANK,
    "Spiderman Plank (Körpergewicht)" to Motion.PLANK,
    "Seitliches Hüftheben (Körpergewicht)" to Motion.PLANK,
    "Seitliches Hüftheben (Körpergewicht, Bank)" to Motion.PLANK,
    "Hüftheben (Körpergewicht)" to Motion.CRUNCH,
    "Hüftheben (Körpergewicht, Bank)" to Motion.CRUNCH,
    "Hüftheben (Körpergewicht, Hip-Lift)" to Motion.CRUNCH,
    "Beinheben liegend (Körpergewicht)" to Motion.CRUNCH,
    "Beinheben liegend (Körpergewicht, Hüftrolle)" to Motion.CRUNCH,
    "Beinheben liegend (Körpergewicht, Kerze)" to Motion.CRUNCH,
    "Beinheben liegend (Körpergewicht, Partner)" to Motion.CRUNCH,
    "Knieheben im Liegen (Körpergewicht)" to Motion.CRUNCH,
    "Flutter Kicks (Körpergewicht)" to Motion.CRUNCH,
    "Flutter Kicks (Körpergewicht, Po)" to Motion.KICKBACK,
    "Beinschere (Körpergewicht)" to Motion.CRUNCH,
    "Beinheben hängend (Körpergewicht)" to Motion.HANGING_RAISE,
    "Beinheben hängend (Körpergewicht, seitlich)" to Motion.HANGING_RAISE,
    "Knieheben am Gerät (Körpergewicht)" to Motion.HANGING_RAISE,
    "Knieheben am Gerät (Körpergewicht, gestreckte Beine)" to Motion.HANGING_RAISE,
    "Knieheben am Gerät (Körpergewicht, seitlich)" to Motion.HANGING_RAISE,
    "Mountain Climbers (Körpergewicht)" to Motion.PUSH_UP,
    "Dead Bug (Körpergewicht)" to Motion.CRUNCH,
    "Bauchroller (Körpergewicht)" to Motion.PLANK,
    "Bauchroller (Langhantel)" to Motion.PLANK,
    "Russian Twist (Körpergewicht)" to Motion.TWIST,
    "Russian Twist (Kurzhantel)" to Motion.TWIST,
    "Russian Twist (Körpergewicht, Partner)" to Motion.TWIST,
    "Hüftrollen (Körpergewicht)" to Motion.TWIST,
    "Rumpfrotation (Kabelzug)" to Motion.TWIST,
    "Rumpfrotation (Kabelzug, nach oben)" to Motion.TWIST,
    "Rumpfrotation (Kabelzug, seitlich nach unten)" to Motion.TWIST,
    "Rumpfrotation (Kabelzug, stehend)" to Motion.TWIST,
    "Rumpfrotation (Kabelzug, sitzend)" to Motion.TWIST,
    "Rumpfrotation (Langhantel)" to Motion.TWIST,
    "Rumpfrotation (Langhantel, sitzend)" to Motion.TWIST,
    "Rumpfrotation (Maschine)" to Motion.TWIST,
    "Seitliches Oberkörperbeugen (Kurzhantel)" to Motion.TWIST,
    "Seitliches Oberkörperbeugen (Langhantel)" to Motion.TWIST,
    "Seitliches Oberkörperbeugen (Kabelzug)" to Motion.TWIST,
    "Seitliches Oberkörperbeugen (Körpergewicht)" to Motion.TWIST,
    "Seitliches Oberkörperbeugen (Hyperextension-Bank)" to Motion.TWIST,
    "Pallof Press (Kabelzug)" to Motion.TWIST,
    "Pallof Press (Widerstandsband)" to Motion.TWIST,
    // Ganzkörper
    "Swing (Kettlebell)" to Motion.KETTLEBELL_SWING,
    "Swing (Kettlebell, einarmig)" to Motion.KETTLEBELL_SWING,
    "Turkish Get-up (Kettlebell)" to Motion.OLYMPIC,
    "Clean & Press (Kettlebell)" to Motion.OLYMPIC,
    "Thruster (Langhantel)" to Motion.OLYMPIC,
    "Thruster (Kurzhantel)" to Motion.OLYMPIC,
    "Umsetzen / Power Clean (Langhantel)" to Motion.OLYMPIC,
    "Reißen / Snatch (Langhantel)" to Motion.OLYMPIC,
    "Farmer's Walk (Kurzhantel)" to Motion.CARRY,
    "Farmer's Walk (Kurzhantel, einarmig)" to Motion.CARRY,
    "Farmer's Walk (Kettlebell)" to Motion.CARRY,
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
    "Battle Ropes" to Motion.BATTLE_ROPES
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
