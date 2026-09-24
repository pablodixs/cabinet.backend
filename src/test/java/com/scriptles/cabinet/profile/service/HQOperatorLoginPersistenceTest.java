package com.scriptles.cabinet.profile.service;

import com.scriptles.cabinet.profile.entity.HQOperator;
import com.scriptles.cabinet.profile.entity.HQProfile;
import com.scriptles.cabinet.profile.entity.Profile;
import com.scriptles.cabinet.profile.enums.HQMemberRole;
import com.scriptles.cabinet.profile.enums.HQType;
import com.scriptles.cabinet.profile.enums.ProfileType;
import com.scriptles.cabinet.profile.repository.HQOperatorRepository;
import com.scriptles.cabinet.profile.repository.HQProfileRepository;
import com.scriptles.cabinet.profile.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class HQOperatorLoginPersistenceTest {
    @Autowired ProfileRepository profiles;
    @Autowired HQProfileRepository hqs;
    @Autowired HQOperatorRepository operators;

    @Test
    void operatorCreatedForExistingHQCanLogIn() {
        Profile profile = new Profile();
        profile.setType(ProfileType.HQ);
        profile.setHandle("studio-test");
        profile.setDisplayName("Studio Test");
        profile = profiles.saveAndFlush(profile);
        HQProfile hq = new HQProfile();
        hq.setProfile(profile);
        hq.setHqType(HQType.FILM_STUDIO);
        hq = hqs.saveAndFlush(hq);

        HQOperatorService service = new HQOperatorService(operators, hqs, new BCryptPasswordEncoder());
        var created = service.create(profile.getId(), new HQOperatorService.OperatorInput(
                "Editor@Example.com", "Editor", "long-password", HQMemberRole.OWNER));
        assertThat(created.active()).isTrue();
        assertThat(operators.findByHqProfileIdOrderByCreatedAtAsc(hq.getId())).hasSize(1);

        var authenticated = service.authenticate("studio-test", "editor@example.com", "long-password");
        assertThat(authenticated.operatorId()).isEqualTo(created.id());
        assertThat(authenticated.hqId()).isEqualTo(hq.getId());

        Profile otherProfile = new Profile();
        otherProfile.setType(ProfileType.HQ);
        otherProfile.setHandle("other-studio");
        otherProfile.setDisplayName("Other Studio");
        otherProfile = profiles.saveAndFlush(otherProfile);
        HQProfile otherHq = new HQProfile();
        otherHq.setProfile(otherProfile);
        otherHq.setHqType(HQType.FILM_STUDIO);
        hqs.saveAndFlush(otherHq);
        var otherProfileId = otherProfile.getId();
        assertThatThrownBy(() -> service.resetPassword(otherProfileId, created.id(), "replacement-password"))
                .isInstanceOf(com.scriptles.cabinet.common.api.ApiException.class);

        service.resetPassword(profile.getId(), created.id(), "replacement-password");
        assertThatThrownBy(() -> service.authenticate("studio-test", "editor@example.com", "long-password"))
                .isInstanceOf(com.scriptles.cabinet.common.api.ApiException.class);
        assertThat(service.authenticate("studio-test", "editor@example.com", "replacement-password").operatorId())
                .isEqualTo(created.id());
    }
}
