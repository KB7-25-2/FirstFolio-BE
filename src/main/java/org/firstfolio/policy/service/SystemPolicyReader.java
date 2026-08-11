package org.firstfolio.policy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.policy.domain.SystemPolicy;
import org.firstfolio.policy.mapper.SystemPolicyMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 지금 유효한 운영 정책을 읽는 <b>단 하나의 자리</b>.
 *
 * <p>{@code system_policies}는 여덟 도메인이 {@code policy_key}로 나눠 쓰는 공용 테이블이다.
 * 내용은 키마다 다르지만 <b>"어느 버전이 지금 유효한가"를 고르는 규칙은 완전히 같다</b> —
 * {@code is_active} · {@code effective_from} · {@code effective_to} · 최신 {@code version_no}.
 * 조건이 넷이라 도메인마다 따로 구현하면 <b>하나만 빠뜨려도 옛 정책으로 조용히 계산된다.</b></p>
 *
 * <h3>쓰는 쪽에게</h3>
 *
 * <p>{@code policyKey}를 넘기면 되므로 다른 도메인도 그대로 재사용할 수 있다.
 * <b>{@code config_json}의 해석은 각 도메인이 한다</b> — 정책 내용이 키마다 완전히 달라
 * 여기서 공통으로 다룰 것이 없다. (예: {@code TRADE}는
 * {@code org.firstfolio.portfolio.service.TradePolicy}가 읽는다.)</p>
 *
 * <h3>정책이 없는 것은 오류가 아니다</h3>
 *
 * <p>{@code null}을 돌려준다. 아직 아무도 정책 행을 넣지 않은 상태가 정상적으로 있고
 * (이 테이블은 <b>쓰기 경로가 명세에 없어 SQL로 넣는다</b>), 그때 기능이 멈추면 안 된다.
 * 호출한 쪽이 안전한 기본값으로 넘어가야 한다.</p>
 *
 * <h3>캐시를 두지 않는다</h3>
 *
 * <p>거래마다 부르지만 인덱스를 타는 1행 조회다. 캐시를 넣으면 정책을 바꿨을 때
 * 언제 반영되는지가 불분명해지는데, 얻는 것이 없다.</p>
 */
@Component
public class SystemPolicyReader {

    private static final Logger log = LogManager.getLogger(SystemPolicyReader.class);

    private final ObjectMapper objectMapper = ApiObjectMapperFactory.create();

    private final SystemPolicyMapper systemPolicyMapper;

    public SystemPolicyReader(SystemPolicyMapper systemPolicyMapper) {
        this.systemPolicyMapper = systemPolicyMapper;
    }

    /**
     * 그 시점에 유효한 정책. <b>없으면 null이다.</b>
     *
     * @param policyKey {@code TRADE} 등
     * @param at        기준 시각(UTC)
     */
    public SystemPolicy findActive(String policyKey, LocalDateTime at) {
        if (policyKey == null || policyKey.isBlank() || at == null) {
            return null;
        }

        return systemPolicyMapper.findActive(policyKey, at);
    }

    /**
     * 유효한 정책의 내용. <b>정책이 없거나 내용을 읽지 못하면 null이다.</b>
     *
     * <p>깨진 JSON에 예외를 던지지 않는다. 정책 하나가 잘못 들어갔다고 거래가 통째로 막히는 것보다
     * <b>기본값으로 도는 편이 낫다.</b> 대신 경고를 남겨 추적할 수 있게 한다.</p>
     */
    public JsonNode findActiveConfig(String policyKey, LocalDateTime at) {
        SystemPolicy policy = findActive(policyKey, at);

        if (policy == null || policy.getConfigJson() == null) {
            return null;
        }

        try {
            return objectMapper.readTree(policy.getConfigJson());
        } catch (Exception exception) {
            log.warn(
                    "정책 내용을 읽지 못했습니다. 기본값으로 계속합니다 key={} version={}",
                    policyKey,
                    policy.getVersionNo(),
                    exception
            );

            return null;
        }
    }
}
