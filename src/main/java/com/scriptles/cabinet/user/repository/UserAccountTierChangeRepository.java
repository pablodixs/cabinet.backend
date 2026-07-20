package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.user.entity.UserAccountTierChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserAccountTierChangeRepository extends JpaRepository<UserAccountTierChange, UUID> {
}
