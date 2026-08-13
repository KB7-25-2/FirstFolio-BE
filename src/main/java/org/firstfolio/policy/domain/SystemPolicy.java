package org.firstfolio.policy.domain;

import java.time.LocalDateTime;

/**
 * {@code system_policies} 한 행. <b>버전이 붙은 운영 정책</b>이다.
 *
 * <p>도메인별로 {@code policy_key}를 나눠 쓴다 — {@code QUIZ_REWARD},
 * {@code DAILY_QUEST_REWARD}, {@code ATTENDANCE}, {@code LEADERBOARD},
 * {@code SIMULATION}, {@code TRADE}, {@code RESET}, {@code GIFTICON},
 * {@code AI_REVIEW}. <b>내용({@code configJson})은 키마다 완전히 다르므로 해석은 각 도메인이 한다.</b>
 * 이 클래스는 어느 행이 지금 유효한지까지만 다룬다.</p>
 *
 * <p>정책은 <b>고쳐 쓰지 않고 새 버전을 쌓는다.</b> 과거 거래가 어느 요율로 계산됐는지
 * 나중에 확인할 수 있어야 하기 때문이다 (SIMULATION_POLICY_v3 3.3절).</p>
 */
public class SystemPolicy {

    private Long policyId;

    /** {@code TRADE} 등. 도메인이 자기 것만 읽는다. */
    private String policyKey;

    /** 같은 키 안에서 올라간다. {@code uq_system_policies_key_version}이 중복을 막는다. */
    private Integer versionNo;

    /** 정책 내용 원문. 해석은 도메인 몫이다. */
    private String configJson;

    private LocalDateTime effectiveFrom;

    /** 종료 시각. <b>null이면 아직 끝나지 않은 정책</b>이다. */
    private LocalDateTime effectiveTo;

    private boolean active;

    private Long createdBy;
    private LocalDateTime createdAt;

    public Long getPolicyId() {
        return policyId;
    }

    public void setPolicyId(Long policyId) {
        this.policyId = policyId;
    }

    public String getPolicyKey() {
        return policyKey;
    }

    public void setPolicyKey(String policyKey) {
        this.policyKey = policyKey;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public LocalDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDateTime effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDateTime getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDateTime effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
