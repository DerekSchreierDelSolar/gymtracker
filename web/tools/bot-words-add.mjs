// Einmalig: ergänzt bot.json um Stichwörter auf Französisch, Italienisch, Spanisch und Türkisch
// (ohne Akzente – Bot.norm entfernt sie ohnehin). Mehrfaches Ausführen schadet nicht (keine Duplikate).
// Aufruf: node web/tools/bot-words-add.mjs && node web/tools/gen-data.mjs

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const file = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'app/src/main/assets/bot.json');
let text = readFileSync(file, 'utf8');
const bot = JSON.parse(text);

const words = {
  exerciseWords: ['exercice', 'exercices', 'entrainer', 'esercizio', 'esercizi', 'allenare', 'ejercicio', 'ejercicios', 'entrenar', 'egzersiz', 'egzersizler', 'calistirmak'],
  muscleWords: ['muscle', 'muscles', 'quels muscles', 'muscolo', 'muscoli', 'quali muscoli', 'musculo', 'musculos', 'que musculos', 'kas', 'kaslar', 'hangi kaslar', 'calistirir'],
  howWords: ['comment', 'technique', 'execution', 'come si fa', 'come fare', 'come eseguire', 'tecnica', 'esecuzione', 'como', 'tecnica', 'ejecucion', 'nasil', 'teknik', 'yapilir'],
  tipWords: ['conseil', 'conseils', 'erreurs', 'consiglio', 'consigli', 'errori', 'consejo', 'consejos', 'errores', 'ipucu', 'ipuclari', 'hatalar'],
};
const anatomy = {
  anatomy: ['origine', 'nerf', 'anatomie', 'latin', 'origine', 'inserzione', 'nervo', 'anatomia', 'latino', 'origen', 'insercion', 'nervio', 'anatomia', 'baslangic', 'yapisma', 'sinir', 'anatomi', 'latince'],
  function: ['fonction', 'a quoi sert', 'funzione', 'a cosa serve', 'funcion', 'para que sirve', 'fonksiyon', 'gorevi', 'ne ise yarar'],
  problems: ['blessure', 'blessures', 'douleur', 'claquage', 'crampe', 'infortunio', 'lesione', 'dolore', 'stiramento', 'crampo', 'lesion', 'dolor', 'tirón', 'calambre', 'sakatlik', 'agri', 'zorlanma', 'yirtik', 'kramp'],
  stretch: ['etirer', 'etirement', 'etirements', 'allungare', 'allungamento', 'stretching', 'estirar', 'estiramiento', 'esneme', 'esnetme', 'germe'],
  general: ["qu est ce que", 'ou se trouve', 'medical', "cos e", 'dove si trova', 'medico', 'que es', 'donde esta', 'medico', 'nedir', 'nerede', 'tibbi'],
};
const targets = {
  'chest.upper': ['haut des pectoraux', 'pectoraux superieurs', 'petto alto', 'pettorale alto', 'pecho superior', 'pecho alto', 'ust gogus'],
  'chest.mid': ['milieu des pectoraux', 'petto medio', 'pecho medio', 'orta gogus'],
  'chest.lower': ['bas des pectoraux', 'pectoraux inferieurs', 'petto basso', 'pecho inferior', 'pecho bajo', 'alt gogus'],
  'delts.front': ['deltoide anterieur', 'epaule avant', 'deltoide anteriore', 'spalla anteriore', 'deltoides anterior', 'hombro frontal', 'on omuz'],
  'delts.side': ['deltoide lateral', 'epaule laterale', 'deltoide laterale', 'spalla laterale', 'deltoides lateral', 'hombro lateral', 'yan omuz'],
  'delts.rear': ['deltoide posterieur', 'arriere d epaule', 'deltoide posteriore', 'spalla posteriore', 'deltoides posterior', 'hombro posterior', 'arka omuz'],
  'biceps.brachialis': ['brachial', 'brachiale', 'braquial'],
  'triceps.long': ['chef long', 'capo lungo', 'cabeza larga', 'uzun bas'],
  'traps.upper': ['trapeze superieur', 'nuque', 'trapezio superiore', 'collo', 'trapecio superior', 'cuello', 'ust trapez', 'boyun'],
  'traps.middle': ['trapeze moyen', 'trapezio medio', 'trapecio medio', 'orta trapez'],
  'lats.upper': ['haut du dos', 'parte alta della schiena', 'espalda alta', 'ust sirt'],
  'lats.lower': ['grand dorsal inferieur', 'dorsale basso', 'dorsal inferior', 'alt kanat'],
  'abs.upper': ['abdos superieurs', 'addominali alti', 'abdominales superiores', 'ust karin'],
  'abs.lower': ['abdos inferieurs', 'bas du ventre', 'addominali bassi', 'abdominales inferiores', 'alt karin'],
  'glutes.med': ['moyen fessier', 'gluteo medio', 'gluteo medio', 'orta kalca'],
  'glutes.max': ['grand fessier', 'grande gluteo', 'gluteo mayor', 'buyuk kalca'],
  'quads.middle': ['droit femoral', 'retto femorale', 'recto femoral'],
  chest: ['pectoraux', 'pecs', 'poitrine', 'petto', 'pettorali', 'pecho', 'pectorales', 'gogus'],
  delts: ['epaule', 'epaules', 'deltoides', 'spalla', 'spalle', 'deltoidi', 'hombro', 'hombros', 'omuz', 'omuzlar'],
  biceps: ['bicipite', 'bicipiti', 'biseps'],
  triceps: ['tricipite', 'tricipiti', 'triseps'],
  forearms: ['avant bras', 'prise', 'avambraccio', 'avambracci', 'presa', 'antebrazo', 'antebrazos', 'agarre', 'on kol', 'on kollar', 'kavrama'],
  traps: ['trapeze', 'trapezes', 'trapezio', 'trapecio', 'trapez'],
  lats: ['grand dorsal', 'dorsaux', 'du dos', 'le dos', 'dorsale', 'dorsali', 'schiena', 'dorsal', 'dorsales', 'espalda', 'kanat', 'sirt'],
  lowerback: ['bas du dos', 'lombaires', 'zona lombare', 'lombari', 'espalda baja', 'lumbares', 'bel', 'alt sirt'],
  abs: ['abdos', 'abdominaux', 'ventre', 'addominali', 'addome', 'abdominales', 'abdomen', 'karin', 'karin kaslari'],
  obliques: ['obliques', 'obliqui', 'oblicuos', 'yan karin'],
  glutes: ['fessiers', 'fesses', 'glutei', 'sedere', 'gluteos', 'trasero', 'kalca', 'popo'],
  quads: ['quadriceps', 'cuisses', 'quadricipiti', 'cosce', 'cuadriceps', 'muslos', 'on bacak', 'kuadriseps'],
  hamstrings: ['ischio jambiers', 'ischios', 'femorali', 'bicipite femorale', 'isquiotibiales', 'femorales', 'arka bacak', 'hamstring'],
  adductors: ['adducteurs', 'adduttori', 'aductores', 'ic bacak', 'adduktor'],
  calves: ['mollet', 'mollets', 'polpaccio', 'polpacci', 'gemelo', 'gemelos', 'pantorrilla', 'baldir'],
};
const groups = [
  ['jambes', 'jambe', 'gambe', 'gamba', 'piernas', 'pierna', 'bacak', 'bacaklar', 'bacak gunu'],
  ['bras', 'braccia', 'brazos', 'kol', 'kollar'],
  ['du dos', 'le dos', 'schiena', 'espalda', 'sirt'],
  ['abdos', 'addominali', 'abdominales', 'karin'],
  ['haut du corps', 'parte superiore', 'tren superior', 'ust vucut'],
  ['bas du corps', 'parte inferiore', 'tren inferior', 'alt vucut'],
  ['corps entier', 'full body', 'total body', 'cuerpo completo', 'tum vucut'],
  ['pousser', 'spinta', 'empuje', 'itme'],
  ['tirer', 'tirata', 'tiron', 'cekme'],
];
const faq = [
  ['ajouter une serie', 'enregistrer une serie', 'aggiungere serie', 'registrare serie', 'anadir serie', 'registrar serie', 'set ekle', 'set kaydet'],
  ['serie d echauffement', 'echauffement', 'jusqu a l echec', 'type de serie', 'riscaldamento', 'cedimento', 'tipo di serie', 'calentamiento', 'al fallo', 'tipo de serie', 'isinma', 'tukenis', 'set turu'],
  ['modifier', 'supprimer', 'corriger', 'modificare', 'eliminare', 'correggere', 'editar', 'eliminar', 'corregir', 'duzenle', 'sil', 'duzelt'],
  ['retirer exercice', 'masquer', 'rimuovere esercizio', 'nascondere', 'quitar ejercicio', 'ocultar', 'egzersizi kaldir', 'gizle'],
  ['note', 'notes', 'nota', 'notas', 'notlar', 'not ekle'],
  ['chronometre', 'secondes', 'gainage', 'cronometro', 'secondi', 'segundos', 'kronometre', 'saniye'],
  ['tapis de course', 'endurance', 'tapis roulant', 'resistenza', 'cinta de correr', 'resistencia', 'kosu bandi', 'dayaniklilik', 'kardiyo'],
  ['commencer la seance', 'demarrer', 'iniziare allenamento', 'avviare', 'empezar entrenamiento', 'iniciar', 'antrenmani baslat', 'baslat'],
  ['repos', 'minuteur de repos', 'temps de repos', 'recupero', 'timer recupero', 'descanso', 'temporizador', 'dinlenme', 'dinlenme suresi'],
  ['reduire', 'seance en cours', 'minimizzare', 'allenamento in corso', 'minimizar', 'entrenamiento en curso', 'kucult', 'devam eden antrenman'],
  ['cercles', 'objectif de repetitions', 'cerchi', 'obiettivo ripetizioni', 'circulos', 'objetivo de repeticiones', 'daireler', 'tekrar hedefi'],
  ['record', 'records', 'meilleure performance', 'primato', 'record personale', 'record', 'marca personal', 'rekor', 'rekorlar', 'kisisel rekor'],
  ['suggestion', 'augmenter le poids', 'suggerimento', 'aumentare peso', 'sugerencia', 'aumentar peso', 'oneri', 'agirlik artir'],
  ['creer un workout', 'nouveau workout', 'modele', 'creare workout', 'nuovo workout', 'modello', 'crear rutina', 'nueva rutina', 'plantilla', 'program olustur', 'yeni program', 'sablon'],
  ['series cibles', 'objectif', 'serie obiettivo', 'obiettivo', 'series objetivo', 'objetivo', 'hedef set', 'hedef', 'istege bagli'],
  ['continuer avec', 'prochain workout', 'continua con', 'prossimo workout', 'continuar con', 'siguiente rutina', 'devam et', 'sonraki program'],
  ['exercice personnalise', 'creer exercice', 'esercizio personalizzato', 'creare esercizio', 'ejercicio personalizado', 'crear ejercicio', 'ozel egzersiz', 'egzersiz olustur'],
  ['catalogue', 'ajouter exercice', 'catalogo', 'aggiungere esercizio', 'anadir ejercicio', 'katalog', 'egzersiz ekle'],
  ['explication', 'spiegazione', 'explicacion', 'aciklama', 'rehber'],
  ['wiki muscles', 'anatomie', 'wiki muscoli', 'anatomia', 'wiki musculos', 'kas wiki', 'anatomi'],
  ['bilan', 'semaine derniere', 'mois dernier', 'riepilogo', 'settimana scorsa', 'mese scorso', 'resumen', 'semana pasada', 'mes pasado', 'ozet', 'gecen hafta', 'gecen ay'],
  ['complement', 'complements', 'complements alimentaires', 'integratore', 'integratori', 'suplemento', 'suplementos', 'takviye', 'takviyeler'],
  ['carte de chaleur', 'muscles entraines', 'mappa di calore', 'muscoli allenati', 'mapa de calor', 'musculos entrenados', 'isi haritasi', 'calisan kaslar'],
  ['couleurs', 'rouge', 'jaune', 'colori', 'rosso', 'giallo', 'colores', 'rojo', 'amarillo', 'renkler', 'kirmizi', 'sari'],
  ['objectif hebdo', 'objectif hebdomadaire', 'obiettivo settimanale', 'objetivo semanal', 'haftalik hedef'],
  ['mes objectifs', 'fixer un objectif', 'objectifs', 'perdre du poids', 'i miei obiettivi', 'obiettivi', 'dimagrire', 'mis objetivos', 'objetivos', 'perder peso', 'hedeflerim', 'hedef belirle', 'kilo vermek'],
  ['succes', 'confettis', 'resume de la seance', 'traguardi', 'coriandoli', 'riepilogo allenamento', 'logros', 'confeti', 'resumen del entrenamiento', 'basarilar', 'konfeti'],
  ['etape', 'etapes', 'prochaine etape', 'traguardo', 'prossimo traguardo', 'hito', 'hitos', 'proximo hito', 'kilometre tasi', 'siradaki'],
  ['hier', 'derniere seance', 'nouveau depart', 'ieri', 'ultimo allenamento', 'nuovo inizio', 'ayer', 'ultimo entrenamiento', 'nuevo comienzo', 'dun', 'son antrenman', 'yeni baslangic'],
  ['niveau', 'points', 'livello', 'punti', 'nivel', 'puntos', 'seviye', 'puan', 'unvan'],
  ['badges', 'badge secret', 'medaille', 'badge segreti', 'medaglia', 'insignia', 'insignias', 'medalla', 'rozet', 'rozetler', 'madalya'],
  ['series d echauffement', 'rampe d echauffement', 'serie di riscaldamento', 'series de calentamiento', 'isinma setleri'],
  ['repetitions en reserve', 'effort', 'tableau 1rm', 'ripetizioni in riserva', 'sforzo', 'tabella 1rm', 'repeticiones en reserva', 'esfuerzo', 'tabla 1rm', 'yedek tekrar', 'efor', '1rm tablosu'],
  ['favori', 'favoris', 'etoile', 'preferito', 'preferiti', 'stella', 'favorito', 'favoritos', 'estrella', 'favori', 'favoriler', 'yildiz'],
  ['prise', 'prise large', 'prise serree', 'corde', 'presa', 'presa larga', 'presa stretta', 'corda', 'agarre', 'agarre ancho', 'agarre cerrado', 'cuerda', 'tutus', 'genis tutus', 'dar tutus', 'halat'],
  ['superset', 'superserie', 'superseries', 'serie gigante', 'super set', 'dev set'],
  ['decharge', 'bloc d entrainement', 'surentrainement', 'scarico', 'blocco', 'sovrallenamento', 'descarga', 'bloque', 'sobreentrenamiento', 'antrenman blogu', 'asiri antrenman'],
  ['mensurations', 'tour de taille', 'masse grasse', 'misure', 'girovita', 'grasso corporeo', 'medidas', 'cintura', 'grasa corporal', 'olculer', 'bel cevresi', 'vucut yagi'],
  ['mon annee', 'bilan annuel', 'il mio anno', 'riepilogo annuale', 'mi ano', 'resumen anual', 'yilim', 'yillik ozet'],
  ['serie de semaines', 'semaines d affilee', 'joker', 'vacances', 'malade', 'serie di settimane', 'jolly', 'vacanza', 'malato', 'racha', 'comodin', 'vacaciones', 'enfermo', 'seri', 'tatil', 'hasta'],
  ['calendrier', 'anneaux', 'calendario', 'anelli', 'anillos', 'takvim', 'halkalar'],
  ['seance libre', 'sans workout', 'allenamento libero', 'entrenamiento libre', 'serbest antrenman'],
  ['resume', 'apercu', 'comparaison', 'panoramica', 'confronto', 'comparacion', 'genel bakis', 'karsilastirma'],
  ['progression', 'graphique', 'progressi', 'grafico', 'progreso', 'ilerleme', 'grafik'],
  ['poids corporel', 'pesee', 'ma balance', 'peso corporeo', 'bilancia', 'peso corporal', 'bascula', 'vucut agirligi', 'tarti'],
  ['calories', 'depense', 'profil', 'taille', 'calorie', 'consumo', 'profilo', 'altezza', 'calorias', 'gasto', 'perfil', 'altura', 'kalori', 'boyum', 'boy uzunlugu'],
  ['exporter', 'importer', 'sauvegarde', 'esportare', 'importare', 'esportazione', 'exportar', 'importar', 'copia de seguridad', 'disa aktar', 'ice aktar', 'yedek', 'yedekleme'],
  ['planning', 'jours d entrainement', 'rappel', 'notification', 'piano', 'giorni di allenamento', 'promemoria', 'notifica', 'plan', 'dias de entrenamiento', 'recordatorio', 'notificacion', 'antrenman gunleri', 'hatirlatici', 'bildirim'],
  ['quelles donnees', 'confidentialite health connect', 'quali dati', 'privacy health connect', 'que datos', 'privacidad health connect', 'hangi veriler', 'health connect gizlilik'],
  ['balance connectee', 'synchroniser le poids', 'bilancia smart', 'sincronizzare peso', 'bascula inteligente', 'sincronizar peso', 'akilli tarti', 'agirlik senkronize'],
  ['exporter les seances', 'seances en double', 'esportare allenamenti', 'allenamenti doppi', 'exportar entrenamientos', 'entrenamientos duplicados', 'antrenmanlari aktar', 'cift antrenman'],
  ['frequence cardiaque', 'pouls', 'montre', 'frequenza cardiaca', 'battito', 'orologio', 'frecuencia cardiaca', 'pulso', 'reloj', 'nabiz', 'kalp atisi', 'saat'],
  ['zones cardiaques', 'frequence cardiaque max', 'zone cardiache', 'frequenza massima', 'zonas de frecuencia', 'pulso maximo', 'nabiz bolgeleri', 'maksimum nabiz'],
  ['calories frequence cardiaque', 'calorie dal battito', 'calorias pulso', 'nabizdan kalori'],
  ['autorisation', 'autorisations', 'permesso', 'permessi', 'permiso', 'permisos', 'izin', 'izinler'],
  ['apple health', 'healthkit', 'apple watch'],
  ['recuperation', 'frequence cardiaque au repos', 'sommeil', 'recupero', 'frequenza a riposo', 'sonno', 'recuperacion', 'pulso en reposo', 'sueno', 'toparlanma', 'dinlenik nabiz', 'uyku'],
  ['pouls par serie', 'repos trop court', 'battito per serie', 'recupero troppo breve', 'pulso por serie', 'descanso demasiado corto', 'set basina nabiz', 'dinlenme cok kisa'],
  ['health connect', 'synchroniser', 'autres applis', 'sincronizzare', 'altre app', 'sincronizar', 'otras apps', 'senkronize', 'diger uygulamalar'],
  ['reinitialiser', 'tout supprimer', 'recommencer', 'reimpostare', 'eliminare tutto', 'ricominciare', 'restablecer', 'borrar todo', 'empezar de nuevo', 'sifirla', 'hepsini sil', 'bastan basla'],
  ['modifier le workout', 'pendant la seance', 'modificare workout', 'durante allenamento', 'editar rutina', 'durante el entrenamiento', 'programi duzenle', 'antrenman sirasinda'],
  ['alternative', 'remplacer', 'echanger', 'alternativa', 'sostituire', 'reemplazar', 'cambiar ejercicio', 'alternatif', 'degistir'],
  ['sauvegarde automatique', 'dossier', 'backup automatico', 'cartella', 'copia automatica', 'carpeta', 'otomatik yedek', 'klasor'],
  ['nouveau telephone', 'transferer', 'nuovo telefono', 'trasferire', 'nuevo movil', 'transferir', 'yeni telefon', 'aktarmak'],
  ['confidentialite', 'compte', 'donnees securisees', 'riservatezza', 'conto', 'dati sicuri', 'privacidad', 'cuenta', 'datos seguros', 'gizlilik', 'hesap', 'verilerim guvende'],
  ['langue', 'francais', 'lingua', 'italiano', 'idioma', 'espanol', 'dil', 'turkce'],
  ['livres', 'unite', 'libbre', 'unita', 'libras', 'unidad', 'libre', 'birim'],
  ['mode sombre', 'sombre', 'theme', 'modalita scura', 'scuro', 'tema', 'modo oscuro', 'oscuro', 'karanlik mod', 'koyu', 'tema'],
  ['ecran d accueil', 'installer', 'schermata home', 'installare', 'pantalla de inicio', 'instalar', 'ana ekran', 'yukle'],
];

// Nur die betroffene Liste im Text ersetzen (bot.json hat eigene Formatierung, so bleibt der Diff klein)
const inline = list => '[' + list.map(x => JSON.stringify(x)).join(', ') + ']';
const add = (list, more) => {
  const old = inline(list);
  if (text.split(old).length !== 2) throw new Error('Liste nicht eindeutig im Text: ' + old.slice(0, 60));
  for (const w of more) if (!list.includes(w)) list.push(w);
  text = text.replace(old, () => inline(list));
};
for (const [k, v] of Object.entries(words)) add(bot[k], v);
for (const [k, v] of Object.entries(anatomy)) add(bot.anatomyWords[k], v);
for (const [k, v] of Object.entries(targets)) { if (!bot.targets[k]) throw new Error('unbekannt: ' + k); add(bot.targets[k], v); }
if (groups.length !== bot.workoutGroups.length || faq.length !== bot.faq.length) throw new Error('Anzahl passt nicht');
groups.forEach((g, i) => add(bot.workoutGroups[i].words, g));
faq.forEach((f, i) => add(bot.faq[i].keys, f));
JSON.parse(text); // noch gültig?
writeFileSync(file, text);
console.log('bot.json ergänzt');
