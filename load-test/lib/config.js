const LOCAL_TARGET_PATTERN = /^https?:\/\/(localhost|127\.0\.0\.1|host\.docker\.internal)(:\d+)?(\/.*)?$/i;

function positiveNumber(value, fallback, name) {
  const parsed = Number(value || fallback);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive number`);
  }
  return parsed;
}

function positiveInteger(value, fallback, name) {
  const parsed = Number(value || fallback);
  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }
  return parsed;
}

function nonNegativeInteger(value, fallback, name) {
  const parsed = Number(value ?? fallback);
  if (!Number.isInteger(parsed) || parsed < 0) {
    throw new Error(`${name} must be a non-negative integer`);
  }
  return parsed;
}

function duration(value, fallback, name) {
  const selected = value || fallback;
  if (!/^\d+(ms|s|m|h)$/.test(selected)) {
    throw new Error(`${name} must be a k6 duration such as 10s or 1m`);
  }
  return selected;
}

export function requestConfig() {
  const baseUrl = (__ENV.BASE_URL || 'http://host.docker.internal:8080').replace(/\/+$/, '');
  const targetPath = __ENV.TARGET_PATH || '/api/health';

  if (!targetPath.startsWith('/')) {
    throw new Error('TARGET_PATH must start with /');
  }
  if (!LOCAL_TARGET_PATTERN.test(baseUrl) && __ENV.ALLOW_REMOTE_TARGET !== 'true') {
    throw new Error('Remote targets are blocked. Set ALLOW_REMOTE_TARGET=true only after reviewing the target and expected cost.');
  }

  const headers = {
    Accept: 'application/json',
    'User-Agent': 'FirstFolio-k6/2.1',
  };
  if (__ENV.AUTH_TOKEN) {
    headers.Authorization = __ENV.AUTH_TOKEN.startsWith('Bearer ')
      ? __ENV.AUTH_TOKEN
      : `Bearer ${__ENV.AUTH_TOKEN}`;
  }

  return {
    url: `${baseUrl}${targetPath}`,
    headers,
    requestIntervalSeconds: positiveNumber(
      __ENV.REQUEST_INTERVAL_SECONDS,
      1,
      'REQUEST_INTERVAL_SECONDS',
    ),
    maxP95Ms: positiveNumber(__ENV.MAX_P95_MS, 500, 'MAX_P95_MS'),
    testId: __ENV.K6_TEST_ID || 'local',
  };
}

export function smokeIterations() {
  return positiveInteger(__ENV.SMOKE_ITERATIONS, 3, 'SMOKE_ITERATIONS');
}

export function loadStages() {
  return [
    {
      duration: duration(__ENV.LOAD_STAGE_1_DURATION, '10s', 'LOAD_STAGE_1_DURATION'),
      target: nonNegativeInteger(__ENV.LOAD_STAGE_1_VUS, 5, 'LOAD_STAGE_1_VUS'),
    },
    {
      duration: duration(__ENV.LOAD_STAGE_2_DURATION, '30s', 'LOAD_STAGE_2_DURATION'),
      target: nonNegativeInteger(__ENV.LOAD_STAGE_2_VUS, 5, 'LOAD_STAGE_2_VUS'),
    },
    {
      duration: duration(__ENV.LOAD_STAGE_3_DURATION, '10s', 'LOAD_STAGE_3_DURATION'),
      target: nonNegativeInteger(__ENV.LOAD_STAGE_3_VUS, 10, 'LOAD_STAGE_3_VUS'),
    },
    {
      duration: duration(__ENV.LOAD_STAGE_4_DURATION, '30s', 'LOAD_STAGE_4_DURATION'),
      target: nonNegativeInteger(__ENV.LOAD_STAGE_4_VUS, 10, 'LOAD_STAGE_4_VUS'),
    },
    {
      duration: duration(__ENV.LOAD_RAMP_DOWN_DURATION, '10s', 'LOAD_RAMP_DOWN_DURATION'),
      target: 0,
    },
  ];
}
