package org.firstfolio.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.user.domain.User;

import java.time.LocalDateTime;

@Mapper
public interface UserMapper {

    int countSignupConflicts(
            @Param("firebaseUid") String firebaseUid,
            @Param("email") String email,
            @Param("nickname") String nickname
    );

    int insert(User user);

    User findByFirebaseUid(@Param("firebaseUid") String firebaseUid);

    User findById(@Param("userId") long userId);

    int countNicknameConflict(
            @Param("userId") long userId,
            @Param("nickname") String nickname
    );

    int updateProfile(
            @Param("userId") long userId,
            @Param("nickname") String nickname,
            @Param("newsletterOptIn") Boolean newsletterOptIn,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int updateLastLoginAt(
            @Param("userId") long userId,
            @Param("lastLoginAt") LocalDateTime lastLoginAt
    );

    String findOnboardingStep(@Param("userId") long userId);
}
