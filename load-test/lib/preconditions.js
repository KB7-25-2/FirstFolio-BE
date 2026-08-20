import { apiRequest, requireCondition, requireData } from './api.js';

export function verifyAuthenticatedUser(config) {
  const login = requireData(apiRequest(config, {
    method: 'POST',
    path: '/api/auth/login',
    metricName: 'POST /api/auth/login',
    performanceClass: 'auth',
  }), 'login');

  requireCondition(login.user?.user_id > 0, 'login returns user_id');
  return login;
}

export function verifyPreparedAccount(config) {
  const login = verifyAuthenticatedUser(config);
  requireCondition(
    login.onboarding_step === 'HOME',
    'test account completed level test and curriculum',
  );

  const roadmap = requireData(apiRequest(config, {
    path: '/api/learning/roadmap',
    metricName: 'GET /api/learning/roadmap',
  }), 'learning roadmap');
  const foundation = roadmap.items?.find((item) => item.chapter_type === 'FOUNDATION');
  requireCondition(foundation?.status === 'COMPLETED', 'test account completed foundation course');

  const portfolio = requireData(apiRequest(config, {
    path: '/api/portfolios/current',
    metricName: 'GET /api/portfolios/current',
  }), 'current portfolio');
  requireCondition(portfolio.portfolio_id > 0, 'test account has an active portfolio');

  return { login, roadmap, foundation, portfolio };
}
