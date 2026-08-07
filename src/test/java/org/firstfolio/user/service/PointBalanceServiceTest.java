package org.firstfolio.user.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.user.domain.PointBalanceSnapshot;
import org.firstfolio.user.mapper.PointBalanceMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PointBalanceServiceTest {

    @Test
    void returnsVerifiedStoredBalance() {
        PointBalanceMapper mapper = mock(PointBalanceMapper.class);
        PointBalanceSnapshot snapshot = new PointBalanceSnapshot();
        snapshot.setUserId(101L);
        snapshot.setPointBalance(4700);
        snapshot.setLedgerBalance(4700);
        when(mapper.findByUserId(101L)).thenReturn(snapshot);

        assertSame(snapshot, new PointBalanceService(mapper).get(101L));
    }

    @Test
    void rejectsMissingUser() {
        PointBalanceMapper mapper = mock(PointBalanceMapper.class);
        ApiException exception = assertThrows(ApiException.class,
                () -> new PointBalanceService(mapper).get(101L));
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void stillReturnsStoredBalanceWhenLedgerNeedsOperationalReview() {
        PointBalanceMapper mapper = mock(PointBalanceMapper.class);
        PointBalanceSnapshot snapshot = new PointBalanceSnapshot();
        snapshot.setPointBalance(4700);
        snapshot.setLedgerBalance(4600);
        when(mapper.findByUserId(101L)).thenReturn(snapshot);

        assertEquals(4700, new PointBalanceService(mapper).get(101L).getPointBalance());
    }
}
