package com.cmrt.pfe.models;

import com.cmrt.pfe.models.enums.ResourceStatus;
import com.cmrt.pfe.models.enums.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A bookable technical-control asset: test board, build board, test interface, tooling
 * or fixture. Replaces the original TestInterface entity and adds ownership, calibration
 * and compatibility so reservations can be validated rather than merely recorded.
 */
@Document(collection = "resources")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestResource {

    @Id
    private String id;

    @Indexed(unique = true, sparse = true)
    private String code;

    private String nom;

    private ResourceType type;

    @Builder.Default
    private ResourceStatus status = ResourceStatus.AVAILABLE;

    private String location;

    private String description;

    /** Technical-control user accountable for the asset. */
    private String ownerId;

    /** Product ids this asset can be used for; empty means universal. */
    @Builder.Default
    private List<String> compatibleProductIds = new ArrayList<>();

    private LocalDate lastMaintenanceDate;

    private LocalDate nextMaintenanceDate;

    /** Set when the asset needs periodic calibration to stay valid for quality tests. */
    @Builder.Default
    private boolean requiresCalibration = false;

    private LocalDate calibrationExpiry;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    /** True when calibration has lapsed and results from this asset cannot be trusted. */
    public boolean isCalibrationExpired() {
        return requiresCalibration
                && calibrationExpiry != null
                && calibrationExpiry.isBefore(LocalDate.now());
    }

    public boolean isBookable() {
        return status == ResourceStatus.AVAILABLE || status == ResourceStatus.RESERVED;
    }
}
