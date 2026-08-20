import { group, sleep } from 'k6';
import { apiRequest, requireCondition, requireData } from '../lib/api.js';
import { authenticatedRequestConfig, loadScenarioOptions } from '../lib/config.js';
import { verifyPreparedAccount } from '../lib/preconditions.js';

const config = authenticatedRequestConfig();

export const options = loadScenarioOptions(config, 'financial-products');

export function setup() {
  verifyPreparedAccount(config);
}

export default function () {
  let selectedProduct;
  group('상품 목록', () => {
    const page = requireData(apiRequest(config, {
      path: '/api/financial-products?size=20',
      metricName: 'GET /api/financial-products',
    }), 'financial product list');
    requireCondition(Array.isArray(page.items), 'product list returns items');
    requireCondition(page.items.length > 0, 'product list is not empty');
    selectedProduct = page.items[__ITER % page.items.length];
  });

  group('상품 상세', () => {
    const product = requireData(apiRequest(config, {
      path: `/api/financial-products/${selectedProduct.product_id}`,
      metricName: 'GET /api/financial-products/:product_id',
    }), 'financial product detail');
    requireCondition(product.product_id === selectedProduct.product_id, 'product detail ID matches list');
    requireCondition(Boolean(product.display_name), 'product detail has display_name');
    requireCondition(Boolean(product.asset_type), 'product detail has asset_type');
  });

  sleep(config.requestIntervalSeconds);
}
