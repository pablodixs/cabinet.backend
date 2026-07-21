package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByUsernameIgnoreCase(String username);
    Boolean existsByUsernameIgnoreCase(String username);
    Boolean existsByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id in :ids order by user.id")
    List<User> findAllLockedByIdIn(@Param("ids") Collection<UUID> ids);

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

    @Query("""
            select user from User user
            where user.active = true
              and (:viewerId is not null
                   or user.profileVisibility is null
                   or user.profileVisibility = :visibility)
              and (:viewerId is null or not exists (
                  select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = user.id)
                     or (block.blocker.id = user.id and block.blocked.id = :viewerId)
              ))
              and (
                lower(user.username) like lower(concat('%', :query, '%'))
                or lower(user.displayName) like lower(concat('%', :query, '%'))
              )
            """)
    Page<User> searchProfiles(
            @Param("query") String query,
            @Param("visibility") Visibility visibility,
            @Param("viewerId") UUID viewerId,
            Pageable pageable
    );
}
