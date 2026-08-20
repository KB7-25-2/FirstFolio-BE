import { group } from 'k6';
import { apiRequest, requireCondition, requireData } from '../lib/api.js';
import { oneOffScenarioOptions, stateChangeConfig } from '../lib/config.js';
import { verifyAuthenticatedUser } from '../lib/preconditions.js';

const config = stateChangeConfig();

export const options = oneOffScenarioOptions(config, 'onboarding-setup');

function loginOrSignup() {
  const loginResult = apiRequest(config, {
    method: 'POST',
    path: '/api/auth/login',
    metricName: 'POST /api/auth/login',
    expectedStatuses: [200, 409],
    performanceClass: 'auth',
  });
  if (loginResult.response.status === 200) {
    return requireData(loginResult, 'login');
  }

  requireCondition(
    loginResult.payload?.error?.code === 'SIGNUP_REQUIRED',
    'unregistered Firebase account returns SIGNUP_REQUIRED',
  );
  const generatedNickname = `k6${String(Date.now()).slice(-8)}`;
  const signup = requireData(apiRequest(config, {
    method: 'POST',
    path: '/api/auth/signup',
    metricName: 'POST /api/auth/signup',
    expectedStatuses: [201],
    body: {
      nickname: __ENV.TEST_NICKNAME || generatedNickname,
      required_terms_agreed: true,
    },
  }), 'signup');
  requireCondition(signup.user_id > 0, 'signup returns user_id');
  requireCondition(signup.onboarding_step === 'LEVEL_TEST', 'signup leads to level test');
  return verifyAuthenticatedUser(config);
}

function completeLevelTest() {
  const attempt = requireData(apiRequest(config, {
    method: 'POST',
    path: '/api/level-tests/attempts',
    metricName: 'POST /api/level-tests/attempts',
    expectedStatuses: [201],
  }), 'level test start');
  requireCondition(attempt.attempt_id > 0, 'level test returns attempt_id');
  requireCondition(attempt.questions?.length > 0, 'level test returns questions');

  const savedAnswers = Object.fromEntries(
    (attempt.answers || []).map((item) => [item.question_id, item.answer?.key]),
  );
  const answers = attempt.questions.map((question) => {
    const key = savedAnswers[question.question_id] || question.choices?.[0]?.key;
    requireCondition(Boolean(key), `level test question ${question.question_id} has a choice`);
    return {
      question_id: question.question_id,
      answer: { key },
    };
  });

  const saved = requireData(apiRequest(config, {
    method: 'PUT',
    path: `/api/level-tests/attempts/${attempt.attempt_id}/answers`,
    metricName: 'PUT /api/level-tests/attempts/:attempt_id/answers',
    body: { answers },
  }), 'level test answer save');
  requireCondition(
    saved.answered_count === saved.total_count,
    'all level test answers are saved',
  );

  const submitted = requireData(apiRequest(config, {
    method: 'POST',
    path: `/api/level-tests/attempts/${attempt.attempt_id}/submit`,
    metricName: 'POST /api/level-tests/attempts/:attempt_id/submit',
  }), 'level test submit');
  requireCondition(submitted.status === 'GRADED', 'level test is graded');
}

function requestedCurriculumIds(draft) {
  if (__ENV.CURRICULUM_MAIN_CHAPTER_IDS) {
    const ids = __ENV.CURRICULUM_MAIN_CHAPTER_IDS
      .split(',')
      .map((value) => Number(value.trim()))
      .filter((value) => Number.isInteger(value) && value > 0);
    requireCondition(ids.length > 0, 'CURRICULUM_MAIN_CHAPTER_IDS contains valid IDs');
    return [...new Set(ids)];
  }

  const ids = [
    ...(draft.items || [])
      .filter((item) => item.source_type !== 'FOUNDATION')
      .map((item) => item.main_chapter_id),
    ...(draft.recommendation_candidates || []).map((item) => item.main_chapter_id),
    ...(draft.cart_candidates || []).map((item) => item.main_chapter_id),
  ];
  return [...new Set(ids)];
}

