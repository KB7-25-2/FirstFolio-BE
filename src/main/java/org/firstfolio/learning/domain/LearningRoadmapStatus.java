package org.firstfolio.learning.domain;

public final class LearningRoadmapStatus {

    private LearningRoadmapStatus() {
    }

    public enum Chapter {
        IN_PROGRESS,
        LOCKED,
        COMPLETED
    }

    public enum Schedule {
        COMPLETED,
        IN_PROGRESS,
        NEXT,
        LOCKED,
        UNAVAILABLE
    }

    public enum Quiz {
        LOCKED,
        AVAILABLE,
        IN_PROGRESS,
        COMPLETED
    }
}
