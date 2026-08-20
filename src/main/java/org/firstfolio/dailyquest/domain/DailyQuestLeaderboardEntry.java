package org.firstfolio.dailyquest.domain;

import java.time.LocalDateTime;

public class DailyQuestLeaderboardEntry {

    private long userId;
    private long rankNo;
    private String nickname;
    private int score;
    private LocalDateTime completedAt;

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public long getRankNo() {
        return rankNo;
    }

    public void setRankNo(long rankNo) {
        this.rankNo = rankNo;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