function confirmCurriculum() {
  const draft = requireData(apiRequest(config, {
    path: '/api/curriculum/draft',
    metricName: 'GET /api/curriculum/draft',
  }), 'curriculum draft');
  const selectedIds = requestedCurriculumIds(draft);

  const confirmed = requireData(apiRequest(config, {
    method: 'POST',
    path: '/api/curriculum/confirm',
    metricName: 'POST /api/curriculum/confirm',
    body: { main_chapter_ids: selectedIds },
  }), 'curriculum confirm');
  requireCondition(
    confirmed.items?.[0]?.source_type === 'FOUNDATION',
    'confirmed curriculum starts with foundation',
  );
}

function finishQuizAttempt(attempt, answerKeys = {}) {
  let lastAnswer = null;
  for (const question of attempt.questions || []) {
    if (question.answered) {
      if (question.correct_answer?.key) {
        answerKeys[question.question_id] = question.correct_answer.key;
      }
      continue;
    }

    const selectedKey = answerKeys[question.question_id] || question.choices?.[0]?.key;
    requireCondition(Boolean(selectedKey), `quiz question ${question.question_id} has a choice`);
    lastAnswer = requireData(apiRequest(config, {
      method: 'PUT',
      path: `/api/learning/quiz-attempts/${attempt.attempt_id}/answers/${question.question_id}`,
      metricName: 'PUT /api/learning/quiz-attempts/:attempt_id/answers/:question_id',
      body: { answer: { key: selectedKey } },
    }), 'quiz answer');
    answerKeys[question.question_id] = lastAnswer.correct_answer?.key;
  }
  return lastAnswer;
}

function completeSubChapter(subChapter) {
  let progress = requireData(apiRequest(config, {
    path: `/api/learning/sub-chapters/${subChapter.sub_chapter_id}/progress`,
    metricName: 'GET /api/learning/sub-chapters/:sub_chapter_id/progress',
  }), 'learning progress');

  if (progress.status !== 'COMPLETED') {
    const content = requireData(apiRequest(config, {
      path: `/api/learning/sub-chapters/${subChapter.sub_chapter_id}`,
      metricName: 'GET /api/learning/sub-chapters/:sub_chapter_id',
    }), 'lesson content');
    const pages = content.lesson?.pages || [];
    const lastPageId = pages[pages.length - 1]?.id;
    requireCondition(Boolean(lastPageId), `sub chapter ${subChapter.sub_chapter_id} has lesson pages`);

    progress = requireData(apiRequest(config, {
      method: 'PUT',
      path: `/api/learning/sub-chapters/${subChapter.sub_chapter_id}/progress`,
      metricName: 'PUT /api/learning/sub-chapters/:sub_chapter_id/progress',
      body: {
        content_version_id: content.content_version_id,
        last_page_id: lastPageId,
        status: 'COMPLETED',
      },
    }), 'learning progress completion');
    requireCondition(progress.status === 'COMPLETED', 'sub chapter lesson is completed');
  }

  if (!progress.quiz?.completed) {
    const attempt = requireData(apiRequest(config, {
      method: 'POST',
      path: `/api/learning/sub-chapters/${subChapter.sub_chapter_id}/quiz-attempts`,
      metricName: 'POST /api/learning/sub-chapters/:sub_chapter_id/quiz-attempts',
      expectedStatuses: [201],
    }), 'sub chapter quiz start');
    finishQuizAttempt(attempt);
  }

  const completed = requireData(apiRequest(config, {
    path: `/api/learning/sub-chapters/${subChapter.sub_chapter_id}/progress`,
    metricName: 'GET /api/learning/sub-chapters/:sub_chapter_id/progress',
  }), 'completed learning progress');
  requireCondition(completed.quiz?.completed, 'sub chapter quiz is completed');
}

