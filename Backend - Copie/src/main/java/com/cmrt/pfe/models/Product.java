package com.cmrt.pfe.models;

import com.cmrt.pfe.models.enums.Customer;
import com.cmrt.pfe.models.enums.Priority;
import com.cmrt.pfe.models.enums.ProductFamily;
import com.cmrt.pfe.models.enums.ProductStatus;
import com.cmrt.pfe.models.enums.ProjectType;
import com.cmrt.pfe.models.enums.StageType;
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
 * A product under industrialisation. Each product carries one pipeline of
 * {@link ProjectStage} gates; references to users are held as plain ids so that a single
 * product read never drags the whole user graph with it.
 */
@Document(collection = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    private String id;

    @Indexed(unique = true, sparse = true)
    private String reference;

    private String nom;

    private String description;

    private ProductFamily family;

    private Customer customer;

    /** Distinguishes the "Methode Production" board from the "Methode NPI" board. */
    private ProjectType projectType;

    private ProductStatus status;

    private Priority priority;

    /** Owner ids. Kept flat (no @DBRef) and resolved on demand by the service layer. */
    private String methodisteId;

    private String qualiticienId;

    private String chefProjetId;

    private LocalDate startDate;

    /** Contractual start-of-production date the whole pipeline is planned backwards from. */
    private LocalDate targetSopDate;

    private LocalDate actualSopDate;

    /** Cached workflow state, recomputed by the workflow engine on every stage transition. */
    private StageType currentStage;

    @Builder.Default
    private int progressPercent = 0;

    @Builder.Default
    private boolean blocked = false;

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    private Integer annualVolume;

    private String programme;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    private String createdBy;
}
