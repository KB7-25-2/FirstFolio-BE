package org.firstfolio.user.domain;

import java.time.LocalDateTime;

public class UserConsent {

    private final long userId;
    private final ConsentType consentType;
    private final String policyVersion;
    private final boolean agreed;
    private final LocalDateTime occurredAt;

    public UserConsent(
            long userId,
            ConsentType consentType,
            String policyVersion,
            boolean agreed,
            LocalDateTime occurredAt
    ) {
        this.userId = userId;
        this.consentType = consentType;
        this.policyVersion = policyVersion;
        this.agreed = agreed;
        this.occurredAt = occurredAt;
    }

    public long getUserId() {
        return userId;
    }

    public ConsentType getConsentType() {
        return consentType;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public boolean isAgreed() {
        return agreed;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
