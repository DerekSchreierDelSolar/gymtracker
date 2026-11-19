// Mini-Webserver zum lokalen Testen (keine Abhängigkeiten).
// Aufruf im Projektordner:  node web/tools/serve.mjs [port]
// Dann im Browser http://localhost:8080 öffnen – oder vom Handy im selben WLAN http://<PC-IP>:8080
// Hinweis: „Zum Home-Bildschirm“ + Offline-Modus funktionieren auf dem iPhone nur über HTTPS
// (z. B. GitHub Pages); zum Ausprobieren der Oberfläche reicht HTTP.

import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { networkInterfaces } from 'node:os';
import { join, extname, normalize, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const port = Number(process.argv[2] ?? 8080);
const types = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.json': 'application/json', '.webmanifest': 'application/manifest+json', '.png': 'image/png', '.svg': 'image/svg+xml',
};

createServer(async (req, res) => {
  let path = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  if (path.endsWith('/')) path += 'index.html';
  const file = normalize(join(root, path));
  if (!file.startsWith(root) || file.includes(`${join(root, 'tools')}`)) { res.writeHead(403).end(); return; }
  try {
    const data = await readFile(file);
    res.writeHead(200, { 'Content-Type': types[extname(file)] ?? 'application/octet-stream', 'Cache-Control': 'no-cache' });
    res.end(data);
  } catch {
    res.writeHead(404).end('Nicht gefunden');
  }
}).listen(port, () => {
  console.log(`GymTracker läuft auf http://localhost:${port}`);
  for (const nets of Object.values(networkInterfaces())) {
    for (const n of nets ?? []) if (n.family === 'IPv4' && !n.internal) console.log(`  im WLAN: http://${n.address}:${port}`);
  }
});
