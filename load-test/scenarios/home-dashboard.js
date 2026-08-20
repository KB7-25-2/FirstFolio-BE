import { sleep } from 'k6';
import { apiRequest, requireCondition, requireData } from '../lib/api.js';
import { authenticatedRequestConfig, loadScenarioOptions } from '../lib/config.js';
import { verifyPreparedAccount } from '../lib/preconditions.js';

const config = authenticatedRequestConfig();

export const options = loadScenarioOptions(config, 'home-dashboard');

export function setup() {
  verifyPreparedAccount(config);
}

export default function () {
  const dashboard = requireData(apiRequest(config, {
    path: '/api/dashboard',
    metricName: 'GET /api/dashboard',
  }), 'dashboard');
  requireCondition(Boolean(dashboard.portfolio), 'dashboard returns portfolio section');
  requireCondition(Boolean(dashboard.daily_quest), 'dashboard returns daily quest section');
  requireCondition(Boolean(dashboard.learning), 'dashboard returns learning section');
  requireCondition(Array.isArray(dashboard.upcoming_events), 'dashboard returns upcoming events');
  requireCondition(Array.isArray(dashboard.latest_news), 'dashboard returns latest news');
  sleep(config.requestIntervalSeconds);
}
