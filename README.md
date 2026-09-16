# Plateforme Engineering CMRT

Plateforme de pilotage de l'industrialisation pour **CMRT** (Contrôle, Mesure et Régulation
Tunisie), fabricant de faisceaux, câbles et pipes pour **Cummins**, **Waukesha** et **Wabtec**.

L'outil relie le département Engineering à la Production, à la Qualité et au Contrôle
technique autour d'un même cycle de vie produit : chaque produit suit un **pipeline
d'industrialisation en 10 jalons**, avec responsables, échéances, livrables obligatoires,
règles d'approbation et notifications automatiques.

---

## 1. Le problème traité

Avant la plateforme, la coordination reposait sur des échanges informels : un blocage en
production remontait par téléphone, les livrables d'un jalon vivaient dans des dossiers
partagés, et personne ne disposait d'une vue consolidée de l'avancement des projets NPI.

La plateforme apporte trois choses que ces échanges ne permettaient pas :

1. **Un cycle de vie qui se vérifie tout seul.** Un jalon ne peut pas se clôturer si ses
   livrables ne sont pas déposés *et approuvés*, si un blocage le gèle, ou si les rôles
   requis n'ont pas signé.
2. **Une traçabilité complète.** Chaque approbation, révision de document, décision ECR ou
   réservation est enregistrée avec son auteur et son horodatage.
3. **Des indicateurs calculés, pas déclarés.** Avancement, respect des délais, SLA des
   blocages et utilisation des moyens de test sont dérivés des données réelles.

---

## 2. Les trois onglets métier

| Onglet | Rôle |
|---|---|
| **Méthode Production** | Produits en vie série : évolutions, industrialisation continue, remontée des blocages. Pipeline de 8 jalons. |
| **Méthode NPI** | Nouveaux produits, de la revue technique au lancement série. Pipeline complet de 10 jalons. |
| **Contrôle technique** | État des interfaces de test, organisation des build &amp; test boards, réservation des moyens avec détection de conflits. |

À chaque produit sont affectés un **méthodiste**, un **qualiticien** et un **chef de
projet**. Le bouton **Réclamation**, présent sur chaque ligne, ouvre la déclaration de
blocage ; si le blocage est marqué bloquant, le jalon concerné est gelé immédiatement.

---

## 3. Le pipeline d'industrialisation

| # | Jalon | Responsable par défaut | Durée | Livrables requis | Approbations |
|---|---|---|---|---|---|
| 1 | Revue technique | Chef de projet | 5 j | Plan, Spécification client | Chef de projet |
| 2 | Validation nomenclature (BOM) | Méthodiste | 4 j | BOM | Méthodiste, Qualiticien |
| 3 | Disponibilité matière | Méthodiste | 10 j | BOM | Méthodiste |
| 4 | Préparation outillage | Méthodiste | 8 j | Plan outillage, Gamme | Méthodiste |
| 5 | Conception banc de test | Contrôle technique | 7 j | Procédure de test, Implantation | Contrôle technique, Qualiticien |
| 6 | Fabrication prototype | Technicien | 6 j | Instruction de travail | Méthodiste |
| 7 | Validation qualité | Qualiticien | 5 j | Plan de contrôle, Rapport FAI | Qualiticien |
| 8 | Approbation client | Chef de projet | 12 j | Dossier PPAP | Chef de projet, Qualiticien |
| 9 | Production pilote | Responsable production | 6 j | Instruction de travail, Rapport qualité | Resp. production, Qualiticien |
| 10 | Lancement en série | Responsable production | 3 j | Gamme, Plan de contrôle | Resp. production, Chef de projet |

Les projets **Production** sautent les jalons 6 et 8 (prototype et approbation client).

Les durées sont des **jours ouvrés** : le planning est déroulé à partir de la date de
lancement du produit, week-ends exclus.

Le modèle est défini une seule fois, dans l'énumération
[`StageType`](Backend%20-%20Copie/src/main/java/com/cmrt/pfe/models/enums/StageType.java).
Ajouter ou réordonner un jalon est une modification d'une ligne, sans migration.

