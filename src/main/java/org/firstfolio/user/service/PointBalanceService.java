package org.firstfolio.user.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.user.domain.PointBalanceSnapshot;
import org.firstfolio.user.mapper.PointBalanceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointBalanceService {

    private static final Logger log = LogManager.getLogger(PointBalanceService.class);

    private final PointBalanceMapper pointBalanceMapper;

    public PointBalanceService(PointBalanceMapper pointBalanceMapper) {
        this.pointBalanceMapper = pointBalanceMapper;
    }

    @Transactional(readOnly = true)
    public PointBalanceSnapshot get(long userId) {
        PointBalanceSnapshot snapshot = pointBalanceMapper.findByUserId(userId);

        if (snapshot == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        if (snapshot.getPointBalance() != snapshot.getLedgerBalance()) {
            log.error(
                    "포인트 저장 잔액과 원장 합계 불일치 userId={} stored={} ledger={}",
                    userId,
                    snapshot.getPointBalance(),
                    snapshot.getLedgerBalance()
            );
        }

        return snapshot;
    }
}
