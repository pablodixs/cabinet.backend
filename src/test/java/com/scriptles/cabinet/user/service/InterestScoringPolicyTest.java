package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InterestScoringPolicyTest {
    private final InterestScoringPolicy policy = new InterestScoringPolicy();

    @Test
    void centersRatingsAtThreeAndSaturatesImplicitSignals() {
        assertThat(policy.rating(new BigDecimal("0.5"))).isEqualTo(-5.0);
        assertThat(policy.rating(new BigDecimal("3.0"))).isZero();
        assertThat(policy.rating(new BigDecimal("5.0"))).isEqualTo(4.0);
        assertThat(policy.implicitSeed(9.0)).isEqualTo(6.0);
        assertThat(policy.inferredNode(-20.0)).isEqualTo(-8.0);
    }

    @Test
    void appliesLibraryAndCreditWeights() {
        assertThat(policy.library(UserMediaStatus.PLANNED)).isEqualTo(0.5);
        assertThat(policy.library(UserMediaStatus.COMPLETED)).isEqualTo(2.0);
        assertThat(policy.library(UserMediaStatus.DROPPED)).isEqualTo(-2.0);
        assertThat(policy.role(CreditRole.DIRECTOR, 20)).isEqualTo(1.0);
        assertThat(policy.role(CreditRole.ACTOR, 9)).isEqualTo(0.5);
        assertThat(policy.role(CreditRole.ACTOR, 10)).isEqualTo(0.2);
    }

    @Test
    void normalizesCaseAndRepeatedWhitespaceWithoutRemovingAccents() {
        assertThat(policy.normalizeGenre("  Ficção   Científica "))
                .isEqualTo("ficção científica");
    }
}
