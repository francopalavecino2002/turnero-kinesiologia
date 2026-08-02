package com.palavecino.backend.usertoken;

import com.palavecino.backend.user.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    Optional<UserToken> findByTokenHash(String tokenHash);

    Optional<UserToken> findFirstByUserAndTypeOrderByCreatedAtDesc(User user, TokenType type);

    void deleteByUserAndTypeAndUsedAtIsNull(User user, TokenType type);
}
