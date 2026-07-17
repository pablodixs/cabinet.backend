package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.MediaAvailabilityOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MediaAvailabilityOfferRepository extends JpaRepository<MediaAvailabilityOffer, UUID> {
    List<MediaAvailabilityOffer> findAllByMediaIdAndCountryCodeOrderByDisplayPriorityAscProviderNameAsc(
            UUID mediaId,
            String countryCode
    );

    void deleteAllByMediaIdAndCountryCode(UUID mediaId, String countryCode);
}
