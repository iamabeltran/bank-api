// Prueba de carga controlada con k6 contra una instancia LOCAL de bank-api.
//
//   Dispersa (10 cuentas):      k6 run load-test/transfers.js
//   Caso crítico (2 cuentas):   k6 run -e ACCOUNTS=2 load-test/transfers.js
//   Guardar resumen:            k6 run --summary-export=load-test/results-spread.json load-test/transfers.js
//
// Requisitos: la app corriendo en BASE_URL (por defecto http://localhost:8080) y MySQL en Docker.
// Cada transferencia es de 1 MXN con una Idempotency-Key única; las cuentas arrancan con 1,000,000.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const ACCOUNTS = parseInt(__ENV.ACCOUNTS || '10');
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  stages: [
    { duration: '10s', target: 10 },  // calentamiento de la JVM y del pool
    { duration: '40s', target: 10 },  // carga sostenida
    { duration: '5s', target: 0 },
  ],
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate<0.01'],     // menos de 1% de errores
    http_req_duration: ['p(95)<500'],   // ajusta según lo que midas; es un umbral de ejemplo
  },
};

export function setup() {
  const ids = [];
  for (let i = 0; i < ACCOUNTS; i++) {
    const created = http.post(`${BASE}/accounts`,
      JSON.stringify({ ownerName: `load-${i}`, currency: 'MXN' }), { headers: JSON_HEADERS });
    const id = created.json('id');
    http.post(`${BASE}/accounts/${id}/deposits`,
      JSON.stringify({ amount: 1000000 }), { headers: JSON_HEADERS });
    ids.push(id);
  }
  return { ids };
}

export default function (data) {
  const n = data.ids.length;
  const a = Math.floor(Math.random() * n);
  let b = Math.floor(Math.random() * (n - 1));
  if (b >= a) b++;  // origen y destino distintos

  const res = http.post(
    `${BASE}/transfers`,
    JSON.stringify({ fromAccountId: data.ids[a], toAccountId: data.ids[b], amount: 1 }),
    {
      headers: Object.assign(
        { 'Idempotency-Key': `${__VU}-${__ITER}-${Date.now()}-${Math.random()}` }, JSON_HEADERS),
      tags: { name: 'POST /transfers' },
    });

  check(res, { 'transfer created (201)': (r) => r.status === 201 });
}