function completeFoundationQuiz(mainChapterId) {
  const answerKeys = {};

  for (let attemptNo = 1; attemptNo <= 3; attemptNo += 1) {
    const attempt = requireData(apiRequest(config, {
      method: 'POST',
      path: `/api/learning/main-chapters/${mainChapterId}/quiz-attempts`,
      metricName: 'POST /api/learning/main-chapters/:main_chapter_id/quiz-attempts',
      expectedStatuses: [201],
    }), 'foundation quiz start');
    const lastAnswer = finishQuizAttempt(attempt, answerKeys);

    if (lastAnswer?.main_chapter_completed === true) {
      requireCondition(
        lastAnswer.next_action === 'PORTFOLIO_SETUP',
        'foundation completion leads to portfolio setup',
      );
      return;
    }
  }

  throw new Error('Foundation main chapter quiz was not completed within three attempts');
}

function prepareFoundation() {
  let roadmap = requireData(apiRequest(config, {
    path: '/api/learning/roadmap',
    metricName: 'GET /api/learning/roadmap',
  }), 'learning roadmap');
  let foundation = roadmap.items?.find((item) => item.chapter_type === 'FOUNDATION');
  requireCondition(Boolean(foundation), 'roadmap contains foundation course');

  if (foundation.status !== 'COMPLETED') {
    for (const subChapter of foundation.sub_chapters || []) {
      completeSubChapter(subChapter);
    }

    roadmap = requireData(apiRequest(config, {
      path: '/api/learning/roadmap',
      metricName: 'GET /api/learning/roadmap',
    }), 'learning roadmap after sub chapters');
    foundation = roadmap.items?.find((item) => item.chapter_type === 'FOUNDATION');

    if (foundation.status !== 'COMPLETED') {
      completeFoundationQuiz(foundation.main_chapter_id);
    }
  }

  const completedRoadmap = requireData(apiRequest(config, {
    path: '/api/learning/roadmap',
    metricName: 'GET /api/learning/roadmap',
  }), 'completed learning roadmap');
  const completedFoundation = completedRoadmap.items?.find(
    (item) => item.chapter_type === 'FOUNDATION',
  );
  requireCondition(completedFoundation?.status === 'COMPLETED', 'foundation course is completed');
}

function preparePortfolio(userId) {
  const portfolio = requireData(apiRequest(config, {
    path: '/api/portfolios/current',
    metricName: 'GET /api/portfolios/current',
  }), 'current portfolio');
  requireCondition(portfolio.portfolio_id > 0, 'initial simulation portfolio exists');

  if (__ENV.INITIAL_PORTFOLIO_BUY !== 'true' || (portfolio.holdings || []).length > 0) {
    return;
  }

  let productId = Number(__ENV.INITIAL_PRODUCT_ID || 0);
  if (!productId) {
    const products = requireData(apiRequest(config, {
      path: '/api/financial-products?size=20',
      metricName: 'GET /api/financial-products',
    }), 'financial products');
    productId = products.items?.[0]?.product_id;
  }
  requireCondition(productId > 0, 'an initial portfolio product is available');

  const trade = requireData(apiRequest(config, {
    method: 'POST',
    path: '/api/portfolios/current/trades',
    metricName: 'POST /api/portfolios/current/trades',
    expectedStatuses: [201],
    body: {
      idempotency_key: `k6-onboarding-${userId}-${productId}`,
      transaction_type: 'BUY',
      product_id: productId,
      amount: __ENV.INITIAL_TRADE_AMOUNT || '1000000',
    },
  }), 'initial portfolio trade');
  requireCondition(trade.status === 'COMPLETED', 'initial portfolio trade is completed');
}

export default function () {
  group('인증 및 온보딩 상태', () => {
    let login = loginOrSignup();
    if (login.onboarding_step === 'LEVEL_TEST') {
      completeLevelTest();
      login = verifyAuthenticatedUser(config);
    }
    if (login.onboarding_step === 'CURRICULUM') {
      confirmCurriculum();
      login = verifyAuthenticatedUser(config);
    }
    requireCondition(login.onboarding_step === 'HOME', 'onboarding reaches HOME');

    group('기초 과정 완료', prepareFoundation);
    group('초기 포트폴리오 확인', () => preparePortfolio(login.user.user_id));
  });
}
