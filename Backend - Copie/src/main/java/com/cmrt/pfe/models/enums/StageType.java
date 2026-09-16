package com.cmrt.pfe.models.enums;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The ten gates of the CMRT industrialisation pipeline.
 * <p>
 * Every stage carries its own template: the order it runs in, the role that owns it by
 * default, the deliverables that must be attached before it can close, the roles whose
 * approval gates it, and the nominal lead time used to plan deadlines. The workflow
 * engine reads this template when a project is created, so adding or reordering a gate
 * is a one-line change here rather than a migration.
 */
public enum StageType {

    ENGINEERING_REVIEW(
            1, "Revue technique", Role.CHEF_PROJET, 5,
            List.of(DocumentType.DRAWING, DocumentType.CUSTOMER_SPEC),
            List.of(Role.CHEF_PROJET)),

    BOM_VALIDATION(
            2, "Validation de la nomenclature (BOM)", Role.METHODISTE, 4,
            List.of(DocumentType.BOM),
            List.of(Role.METHODISTE, Role.QUALITICIEN)),

    MATERIAL_AVAILABILITY(
            3, "Disponibilite matiere", Role.METHODISTE, 10,
            List.of(DocumentType.BOM),
            List.of(Role.METHODISTE)),

    TOOLING_PREPARATION(
            4, "Preparation outillage", Role.METHODISTE, 8,
            List.of(DocumentType.TOOLING_DRAWING, DocumentType.ROUTING),
            List.of(Role.METHODISTE)),

    TEST_BOARD_DESIGN(
            5, "Conception du banc de test", Role.CONTROLE_TECHNIQUE, 7,
            List.of(DocumentType.TEST_PROCEDURE, DocumentType.LAYOUT),
            List.of(Role.CONTROLE_TECHNIQUE, Role.QUALITICIEN)),

    PROTOTYPE_MANUFACTURING(
            6, "Fabrication du prototype", Role.TECHNICIEN, 6,
            List.of(DocumentType.WORK_INSTRUCTION),
            List.of(Role.METHODISTE)),

    QUALITY_VALIDATION(
            7, "Validation qualite", Role.QUALITICIEN, 5,
            List.of(DocumentType.CONTROL_PLAN, DocumentType.FAI_REPORT),
            List.of(Role.QUALITICIEN)),

    CUSTOMER_APPROVAL(
            8, "Approbation client", Role.CHEF_PROJET, 12,
            List.of(DocumentType.PPAP),
            List.of(Role.CHEF_PROJET, Role.QUALITICIEN)),

    PILOT_PRODUCTION(
            9, "Production pilote", Role.RESPONSABLE_PRODUCTION, 6,
            List.of(DocumentType.WORK_INSTRUCTION, DocumentType.QUALITY_REPORT),
            List.of(Role.RESPONSABLE_PRODUCTION, Role.QUALITICIEN)),

    MASS_PRODUCTION_RELEASE(
            10, "Lancement en serie", Role.RESPONSABLE_PRODUCTION, 3,
            List.of(DocumentType.ROUTING, DocumentType.CONTROL_PLAN),
            List.of(Role.RESPONSABLE_PRODUCTION, Role.CHEF_PROJET));

    private final int order;
    private final String label;
    private final Role defaultOwnerRole;
    private final int nominalDurationDays;
    private final List<DocumentType> requiredDeliverables;
    private final List<Role> approverRoles;

    StageType(int order, String label, Role defaultOwnerRole, int nominalDurationDays,
              List<DocumentType> requiredDeliverables, List<Role> approverRoles) {
        this.order = order;
        this.label = label;
        this.defaultOwnerRole = defaultOwnerRole;
        this.nominalDurationDays = nominalDurationDays;
        this.requiredDeliverables = requiredDeliverables;
        this.approverRoles = approverRoles;
    }

    public int getOrder() {
        return order;
    }

    public String getLabel() {
        return label;
    }

    public Role getDefaultOwnerRole() {
        return defaultOwnerRole;
    }

    public int getNominalDurationDays() {
        return nominalDurationDays;
    }

    public List<DocumentType> getRequiredDeliverables() {
        return requiredDeliverables;
    }

    public List<Role> getApproverRoles() {
        return approverRoles;
    }

    /** Stages in execution order. */
    public static List<StageType> pipeline() {
        return Arrays.stream(values())
                .sorted(Comparator.comparingInt(StageType::getOrder))
                .toList();
    }

    /** The gate that follows this one, or {@code null} when this is the final release gate. */
    public StageType next() {
        return pipeline().stream()
                .filter(s -> s.order == this.order + 1)
                .findFirst()
                .orElse(null);
    }

    /**
     * NPI projects run the full ten gates. Products already in serial life re-enter the
     * pipeline for change management only, so the prototype and customer-approval gates
     * are skipped by default.
     */
    public static List<StageType> pipelineFor(ProjectType projectType) {
        if (projectType == ProjectType.NPI) {
            return pipeline();
        }
        return pipeline().stream()
                .filter(s -> s != PROTOTYPE_MANUFACTURING && s != CUSTOMER_APPROVAL)
                .toList();
    }
}
