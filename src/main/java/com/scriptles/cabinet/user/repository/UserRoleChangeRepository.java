package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.user.entity.UserRoleChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRoleChangeRepository extends JpaRepository<UserRoleChange, UUID> {
}
