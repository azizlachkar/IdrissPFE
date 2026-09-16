/**
 * French display labels and colour classes for the API's enum values.
 * The backend stays language-neutral; all wording for the user lives here.
 */

const LABELS: Record<string, string> = {
  // Roles
  ADMIN: 'Administrateur',
  CHEF_PROJET: 'Chef de projet',
  METHODISTE: 'Méthodiste',
  QUALITICIEN: 'Qualiticien',
  TECHNICIEN: 'Technicien',
  RESPONSABLE_PRODUCTION: 'Responsable production',
  CONTROLE_TECHNIQUE: 'Contrôle technique',
  VIEWER: 'Consultation',

  // Départements & services
  ENGINEERING: 'Engineering',
  QHSE: 'QHSE',
  SUPPLY_CHAIN: 'Supply chain',
  MAINTENANCE: 'Maintenance',
  METHODE: 'Méthode',
  NPI: 'NPI',
  QUALITE: 'Qualité',

  // Postes
  DIRECTEUR: 'Directeur',
  RESPONSABLE: 'Responsable',
  INGENIEUR: 'Ingénieur',
  SUPERVISEUR: 'Superviseur',
  MONITRICE: 'Monitrice',
  OPERATEUR: 'Opérateur',

  // Familles produit
  FAISCEAU: 'Faisceau',
  PIPE: 'Pipe',
  CABLE: 'Câble',
  SOUS_ENSEMBLE: 'Sous-ensemble',

  // Clients
  CUMMINS: 'Cummins',
  WAUKESHA: 'Waukesha',
  WABTEC: 'Wabtec',
  AUTRE: 'Autre',

  // Statuts produit
  DRAFT: 'Brouillon',
  IN_DEVELOPMENT: 'En développement',
  PILOT: 'Pilote',
  MASS_PRODUCTION: 'Série',
  ON_HOLD: 'En attente',
  CANCELLED: 'Annulé',
  OBSOLETE: 'Obsolète',

  // Jalons
  ENGINEERING_REVIEW: 'Revue technique',
  BOM_VALIDATION: 'Validation nomenclature',
  MATERIAL_AVAILABILITY: 'Disponibilité matière',
  TOOLING_PREPARATION: 'Préparation outillage',
  TEST_BOARD_DESIGN: 'Conception banc de test',
  PROTOTYPE_MANUFACTURING: 'Fabrication prototype',
  QUALITY_VALIDATION: 'Validation qualité',
  CUSTOMER_APPROVAL: 'Approbation client',
  PILOT_PRODUCTION: 'Production pilote',
  MASS_PRODUCTION_RELEASE: 'Lancement en série',

  // Statuts jalon
  NOT_STARTED: 'Non démarré',
  IN_PROGRESS: 'En cours',
  BLOCKED: 'Bloqué',
  PENDING_APPROVAL: 'En validation',
  COMPLETED: 'Clôturé',
  REJECTED: 'Refusé',
  SKIPPED: 'Ignoré',

  // Décisions
  PENDING: 'En attente',
  APPROVED: 'Approuvé',

  // Priorités & gravités
  LOW: 'Basse',
  MEDIUM: 'Moyenne',
  HIGH: 'Haute',
  MINOR: 'Mineure',
  MAJOR: 'Majeure',
  CRITICAL: 'Critique',
  BLOCKING: 'Bloquante',

  // Tâches
  TODO: 'À faire',
  IN_REVIEW: 'En revue',
  DONE: 'Terminé',

  // Blocages
  OPEN: 'Ouvert',
  ACKNOWLEDGED: 'Pris en charge',
  RESOLVED: 'Résolu',
  CLOSED: 'Clôturé',
  TECHNIQUE: 'Technique',
  MATIERE: 'Matière',
  OUTILLAGE: 'Outillage',
  DOCUMENTATION: 'Documentation',
  MOYEN_DE_TEST: 'Moyen de test',
  FOURNISSEUR: 'Fournisseur',
  PROCESS: 'Process',
  SECURITE: 'Sécurité',

  // Modifications
  DESIGN: 'Conception',
  MATERIAL: 'Matière',
  CUSTOMER_REQUEST: 'Demande client',
  TOOLING: 'Outillage',
  SUBMITTED: 'Soumise',
  UNDER_REVIEW: 'En revue',
  IMPLEMENTED: 'Mise en œuvre',

  // Documents
  DRAWING: 'Plan',
  BOM: 'Nomenclature (BOM)',
  WORK_INSTRUCTION: 'Instruction de travail',
  CONTROL_PLAN: 'Plan de contrôle',
  TEST_PROCEDURE: 'Procédure de test',
  FAI_REPORT: 'Rapport FAI',
  PPAP: 'Dossier PPAP',
  CUSTOMER_SPEC: 'Spécification client',
  TOOLING_DRAWING: 'Plan outillage',
  ROUTING: 'Gamme de fabrication',
  LAYOUT: 'Implantation',
  QUALITY_REPORT: 'Rapport qualité',
  OTHER: 'Autre',

  // Moyens de test
  TEST_BOARD: 'Banc de test',
  BUILD_BOARD: 'Build board',
  TEST_INTERFACE: 'Interface de test',
  FIXTURE: 'Gabarit',
  AVAILABLE: 'Disponible',
  RESERVED: 'Réservé',
  IN_USE: 'En cours d’utilisation',
  OUT_OF_SERVICE: 'Hors service',

  // Types de projet
  PRODUCTION: 'Production',

  // Actions d'audit
  CREATE: 'Création',
  UPDATE: 'Modification',
  DELETE: 'Suppression',
  APPROVE: 'Approbation',
  REJECT: 'Refus',
  LOGIN: 'Connexion',
  UPLOAD: 'Dépôt',
  STATUS_CHANGE: 'Changement de statut',
  ASSIGN: 'Affectation'
};

