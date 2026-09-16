package com.cmrt.pfe.bootstrap;

import com.cmrt.pfe.models.Issue;
import com.cmrt.pfe.models.Product;
import com.cmrt.pfe.models.Task;
import com.cmrt.pfe.models.TestResource;
import com.cmrt.pfe.models.User;
import com.cmrt.pfe.models.enums.Customer;
import com.cmrt.pfe.models.enums.Departement;
import com.cmrt.pfe.models.enums.IssueCategory;
import com.cmrt.pfe.models.enums.Poste;
import com.cmrt.pfe.models.enums.Priority;
import com.cmrt.pfe.models.enums.ProductFamily;
import com.cmrt.pfe.models.enums.ProductStatus;
import com.cmrt.pfe.models.enums.ProjectType;
import com.cmrt.pfe.models.enums.ResourceStatus;
import com.cmrt.pfe.models.enums.ResourceType;
import com.cmrt.pfe.models.enums.Role;
import com.cmrt.pfe.models.enums.ServiceUnit;
import com.cmrt.pfe.models.enums.Severity;
import com.cmrt.pfe.models.enums.StageType;
import com.cmrt.pfe.models.enums.TaskStatus;
import com.cmrt.pfe.repositories.ProductRepository;
import com.cmrt.pfe.repositories.ResourceRepository;
import com.cmrt.pfe.repositories.TaskRepository;
import com.cmrt.pfe.repositories.UserRepository;
import com.cmrt.pfe.services.IssueService;
import com.cmrt.pfe.services.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds a demonstrable dataset on an empty database: one account per role, products on
 * both boards with their pipelines partly advanced, test benches, tasks and a couple of
 * live blockages. Runs only when {@code app.seed.enabled} is true and the users
 * collection is empty, so it never overwrites real data.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ResourceRepository resourceRepository;
    private final TaskRepository taskRepository;
    private final WorkflowService workflowService;
    private final IssueService issueService;

    @Value("${app.seed.enabled}")
    private boolean enabled;

    @Value("${app.seed.default-password}")
    private String defaultPassword;

    @Override
    public void run(String... args) {
        if (!enabled) {
            log.info("Jeu de donnees de demonstration desactive");
            return;
        }
        if (userRepository.count() > 0) {
            log.info("Base deja peuplee : le seeder ne fait rien");
            return;
        }

        log.info("Creation du jeu de donnees de demonstration...");
        List<User> users = seedUsers();
        User admin = byRole(users, Role.ADMIN);
        User chef = byRole(users, Role.CHEF_PROJET);
        User methodiste = byRole(users, Role.METHODISTE);
        User qualiticien = byRole(users, Role.QUALITICIEN);
        User controle = byRole(users, Role.CONTROLE_TECHNIQUE);

        seedResources(controle);
        List<Product> products = seedProducts(chef, methodiste, qualiticien);
        seedTasks(products, methodiste, qualiticien, controle);
        seedIssues(products, admin);
        advancePipelines(products, admin);

        log.info("Jeu de donnees pret : {} utilisateurs, {} produits. Mot de passe commun : {}",
                users.size(), products.size(), defaultPassword);
    }

    // ------------------------------------------------------------------

    private List<User> seedUsers() {
        String hash = BCrypt.hashpw(defaultPassword, BCrypt.gensalt());
        List<User> users = List.of(
                user("ADM001", "Lachkar", "Aziz", "admin@cmrt.tn", hash, Role.ADMIN,
                        Departement.ENGINEERING, Poste.DIRECTEUR, ServiceUnit.METHODE),
                user("CP001", "Ben Salah", "Yassine", "chef.projet@cmrt.tn", hash, Role.CHEF_PROJET,
                        Departement.ENGINEERING, Poste.CHEF_PROJET, ServiceUnit.NPI),
                user("MET001", "Trabelsi", "Ahmed", "methodiste@cmrt.tn", hash, Role.METHODISTE,
                        Departement.ENGINEERING, Poste.INGENIEUR, ServiceUnit.METHODE),
                user("MET002", "Gharbi", "Youssef", "methodiste2@cmrt.tn", hash, Role.METHODISTE,
                        Departement.ENGINEERING, Poste.TECHNICIEN, ServiceUnit.NPI),
                user("QUA001", "Mansouri", "Sarra", "qualite@cmrt.tn", hash, Role.QUALITICIEN,
                        Departement.QHSE, Poste.INGENIEUR, ServiceUnit.QUALITE),
                user("QUA002", "Hamdi", "Fatma", "qualite2@cmrt.tn", hash, Role.QUALITICIEN,
                        Departement.QHSE, Poste.TECHNICIEN, ServiceUnit.QUALITE),
                user("CT001", "Jendoubi", "Khaled", "controle@cmrt.tn", hash, Role.CONTROLE_TECHNIQUE,
                        Departement.ENGINEERING, Poste.INGENIEUR, ServiceUnit.CONTROLE_TECHNIQUE),
                user("PRD001", "Aouni", "Mohamed", "production@cmrt.tn", hash, Role.RESPONSABLE_PRODUCTION,
                        Departement.PRODUCTION, Poste.RESPONSABLE, ServiceUnit.PRODUCTION),
                user("TEC001", "Riahi", "Ali", "technicien@cmrt.tn", hash, Role.TECHNICIEN,
                        Departement.PRODUCTION, Poste.TECHNICIEN, ServiceUnit.PRODUCTION),
                user("SUP001", "Karray", "Ines", "superviseur@cmrt.tn", hash, Role.VIEWER,
                        Departement.PRODUCTION, Poste.SUPERVISEUR, ServiceUnit.PRODUCTION));
        return userRepository.saveAll(users);
    }

    private User user(String matricule, String nom, String prenom, String email, String hash,
                      Role role, Departement departement, Poste poste, ServiceUnit service) {
        return User.builder()
                .matricule(matricule).nom(nom).prenom(prenom).email(email).password(hash)
                .role(role).departement(departement).poste(poste).serviceUnit(service)
                // Demo accounts are pre-verified so the platform is usable without SMTP.
                .enabled(true).active(true)
                .build();
    }

    private User byRole(List<User> users, Role role) {
        return users.stream().filter(u -> u.getRole() == role).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------

    private void seedResources(User owner) {
        resourceRepository.saveAll(List.of(
                resource("BT-HT-01", "Banc de test haute tension A", ResourceType.TEST_BOARD,
                        "Zone 1 - Build Board", ResourceStatus.AVAILABLE, owner, true,
                        LocalDate.now().plusMonths(4)),
                resource("BT-CONT-02", "Banc de continuite B", ResourceType.TEST_BOARD,
                        "Zone 2 - Test Board", ResourceStatus.IN_USE, owner, true,
                        LocalDate.now().plusMonths(2)),
                resource("IT-ETA-03", "Testeur d'etancheite pipe", ResourceType.TEST_INTERFACE,
                        "Zone 3 - Controle", ResourceStatus.MAINTENANCE, owner, true,
                        LocalDate.now().plusMonths(1)),
                resource("BB-ASM-04", "Build board assemblage faisceau", ResourceType.BUILD_BOARD,
                        "Zone 1 - Build Board", ResourceStatus.AVAILABLE, owner, false, null),
                resource("OUT-SER-05", "Outillage sertissage automatique", ResourceType.TOOLING,
                        "Atelier outillage", ResourceStatus.AVAILABLE, owner, true,
                        LocalDate.now().minusDays(10)),
                resource("FIX-WAB-06", "Gabarit de controle Wabtec", ResourceType.FIXTURE,
                        "Zone 3 - Controle", ResourceStatus.AVAILABLE, owner, false, null)));
    }

    private TestResource resource(String code, String nom, ResourceType type, String location,
                                  ResourceStatus status, User owner, boolean calibration, LocalDate expiry) {
        return TestResource.builder()
                .code(code).nom(nom).type(type).location(location).status(status)
                .ownerId(owner.getId())
                .requiresCalibration(calibration)
                .calibrationExpiry(expiry)
                .lastMaintenanceDate(LocalDate.now().minusMonths(2))
                .nextMaintenanceDate(LocalDate.now().plusMonths(4))
                .compatibleProductIds(new ArrayList<>())
                .build();
    }

    // ------------------------------------------------------------------

    private List<Product> seedProducts(User chef, User methodiste, User qualiticien) {
        List<Product> products = productRepository.saveAll(List.of(
                product("FA-CUM-1042", "Faisceau moteur Cummins QSK60", ProductFamily.FAISCEAU,
                        Customer.CUMMINS, ProjectType.NPI, ProductStatus.IN_DEVELOPMENT, Priority.HIGH,
                        chef, methodiste, qualiticien, 45, 12000, "QSK60 Tier 4"),
                product("CB-WAU-2207", "Cable capteur haute temperature Waukesha", ProductFamily.CABLE,
                        Customer.WAUKESHA, ProjectType.NPI, ProductStatus.IN_DEVELOPMENT, Priority.CRITICAL,
                        chef, methodiste, qualiticien, 30, 8000, "VHP Series"),
                product("PI-WAB-3310", "Pipe de refroidissement Wabtec", ProductFamily.PIPE,
                        Customer.WABTEC, ProjectType.NPI, ProductStatus.DRAFT, Priority.MEDIUM,
                        chef, methodiste, qualiticien, 70, 5000, "Locomotive ES44"),
                product("FA-CUM-0871", "Faisceau cabine Cummins X15", ProductFamily.FAISCEAU,
                        Customer.CUMMINS, ProjectType.PRODUCTION, ProductStatus.MASS_PRODUCTION, Priority.MEDIUM,
                        chef, methodiste, qualiticien, -60, 24000, "X15 Efficiency"),
                product("CB-WAB-0455", "Cable de signalisation Wabtec", ProductFamily.CABLE,
                        Customer.WABTEC, ProjectType.PRODUCTION, ProductStatus.MASS_PRODUCTION, Priority.LOW,
                        chef, methodiste, qualiticien, -90, 16000, "Signal Line"),
                product("SE-WAU-0620", "Sous-ensemble allumage Waukesha", ProductFamily.SOUS_ENSEMBLE,
                        Customer.WAUKESHA, ProjectType.PRODUCTION, ProductStatus.PILOT, Priority.HIGH,
                        chef, methodiste, qualiticien, -20, 9500, "APG Series")));

        products.forEach(product -> workflowService.initializePipeline(product, null));
        return products;
    }

    private Product product(String reference, String nom, ProductFamily family, Customer customer,
                            ProjectType type, ProductStatus status, Priority priority,
                            User chef, User methodiste, User qualiticien,
                            int sopOffsetDays, int volume, String programme) {
        return Product.builder()
                .reference(reference).nom(nom).family(family).customer(customer)
                .projectType(type).status(status).priority(priority)
                .chefProjetId(chef.getId())
                .methodisteId(methodiste.getId())
                .qualiticienId(qualiticien.getId())
                // Production items started well before their SOP; NPI items are still ramping up.
                .startDate(LocalDate.now().minusDays(Math.max(20, 90 - sopOffsetDays)))
                .targetSopDate(LocalDate.now().plusDays(sopOffsetDays))
                .annualVolume(volume)
                .programme(programme)
                .description("Produit " + family + " pour le programme " + programme + ".")
                .tags(new ArrayList<>(List.of(customer.name().toLowerCase(), family.name().toLowerCase())))
                .build();
    }

    // ------------------------------------------------------------------

    private void seedTasks(List<Product> products, User methodiste, User qualiticien, User controle) {
        List<Task> tasks = new ArrayList<>();
        Product first = products.get(0);
        Product second = products.get(1);

        tasks.add(task("Verifier la nomenclature des connecteurs Deutsch", first, StageType.BOM_VALIDATION,
                methodiste, TaskStatus.IN_PROGRESS, Priority.HIGH, 2));
        tasks.add(task("Commander les manchons thermoretractables", first, StageType.MATERIAL_AVAILABILITY,
                methodiste, TaskStatus.TODO, Priority.MEDIUM, 6));
        tasks.add(task("Definir le plan de controle dimensionnel", first, StageType.QUALITY_VALIDATION,
                qualiticien, TaskStatus.TODO, Priority.HIGH, 9));
        tasks.add(task("Concevoir l'interface de test continuite", second, StageType.TEST_BOARD_DESIGN,
                controle, TaskStatus.IN_REVIEW, Priority.CRITICAL, 1));
        tasks.add(task("Valider la resistance thermique du cable", second, StageType.ENGINEERING_REVIEW,
                qualiticien, TaskStatus.DONE, Priority.HIGH, -3));
        tasks.add(task("Mettre a jour l'instruction de travail atelier", products.get(3), null,
                methodiste, TaskStatus.TODO, Priority.LOW, -1));

        taskRepository.saveAll(tasks);
    }

    private Task task(String title, Product product, StageType stage, User assignee,
                      TaskStatus status, Priority priority, int dueInDays) {
        return Task.builder()
                .title(title)
                .productId(product.getId())
                .stageType(stage)
                .assigneeId(assignee.getId())
                .status(status)
                .priority(priority)
                .dueDate(LocalDate.now().plusDays(dueInDays))
                .estimatedHours(8.0)
                .checklist(new ArrayList<>())
                .tags(new ArrayList<>())
                .build();
    }

    // ------------------------------------------------------------------

    private void seedIssues(List<Product> products, User admin) {
        com.cmrt.pfe.security.AuthPrincipal actor = new com.cmrt.pfe.security.AuthPrincipal(
                admin.getId(), admin.getEmail(), Role.ADMIN.name(), admin.getFullName());

        Issue material = new Issue();
        material.setProductId(products.get(0).getId());
        material.setStageType(StageType.MATERIAL_AVAILABILITY);
        material.setTitle("Rupture de stock sur le connecteur DT06-12S");
        material.setCategory(IssueCategory.MATIERE);
        material.setSeverity(Severity.BLOCKING);
        material.setBlocksStage(true);
        material.setDescription("Le fournisseur annonce 4 semaines de delai supplementaire, "
                + "la fabrication du prototype est a l'arret.");
        issueService.create(material, actor);

        Issue tooling = new Issue();
        tooling.setProductId(products.get(1).getId());
        tooling.setStageType(StageType.TEST_BOARD_DESIGN);
        tooling.setTitle("Interface de test incompatible avec le nouveau connecteur");
        tooling.setCategory(IssueCategory.MOYEN_DE_TEST);
        tooling.setSeverity(Severity.CRITICAL);
        tooling.setBlocksStage(false);
        tooling.setDescription("Le brochage du banc BT-CONT-02 ne correspond pas a la revision B du plan.");
        issueService.create(tooling, actor);

        Issue doc = new Issue();
        doc.setProductId(products.get(3).getId());
        doc.setTitle("Instruction de travail obsolete au poste 12");
        doc.setCategory(IssueCategory.DOCUMENTATION);
        doc.setSeverity(Severity.MINOR);
        doc.setBlocksStage(false);
        doc.setDescription("L'operateur utilise encore la revision A alors que la revision C est approuvee.");
        issueService.create(doc, actor);
    }

    /**
     * Pushes a few pipelines forward so the dashboards open on something meaningful
     * rather than a wall of untouched gates.
     */
    private void advancePipelines(List<Product> products, User admin) {
        com.cmrt.pfe.security.AuthPrincipal actor = new com.cmrt.pfe.security.AuthPrincipal(
                admin.getId(), admin.getEmail(), Role.ADMIN.name(), admin.getFullName());

        for (Product product : products) {
            int gatesToClose = switch (product.getReference()) {
                case "FA-CUM-1042" -> 2;
                case "CB-WAU-2207" -> 4;
                case "FA-CUM-0871" -> 8;
                case "CB-WAB-0455" -> 8;
                case "SE-WAU-0620" -> 5;
                default -> 0;
            };
            closeFirstGates(product, gatesToClose, actor);
        }
    }

    private void closeFirstGates(Product product, int count, com.cmrt.pfe.security.AuthPrincipal actor) {
        var stages = workflowService.stagesOf(product.getId());
        for (int i = 0; i < Math.min(count, stages.size()); i++) {
            var stage = stages.get(i);
            try {
                // Seeded history bypasses the deliverable gate on purpose: the point is to
                // show a pipeline mid-flight, not to fabricate approved documents.
                workflowService.skipStage(product.getId(), stage.getStageType(),
                        "Jalon cloture (donnees de demonstration)", actor);
            } catch (RuntimeException e) {
                log.debug("Jalon {} non avance pour {} : {}",
                        stage.getStageType(), product.getReference(), e.getMessage());
            }
        }
    }
}