### Règles appliquées par le moteur de workflow

- **Ordre strict** : un jalon ne démarre que si le précédent est clôturé ou ignoré.
- **Barrière des livrables** : la soumission à validation est refusée tant qu'un type de
  document requis n'a pas de révision **approuvée**.
- **Barrière des blocages** : un blocage ouvert marqué « bloquant » gèle le jalon.
- **Signatures multiples** : le jalon ne se clôture que lorsque *tous* les rôles requis ont
  approuvé. Un refus renvoie le jalon à son responsable et remet les signatures à zéro.
- **Enchaînement automatique** : la clôture d'un jalon ouvre le suivant et notifie son
  responsable. La clôture du dernier bascule le produit en production série.

Ces règles vivent exclusivement dans
[`WorkflowService`](Backend%20-%20Copie/src/main/java/com/cmrt/pfe/services/WorkflowService.java) :
les contrôleurs ne modifient jamais un jalon directement, donc la règle s'applique quel que
soit l'écran d'où vient l'action.

---

## 4. Modules

| Module | Contenu |
|---|---|
| **Tableau de bord** | 16 indicateurs, entonnoir du pipeline, répartitions par gravité / client / moyen, débit mensuel, liste des urgences, activité récente, charge de l'équipe. |
| **Gestion des tâches** | Tableau kanban à 4 colonnes, glisser-déposer, priorités, échéances, rattachement à un jalon. |
| **Suivi des blocages** | Catégorie, gravité, SLA calculé, cause racine, action corrective, fil de discussion, gel du jalon. |
| **Gestion des modifications (ECR/ECO)** | Brouillon → revue → décision → mise en œuvre. Le circuit d'approbation est **dérivé de l'impact** déclaré (outillage, moyens de test, client). |
| **Documents maîtrisés** | Révisions A, B, C… immuables. Approuver une révision rend la précédente obsolète ; une seule est en vigueur à la fois. |
| **Moyens de test** | Bancs, build boards, interfaces, outillages : statut, calibration, maintenance. Réservation par créneau avec refus des chevauchements. |
| **Notifications** | Boîte de réception in-app, badge temps réel, doublage par email pour les alertes critiques. |
| **Administration** | Comptes, rôles, validation des inscriptions, traçabilité globale, référentiel du pipeline. |

### SLA des blocages

| Gravité | Délai de réponse |
|---|---|
| Bloquante | 4 h |
| Critique | 8 h |
| Majeure | 24 h |
| Mineure | 72 h |

Un balayage automatique quotidien (7 h, du lundi au vendredi) alerte sur les jalons en
retard, les tâches dues ou dépassées et les SLA rompus, avec escalade au chef de projet.

---

## 5. Architecture

```
Angular 18 (SPA)                    Spring Boot 4 (API REST)              MongoDB
─────────────────                   ────────────────────────              ───────
core/     services + JWT   ──────►  controllers/  ← @RequireRole          users
shared/   charts + UI               services/     ← règles métier         products
features/ écrans métier             repositories/                         project_stages
layout/   shell + menu              security/     ← JWT + intercepteur    tasks, issues
                                    bootstrap/    ← jeu de démonstration  documents, ...
```

**Backend** — Spring Boot 4.0.4, Java 17, MongoDB.
L'authentification est stateless par JWT (HS384, 8 h). L'autorisation passe par un
`HandlerInterceptor` et l'annotation `@RequireRole` posée sur les endpoints : les règles
restent lisibles à côté du code qu'elles protègent. Les mots de passe sont hachés avec
BCrypt.

**Frontend** — Angular 18 standalone, signals, routes chargées à la demande.
Aucune dépendance graphique externe : les graphiques (anneau, barres, courbe) sont des
composants SVG maison, ce qui évite ~300 ko de bundle et garantit une charte visuelle
homogène. Un intercepteur HTTP ajoute le jeton et transforme les erreurs de l'API en
notifications.

**Points de conception**

- Les identifiants d'utilisateurs sont stockés à plat (pas de `@DBRef`) : une lecture de
  produit ne tire pas tout le graphe d'utilisateurs. Les noms sont résolus en une requête
  groupée par le service.
