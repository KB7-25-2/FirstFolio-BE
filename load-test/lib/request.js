import http from 'k6/http';
import { check } from 'k6';

export function getTarget(config) {
  const response = http.get(config.url, {
    headers: config.headers,
    tags: { name: `GET ${__ENV.TARGET_PATH || '/api/health'}` },
  });

  check(response, {
    'status is 200': (result) => result.status === 200,
  });
}
