package com.scriptles.cabinet.profile.repository;
import com.scriptles.cabinet.profile.entity.HQMember; import com.scriptles.cabinet.profile.enums.HQMemberRole; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface HQMemberRepository extends JpaRepository<HQMember,UUID> { Optional<HQMember> findByHqProfileIdAndAccountId(UUID hqId,UUID accountId); boolean existsByHqProfileIdAndAccountId(UUID hqId,UUID accountId); List<HQMember> findByHqProfileIdOrderByCreatedAtAsc(UUID hqId); }
