package com.scriptles.cabinet.profile.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.profile.dto.HQMemberRequest;
import com.scriptles.cabinet.profile.dto.HQMemberResponse;
import com.scriptles.cabinet.profile.entity.HQMember;
import com.scriptles.cabinet.profile.entity.HQProfile;
import com.scriptles.cabinet.profile.enums.HQClaimStatus;
import com.scriptles.cabinet.profile.enums.HQMemberRole;
import com.scriptles.cabinet.profile.repository.HQMemberRepository;
import com.scriptles.cabinet.profile.repository.HQProfileRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class HQManagementService {
    private final HQProfileRepository hqs;
    private final HQMemberRepository members;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public List<HQMemberResponse> list(UUID actorId, UUID profileId) {
        HQProfile hq = hq(profileId); authorize(actorId, hq, false);
        return members.findByHqProfileIdOrderByCreatedAtAsc(hq.getId()).stream().map(this::to).toList();
    }

    @Transactional
    public HQMemberResponse add(UUID actorId, UUID profileId, HQMemberRequest request) {
        HQProfile hq = hq(profileId); HQMember actor = authorize(actorId, hq, true);
        if (members.existsByHqProfileIdAndAccountId(hq.getId(), request.accountId())) throw error("MEMBER_ALREADY_EXISTS", "Conta já faz parte da equipe");
        if (actor != null && actor.getRole() == HQMemberRole.ADMIN && (request.role() == HQMemberRole.OWNER || request.role() == HQMemberRole.ADMIN)) throw error("HQ_FORBIDDEN", "ADMIN só pode gerenciar EDITOR e ANALYST");
        HQMember member = new HQMember(); member.setHqProfile(hq); member.setAccount(users.findById(request.accountId()).orElseThrow(() -> error("ACCOUNT_NOT_FOUND", "Conta não encontrada"))); member.setRole(request.role());
        return to(members.save(member));
    }

    @Transactional
    public HQMemberResponse changeRole(UUID actorId, UUID profileId, UUID memberId, HQMemberRequest request) {
        HQProfile hq = hq(profileId); HQMember actor = authorize(actorId, hq, true);
        HQMember member = members.findById(memberId).filter(m -> m.getHqProfile().getId().equals(hq.getId())).orElseThrow(() -> error("MEMBER_NOT_FOUND", "Membro não encontrado"));
        if (actor != null && actor.getRole() == HQMemberRole.ADMIN && (member.getRole() == HQMemberRole.OWNER || member.getRole() == HQMemberRole.ADMIN || request.role() == HQMemberRole.OWNER || request.role() == HQMemberRole.ADMIN)) throw error("HQ_FORBIDDEN", "ADMIN só pode gerenciar EDITOR e ANALYST");
        if (member.getRole() == HQMemberRole.OWNER && request.role() != HQMemberRole.OWNER && members.findByHqProfileIdOrderByCreatedAtAsc(hq.getId()).stream().filter(m -> m.getRole() == HQMemberRole.OWNER).count() <= 1) throw error("LAST_OWNER", "A HQ precisa manter um OWNER");
        member.setRole(request.role()); return to(members.save(member));
    }

    @Transactional
    public void remove(UUID actorId, UUID profileId, UUID memberId) {
        HQProfile hq = hq(profileId); HQMember actor = authorize(actorId, hq, true);
        HQMember member = members.findById(memberId).filter(m -> m.getHqProfile().getId().equals(hq.getId())).orElseThrow(() -> error("MEMBER_NOT_FOUND", "Membro não encontrado"));
        if (actor != null && actor.getRole() == HQMemberRole.ADMIN && (member.getRole() == HQMemberRole.OWNER || member.getRole() == HQMemberRole.ADMIN)) throw error("HQ_FORBIDDEN", "ADMIN só pode gerenciar EDITOR e ANALYST");
        if (member.getRole() == HQMemberRole.OWNER && members.findByHqProfileIdOrderByCreatedAtAsc(hq.getId()).stream().filter(m -> m.getRole() == HQMemberRole.OWNER).count() <= 1) throw error("LAST_OWNER", "A HQ precisa manter um OWNER");
        members.delete(member);
    }

    private HQMember authorize(UUID actorId, HQProfile hq, boolean write) {
        if (users.findById(actorId).map(u -> u.getRole() != null && u.getRole().name().equals("ADMIN")).orElse(false)) return null;
        if (hq.getClaimStatus() == HQClaimStatus.SUSPENDED) throw error("HQ_SUSPENDED", "HQ suspensa");
        HQMember member = members.findByHqProfileIdAndAccountId(hq.getId(), actorId).orElseThrow(() -> error("HQ_FORBIDDEN", "Você não pertence à equipe"));
        if (write && member.getRole() != HQMemberRole.OWNER && member.getRole() != HQMemberRole.ADMIN) throw error("HQ_FORBIDDEN", "Permissão insuficiente");
        return member;
    }
    private HQProfile hq(UUID profileId) { return hqs.findByProfileId(profileId).orElseThrow(() -> error("PROFILE_NOT_FOUND", "Perfil HQ não encontrado")); }
    private HQMemberResponse to(HQMember m) { return new HQMemberResponse(m.getId(), m.getAccount().getId(), m.getAccount().getUsername(), m.getAccount().getDisplayName(), m.getRole(), m.getCreatedAt()); }
    private ApiException error(String code, String message) { return new ApiException(HttpStatus.FORBIDDEN, code, message); }
}
