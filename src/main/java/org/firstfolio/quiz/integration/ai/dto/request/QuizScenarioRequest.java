package org.firstfolio.quiz.integration.ai.dto.request;

import java.util.List;

public record QuizScenarioRequest(
        String title,
        String narrative,
        Persona persona,
        Requirements requirements,
        Market market,
        List<String> constraints,
        String paperTitle
) {

    public record Persona(
            String name,
            String age,
            String job
    ) {
    }

    public record Requirements(
            String assets,
            String risk,
            String goal
    ) {
    }

    public record Market(
            String title,
            String referenceAt,
            List<String> bullets
    ) {
    }
}
