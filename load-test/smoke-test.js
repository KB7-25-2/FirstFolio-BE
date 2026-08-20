import { requestConfig, smokeIterations } from './lib/config.js';
import { getTarget } from './lib/request.js';

const config = requestConfig();

export const options = {
  discardResponseBodies: true,
  scenarios: {
    smoke: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: smokeIterations(),
      maxDuration: '30s',
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: [`p(95)<${config.maxP95Ms}`],
  },
  tags: {
    testid: config.testId,
    test_type: 'smoke',
  },
};

export default function () {
  getTarget(config);
}
