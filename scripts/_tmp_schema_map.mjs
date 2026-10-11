// One-off: map every JPA entity to its table and location, plus each service's H2 datasource.
import fs from 'node:fs';
import path from 'node:path';

const svcs = ['user-service', 'product-service', 'order-service', 'inventory-Service', 'payment-service', 'ai-customer-service'];
const found = {};

function check(file, svc) {
  const s = fs.readFileSync(file, 'utf8');
  if (!s.includes('@Entity')) return;
  if (!/^\s*(public\s+)?(abstract\s+)?class\s/m.test(s)) return;
  const cls = (s.match(/class\s+([A-Za-z0-9_]+)/) || [])[1];
  const table = (s.match(/@Table\(\s*name\s*=\s*'([^']+)'/) || s.match(/@Table\(\s*name\s*=\s*"([^"]+)"/) || [])[1] || '(class name)';
  (found[svc] = found[svc] || []).push([table, cls, path.relative(process.cwd(), file)]);
}

function walk(dir, svc) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const f = path.join(dir, e.name);
    if (e.isDirectory()) walk(f, svc);
    else if (e.name.endsWith('.java')) check(f, svc);
  }
}

for (const svc of svcs) walk(path.join(svc, 'src', 'main', 'java'), svc);

for (const svc of svcs) {
  console.log('== ' + svc);
  (found[svc] || []).sort().forEach((r) => console.log('   table=' + r[0].padEnd(26) + ' entity=' + r[1].padEnd(24) + r[2]));
}

console.log('');
console.log('== H2 datasource per service');
for (const svc of svcs) {
  const y = fs.readFileSync(path.join(svc, 'src', 'main', 'resources', 'application.yml'), 'utf8');
  const m = y.match(/jdbc:h2:\S+/);
  const ddl = y.match(/ddl-auto:\s*(\S+)/);
  console.log('   ' + svc.padEnd(20) + (m ? m[0] : 'n/a') + '   ddl-auto=' + (ddl ? ddl[1] : 'n/a'));
}
