import http from 'k6/http';
import { check } from 'k6';

function parsePayload(response) {
  try {
    return response.json();
  } catch (error) {
    return null;
  }
}

export function apiRequest(
  config,
  {
    method = 'GET',
    path,
    metricName,
    body = null,
    expectedStatuses = [200],
    performanceClass = null,
  },
) {
  const selectedPerformanceClass = performanceClass || (method === 'GET' ? 'read' : 'write');
  if (!['auth', 'read', 'write'].includes(selectedPerformanceClass)) {
    throw new Error(`Unsupported performance class: ${selectedPerformanceClass}`);
  }

  const headers = { ...config.headers };
  let requestBody = null;
  if (body !== null) {
    headers['Content-Type'] = 'application/json';
    requestBody = JSON.stringify(body);
  }

  const response = http.request(method, `${config.baseUrl}${path}`, requestBody, {
    headers,
    responseCallback: http.expectedStatuses(...expectedStatuses),
    tags: {
      name: metricName,
      performance_class: selectedPerformanceClass,
    },
  });
  const statusAccepted = expectedStatuses.includes(response.status);
  const statusCheck = {};
  statusCheck[`${metricName} status accepted`] = () => statusAccepted;
  check(response, statusCheck);

  return {
    response,
    payload: parsePayload(response),
    ok: statusAccepted,
  };
}

export function requireData(result, label) {
  if (!result.ok) {
    const errorCode = result.payload?.error?.code || 'UNKNOWN_ERROR';
    throw new Error(`${label} failed: HTTP ${result.response.status} ${errorCode}`);
  }
  if (!result.payload || !Object.prototype.hasOwnProperty.call(result.payload, 'data')) {
    throw new Error(`${label} did not return the FirstFolio data envelope`);
  }
  return result.payload.data;
}

export function requireCondition(condition, message) {
  const result = check(null, {
    [message]: () => Boolean(condition),
  });
  if (!result) {
    throw new Error(message);
  }
}