- Les jalons sont une collection séparée, pas un tableau imbriqué : les vues transverses
  (« tout ce qui est bloqué en validation BOM ») sont une seule requête indexée.
- L'avancement et le jalon courant sont **recalculés et mis en cache** à chaque transition,
  donc les tableaux n'agrègent rien à la lecture.

---

## 6. Démarrage

### Prérequis
Java 17+, Maven 3.9+, Node 18+, MongoDB en écoute sur `localhost:27017`.

### Backend
```bash
cd "Backend - Copie"
mvn spring-boot:run
```
API sur `http://localhost:8080`. Au premier démarrage sur une base vide, un jeu de
démonstration est créé : 10 comptes, 6 produits avec leurs pipelines partiellement
avancés, 6 moyens de test, des tâches et des blocages.

### Frontend
```bash
cd cmrt-frontend
npm install
npm start
```
Application sur `http://localhost:4200`.

### Comptes de démonstration

Mot de passe commun : `Cmrt@2026!Pfe`

| Email | Rôle |
|---|---|
| `admin@cmrt.tn` | Administrateur |
| `chef.projet@cmrt.tn` | Chef de projet |
| `methodiste@cmrt.tn` | Méthodiste |
| `qualite@cmrt.tn` | Qualiticien |
| `controle@cmrt.tn` | Contrôle technique |
| `production@cmrt.tn` | Responsable production |
| `technicien@cmrt.tn` | Technicien |
| `superviseur@cmrt.tn` | Consultation |

---

## 7. Configuration

Tous les paramètres sont surchargeables par variables d'environnement — aucun secret n'est
écrit en dur dans le dépôt.

| Variable | Défaut | Rôle |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/cmrt_engineering` | Base de données |
| `JWT_SECRET` | valeur de développement | Clé de signature — **à changer en production** |
| `JWT_EXPIRATION_MS` | `28800000` | Durée de validité du jeton (8 h) |
| `CORS_ORIGINS` | `http://localhost:4200` | Origines autorisées |
| `UPLOAD_DIR` | `./uploads` | Stockage des révisions de documents |
| `SEED_ENABLED` | `true` | Jeu de démonstration sur base vide |
| `MAIL_ENABLED` | `false` | Envoi réel des emails |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | vide | Identifiants SMTP |

> **Emails.** La livraison est désactivée par défaut : la plateforme fonctionne de bout en
> bout sans SMTP, les notifications in-app restant le canal principal. Pour l'activer,
> renseignez `MAIL_ENABLED=true` et un mot de passe d'application Gmail.

---

## 8. Sécurité

- Mots de passe hachés en BCrypt ; jamais renvoyés par l'API (`@JsonIgnore`).
- Politique : 12 caractères minimum, avec lettre, chiffre et symbole.
- Réponses identiques pour « email inconnu » et « mot de passe erroné », et pour la
  demande de réinitialisation : l'API ne permet pas d'énumérer les comptes.
- L'inscription libre n'accorde jamais le rôle `ADMIN` ; il est attribué par un
  administrateur.
- Les chemins de fichiers sont normalisés et vérifiés : impossible de sortir du répertoire
  de stockage.
- CORS restreint à des origines explicites, pas `*`.

---

## 9. Vérification

Le moteur de workflow a été validé de bout en bout contre l'API en fonctionnement
(33 contrôles, tous passants) :

- rejet des appels sans jeton, avec jeton invalide, et des rôles insuffisants ;
- génération du pipeline à 10 jalons et refus des références dupliquées ;
- respect de l'ordre des jalons ;
- refus de soumission tant que les livrables manquent, puis tant qu'ils ne sont pas
  approuvés ;
- refus de signature par un rôle non requis, refus de double signature, clôture après
  signature complète et ouverture automatique du jalon suivant ;
- gel puis dégel du produit par un blocage bloquant ;
- versionnage A → B avec passage de A en obsolète ;
- refus d'un créneau de réservation chevauchant ;
- 14 événements de traçabilité produits par le scénario.
