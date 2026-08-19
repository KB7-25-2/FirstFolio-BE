package org.firstfolio.quiz.integration.ai.service;

import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.domain.SubChapter;
import org.firstfolio.curriculum.mapper.MainChapterMapper;
import org.firstfolio.curriculum.mapper.SubChapterMapper;
import org.firstfolio.quiz.integration.ai.dto.response.QuizGenerationTargetResponse;
import org.firstfolio.quiz.integration.ai.dto.response.QuizGenerationTargetResponse.MainChapterTarget;
import org.firstfolio.quiz.integration.ai.dto.response.QuizGenerationTargetResponse.SubChapterTarget;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuizGenerationTargetQueryService {

    private final MainChapterMapper mainChapterMapper;
    private final SubChapterMapper subChapterMapper;

    public QuizGenerationTargetQueryService(
            MainChapterMapper mainChapterMapper,
            SubChapterMapper subChapterMapper
    ) {
        this.mainChapterMapper = mainChapterMapper;
        this.subChapterMapper = subChapterMapper;
    }

    public QuizGenerationTargetResponse findTargets() {
        List<MainChapterTarget> mainChapterTargets = mainChapterMapper.findAll(null, true)
                .stream()
                .map(this::toMainChapterTarget)
                .toList();

        return new QuizGenerationTargetResponse(mainChapterTargets);
    }

    private MainChapterTarget toMainChapterTarget(MainChapter mainChapter) {
        List<SubChapterTarget> subChapterTargets = subChapterMapper
                .findAllByMainChapterId(mainChapter.getMainChapterId())
                .stream()
                .filter(SubChapter::isActive)
                .map(this::toSubChapterTarget)
                .toList();

        return new MainChapterTarget(
                mainChapter.getMainChapterId(),
                mainChapter.getTitle(),
                mainChapter.getChapterType(),
                subChapterTargets
        );
    }

    private SubChapterTarget toSubChapterTarget(SubChapter subChapter) {
        return new SubChapterTarget(
                subChapter.getSubChapterId(),
                subChapter.getMainChapterId(),
                subChapter.getTitle()
        );
    }
}
