package org.firstfolio.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.firstfolio.user.domain.UserConsent;

@Mapper
public interface UserConsentMapper {

    int insert(UserConsent consent);
}
