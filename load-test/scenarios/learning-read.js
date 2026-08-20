import { group, sleep } from 'k6';
import { apiRequest, requireCondition, requireData } from '../lib/api.js';
import { authenticatedRequestConfig, loadScenarioOptions } from '../lib/config.js';
import { verifyPreparedAccount } from '../lib/preconditions.js';

const config = authenticatedRequestConfig();

export const options = loadScenarioOptions(config, 'learning-read');

export function setup() {
  verifyPreparedAccount(config);
}

export default function () {
  let roadmap;
  group('커리큘럼과 로드맵', () => {
    const curriculum = requireData(apiRequest(config, {
      path: '/api/curriculum',
      metricName: 'GET /api/curriculum',
    }), 'curriculum');
    requireCondition(curriculum.items?.[0]?.chapter_type === 'FOUNDATION', 'curriculum starts with foundation');

    roadmap = requireData(apiRequest(config, {
      path: '/api/learning/roadmap',
      metricName: 'GET /api/learning/roadmap',
    }), 'learning roadmap');
    requireCondition(roadmap.items?.length > 0, 'roadmap returns chapters');
  });

  group('강좌 상세와 진도', () => {
    const chapter = roadmap.items.find((item) => item.sub_chapters?.length > 0);
    const subChapter = chapter?.sub_chapters?.[0];
    requireCondition(Boolean(subChapter), 'roadmap contains a sub chapter');

    const content = requireData(apiRequest(config, {
      path: `/api/learning/sub-chapters/${subChapter.sub_chapter_id}`,
      metricName: 'GET /api/learning/sub-chapters/:sub_chapter_id',
    }), 'lesson content');
    requireCondition(content.lesson?.pages?.length > 0, 'lesson content contains pages');

    const progress = requireData(apiRequest(config, {
      path: `/api/learning/sub-chapters/${subChapter.sub_chapter_id}/progress`,
      metricName: 'GET /api/learning/sub-chapters/:sub_chapter_id/progress',
    }), 'learning progress');
    requireCondition(progress.sub_chapter_id === subChapter.sub_chapter_id, 'progress matches sub chapter');
  });

  sleep(config.requestIntervalSeconds);
}