/** Human label for an API enum value; falls back to a readable form of the code. */
export function label(value: string | null | undefined): string {
  if (!value) return '—';
  return LABELS[value] ?? value.replace(/_/g, ' ').toLowerCase();
}

/** Badge colour class for a status-like value. */
export function badgeClass(value: string | null | undefined): string {
  switch (value) {
    case 'COMPLETED':
    case 'APPROVED':
    case 'DONE':
    case 'RESOLVED':
    case 'AVAILABLE':
    case 'MASS_PRODUCTION':
    case 'IMPLEMENTED':
      return 'success';

    case 'BLOCKED':
    case 'REJECTED':
    case 'CRITICAL':
    case 'BLOCKING':
    case 'OUT_OF_SERVICE':
    case 'CANCELLED':
    case 'OPEN':
      return 'danger';

    case 'PENDING_APPROVAL':
    case 'PENDING':
    case 'IN_REVIEW':
    case 'UNDER_REVIEW':
    case 'MAINTENANCE':
    case 'ON_HOLD':
    case 'HIGH':
    case 'MAJOR':
    case 'ACKNOWLEDGED':
      return 'warning';

    case 'IN_PROGRESS':
    case 'IN_DEVELOPMENT':
    case 'IN_USE':
    case 'SUBMITTED':
    case 'RESERVED':
      return 'info';

    case 'NPI':
    case 'PILOT':
      return 'purple';

    default:
      return '';
  }
}

/** Colour of the left rail on a progress bar, driven by how far along and how late. */
export function progressClass(percent: number, atRisk = false): string {
  if (atRisk) return 'bad';
  if (percent >= 80) return 'ok';
  if (percent >= 40) return '';
  return 'warn';
}

export const CHART_COLORS = [
  '#2B6CB0', '#00B5D8', '#7C3AED', '#059669',
  '#D97706', '#DC2626', '#0F766E', '#BE185D'
];
