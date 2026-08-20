import { sleep } from 'k6';
import { loadStages, requestConfig } from './lib/config.js';
import { getTarget } from './lib/request.js';

const config = requestConfig();

export const options = {
  discardResponseBodies: true,
  scenarios: {
    backend: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: loadStages(),
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: [`p(95)<${config.maxP95Ms}`],
  },
  tags: {
    testid: config.testId,
    test_type: 'load',
  },
};

export default function () {
  getTarget(config);
  sleep(config.requestIntervalSeconds);
}
