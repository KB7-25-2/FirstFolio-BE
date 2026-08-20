import { group, sleep } from 'k6';
import { apiRequest, requireCondition, requireData } from '../lib/api.js';
import { authenticatedRequestConfig, loadScenarioOptions } from '../lib/config.js';
import { verifyPreparedAccount } from '../lib/preconditions.js';

const config = authenticatedRequestConfig();

export const options = loadScenarioOptions(config, 'core-read-journey');

export function setup() {
  verifyPreparedAccount(config);
}

export default function () {
  group('홈', () => {
    const dashboard = requireData(apiRequest(config, {
      path: '/api/dashboard',
      metricName: 'GET /api/dashboard',
    }), 'dashboard');
    requireCondition(Boolean(dashboard.learning), 'dashboard has learning section');
  });

  group('학습', () => {
    const roadmap = requireData(apiRequest(config, {
      path: '/api/learning/roadmap',
      metricName: 'GET /api/learning/roadmap',
    }), 'learning roadmap');
    requireCondition(roadmap.items?.length > 0, 'roadmap has items');

    const curriculum = requireData(apiRequest(config, {
      path: '/api/curriculum',
      metricName: 'GET /api/curriculum',
    }), 'curriculum');
    requireCondition(curriculum.items?.length > 0, 'curriculum has items');
  });

  group('상품과 포트폴리오', () => {
    const products = requireData(apiRequest(config, {
      path: '/api/financial-products?size=20',
      metricName: 'GET /api/financial-products',
    }), 'financial products');
    requireCondition(Array.isArray(products.items), 'products return items');

    const portfolio = requireData(apiRequest(config, {
      path: '/api/portfolios/current',
      metricName: 'GET /api/portfolios/current',
    }), 'current portfolio');
    requireCondition(portfolio.portfolio_id > 0, 'portfolio is active');
  });

  group('포인트와 뉴스', () => {
    const points = requireData(apiRequest(config, {
      path: '/api/points/balance',
      metricName: 'GET /api/points/balance',
    }), 'point balance');
    requireCondition(Number.isInteger(points.point_balance), 'point balance is an integer');

    const news = requireData(apiRequest(config, {
      path: '/api/financial-news?limit=3',
      metricName: 'GET /api/financial-news',
    }), 'financial news');
    requireCondition(Array.isArray(news.items), 'financial news returns items');
  });

  sleep(config.requestIntervalSeconds);
}
