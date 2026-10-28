// Builds src/globe-land.js. Run once outside the app, in a folder with world-atlas@2, topojson-client@3 and d3-geo@3 installed:
//   node globe-land.mjs <path-to>/frontend/src/globe-land.js
import fs from 'fs';
import { feature } from 'topojson-client';
import { geoContains } from 'd3-geo';
const topo = JSON.parse(fs.readFileSync('node_modules/world-atlas/land-50m.json'));
const land = feature(topo, topo.objects.land);
const step = 2.0, rows = [];
let n = 0;
for (let lat = -58; lat <= 82; lat += step) {
  const count = Math.max(1, Math.round(360 * Math.cos(lat * Math.PI / 180) / step));
  const hits = [];
  for (let i = 0; i < count; i++) {
    const lon = -180 + (i + 0.5) * 360 / count;
    if (geoContains(land, [lon, lat])) hits.push(i);
  }
  n += hits.length;
  // row: lat, column count, then indices of land columns
  rows.push([lat, count, ...hits]);
}
const out = `// Land dots for the Echo globe, sampled every ${step}° from Natural Earth 1:50m land (public domain)\n// via the world-atlas package. Each row: latitude, columns in the ring, then the columns that are land.\n// Regenerate with frontend/scripts/globe-land.mjs if the sampling changes.\nexport const LAND_ROWS = ${JSON.stringify(rows)};\n`;
fs.writeFileSync(process.argv[2], out);
console.log(n, out.length);
