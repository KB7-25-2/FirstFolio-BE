import { sleep } from 'k6';
import { requireCondition } from '../lib/api.js';
import { authenticatedRequestConfig, loadScenarioOptions } from '../lib/config.js';
import { verifyAuthenticatedUser } from '../lib/preconditions.js';

const config = authenticatedRequestConfig();

export const options = loadScenarioOptions(
  config,
  'auth-login',
  __ENV.AUTH_MAX_P95_MS || 2000,
);

export default function () {
  const login = verifyAuthenticatedUser(config);
  requireCondition(
    ['LEVEL_TEST', 'CURRICULUM', 'HOME'].includes(login.onboarding_step),
    'login returns a valid onboarding step',
  );
  sleep(config.requestIntervalSeconds);
}
