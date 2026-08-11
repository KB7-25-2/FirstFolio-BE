package org.firstfolio.policy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.policy.domain.SystemPolicy;

import java.time.LocalDateTime;

@Mapper
public interface SystemPolicyMapper {

    /**
     * 그 시점에 유효한 정책 한 건. <b>없으면 null이다.</b>
     *
     * <p>유효 조건은 넷이다 — {@code is_active}가 참이고, {@code effective_from}이 지났고,
     * {@code effective_to}가 아직 안 됐거나 없고, 그중 <b>가장 높은 버전</b>.
     * <b>하나만 빠뜨려도 옛 정책으로 조용히 계산된다.</b> 도메인마다 따로 구현하지 않고
     * 이 한 곳에 모아 둔 이유다.</p>
     *
     * <p>{@code idx_system_policies_active_period (policy_key, is_active, effective_from,
     * effective_to)}를 그대로 탄다.</p>
     *
     * @param policyKey {@code TRADE} 등. 도메인이 자기 키만 넘긴다
     * @param at        기준 시각(UTC)
     */
    SystemPolicy findActive(
            @Param("policyKey") String policyKey,
            @Param("at") LocalDateTime at
    );
}
