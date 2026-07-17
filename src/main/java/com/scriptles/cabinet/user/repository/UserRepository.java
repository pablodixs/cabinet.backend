package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByUsernameIgnoreCase(String username);
    Boolean existsByUsernameIgnoreCase(String username);
    Boolean existsByEmailIgnoreCase(String email);

    Page<User> findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String username,
            String displayName,
            String email,
            Pageable pageable
    );

    @Query("""
            select user from User user
            where user.active = true
              and (user.profileVisibility is null or user.profileVisibility = :visibility)
              and (
                lower(user.username) like lower(concat('%', :query, '%'))
                or lower(user.displayName) like lower(concat('%', :query, '%'))
              )
            """)
    Page<User> searchVisibleProfiles(
            @Param("query") String query,
            @Param("visibility") Visibility visibility,
            Pageable pageable
    );
}
