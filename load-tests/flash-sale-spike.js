import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

// Custom metrics
export const reservedCounter = new Counter('flashsale_reservations_created');
export const outOfStockCounter = new Counter('flashsale_reservations_rejected');
export const errorRate = new Rate('flashsale_errors');

export const options = {
  scenarios: {
    flash_sale_spike: {
      executor: 'shared-iterations',
      vus: 500,
      iterations: 2000,
      maxDuration: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(99)<500'], // p99 latency must stay below 500ms
    'flashsale_errors': ['rate==0'],   // Zero 500-level internal errors allowed
    checks: ['rate==1.0'],             // All checks must evaluate to true
  },
};

const SALE_ID = '11111111-1111-1111-1111-111111111111';
const BASE_URL = __ENV.TARGET_URL || 'http://localhost:8080';

export default function () {
  const userId = `vu-${__VU}-iter-${__ITER}`;
  const url = `${BASE_URL}/api/v1/flash-sales/${SALE_ID}/reservations`;

  const payload = JSON.stringify({
    quantity: 1,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': userId,
    },
    tags: { name: 'ReserveFlashSaleItem' },
  };

  const res = http.post(url, payload, params);

  const isValidStatus = res.status === 201 || res.status === 409;
  const isNotServerError = res.status < 500;

  const passed = check(res, {
    'status is 201 or 409': (r) => r.status === 201 || r.status === 409,
    'zero 500 internal server errors': (r) => r.status !== 500,
  });

  if (!isNotServerError) {
    errorRate.add(1);
  } else {
    errorRate.add(0);
  }

  if (res.status === 201) {
    reservedCounter.add(1);
  } else if (res.status === 409) {
    outOfStockCounter.add(1);
  }
}
