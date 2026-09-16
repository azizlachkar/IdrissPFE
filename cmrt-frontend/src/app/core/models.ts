/**
 * Types mirroring the backend API. Enums are string unions so they compare
 * directly against the JSON the API returns, with no runtime mapping layer.
 */

export type Role =
  | 'ADMIN' | 'CHEF_PROJET' | 'METHODISTE' | 'QUALITICIEN'
  | 'TECHNICIEN' | 'RESPONSABLE_PRODUCTION' | 'CONTROLE_TECHNIQUE' | 'VIEWER';

export type Departement = 'ENGINEERING' | 'PRODUCTION' | 'QHSE' | 'SUPPLY_CHAIN' | 'MAINTENANCE';

export type Poste =
  | 'DIRECTEUR' | 'RESPONSABLE' | 'CHEF_PROJET' | 'INGENIEUR'
  | 'TECHNICIEN' | 'SUPERVISEUR' | 'MONITRICE' | 'OPERATEUR';

export type ServiceUnit =
  | 'PRODUCTION' | 'METHODE' | 'NPI' | 'CONTROLE_TECHNIQUE'
  | 'QUALITE' | 'SUPPLY_CHAIN' | 'MAINTENANCE';

export type ProductFamily = 'FAISCEAU' | 'PIPE' | 'CABLE' | 'SOUS_ENSEMBLE';
export type Customer = 'CUMMINS' | 'WAUKESHA' | 'WABTEC' | 'AUTRE';
export type ProjectType = 'PRODUCTION' | 'NPI';

export type ProductStatus =
  | 'DRAFT' | 'IN_DEVELOPMENT' | 'PILOT' | 'MASS_PRODUCTION'
  | 'ON_HOLD' | 'CANCELLED' | 'OBSOLETE';

export type StageType =
  | 'ENGINEERING_REVIEW' | 'BOM_VALIDATION' | 'MATERIAL_AVAILABILITY'
  | 'TOOLING_PREPARATION' | 'TEST_BOARD_DESIGN' | 'PROTOTYPE_MANUFACTURING'
  | 'QUALITY_VALIDATION' | 'CUSTOMER_APPROVAL' | 'PILOT_PRODUCTION'
  | 'MASS_PRODUCTION_RELEASE';

export type StageStatus =
  | 'NOT_STARTED' | 'IN_PROGRESS' | 'BLOCKED' | 'PENDING_APPROVAL'
  | 'COMPLETED' | 'REJECTED' | 'SKIPPED';

export type ApprovalDecision = 'PENDING' | 'APPROVED' | 'REJECTED';
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type Severity = 'MINOR' | 'MAJOR' | 'CRITICAL' | 'BLOCKING';
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE' | 'CANCELLED';

export type IssueStatus = 'OPEN' | 'ACKNOWLEDGED' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED' | 'REJECTED';

export type IssueCategory =
  | 'TECHNIQUE' | 'MATIERE' | 'OUTILLAGE' | 'QUALITE' | 'DOCUMENTATION'
  | 'MOYEN_DE_TEST' | 'FOURNISSEUR' | 'PROCESS' | 'SECURITE' | 'AUTRE';

export type ChangeType = 'DESIGN' | 'PROCESS' | 'MATERIAL' | 'DOCUMENTATION' | 'CUSTOMER_REQUEST' | 'TOOLING';
export type ChangeStatus = 'DRAFT' | 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'IMPLEMENTED' | 'CLOSED';

export type DocumentType =
  | 'DRAWING' | 'BOM' | 'WORK_INSTRUCTION' | 'CONTROL_PLAN' | 'TEST_PROCEDURE'
  | 'FAI_REPORT' | 'PPAP' | 'CUSTOMER_SPEC' | 'TOOLING_DRAWING' | 'ROUTING'
  | 'LAYOUT' | 'QUALITY_REPORT' | 'OTHER';

export type DocumentStatus = 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'OBSOLETE';

export type ResourceType = 'TEST_BOARD' | 'BUILD_BOARD' | 'TEST_INTERFACE' | 'TOOLING' | 'FIXTURE';
export type ResourceStatus = 'AVAILABLE' | 'RESERVED' | 'IN_USE' | 'MAINTENANCE' | 'OUT_OF_SERVICE';
export type ReservationStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'IN_PROGRESS' | 'COMPLETED';

// ---------------------------------------------------------------- entities

export interface UserProfile {
  id: string;
  matricule?: string;
  nom: string;
  prenom: string;
  fullName: string;
  initials: string;
  email: string;
  telephone?: string;
  departement?: Departement;
  poste?: Poste;
  serviceUnit?: ServiceUnit;
  role: Role;
  enabled?: boolean;
  active?: boolean;
}

export interface AuthResponse {
  token: string;
  expiresInMs: number;
  user: UserProfile;
}

export interface ProductView {
  id: string;
  reference: string;
  nom: string;
  description?: string;
  family: ProductFamily;
  customer: Customer;
  projectType: ProjectType;
  status: ProductStatus;
  priority: Priority;
  methodisteId?: string;
  methodisteName: string;
  qualiticienId?: string;
  qualiticienName: string;
  chefProjetId?: string;
  chefProjetName: string;
  startDate?: string;
  targetSopDate?: string;
  actualSopDate?: string;
  currentStage?: StageType;
  currentStageLabel?: string;
  progressPercent: number;
  blocked: boolean;
  atRisk: boolean;
  openIssues: number;
  openTasks: number;
  totalStages: number;
  completedStages: number;
  annualVolume?: number;
  programme?: string;
  tags: string[];
}

export interface ApprovalView {
  requiredRole: Role;
  decision: ApprovalDecision;
  approverId?: string;
  approverName?: string;
  comment?: string;
  decidedAt?: string;
}

export interface StageView {
  id: string;
  stageType: StageType;
  label: string;
  order: number;
  status: StageStatus;
  ownerId?: string;
  ownerName: string;
  plannedStart?: string;
  plannedEnd?: string;
  actualStart?: string;
  actualEnd?: string;
  completionPercent: number;
  overdue: boolean;
  blocked: boolean;
  blockReason?: string;
  notes?: string;
  requiredDeliverables: DocumentType[];
  missingDeliverables: DocumentType[];
  approvals: ApprovalView[];
  openIssues: number;
}

export interface ProductDetail {
  product: ProductView;
  stages: StageView[];
  openIssues: number;
  openTasks: number;
  documents: number;
  openChanges: number;
}

export interface ProductRequest {
  reference: string;
  nom: string;
  description?: string;
  family: ProductFamily | null;
  customer: Customer | null;
  projectType: ProjectType | null;
  status?: ProductStatus;
  priority?: Priority;
  methodisteId?: string | null;
  qualiticienId?: string | null;
  chefProjetId?: string | null;
  startDate?: string | null;
  targetSopDate?: string | null;
  annualVolume?: number | null;
  programme?: string | null;
  tags?: string[];
}

export interface ChecklistItem { label: string; done: boolean; }

export interface Task {
  id: string;
  title: string;
  description?: string;
  productId?: string;
  stageType?: StageType;
  assigneeId?: string;
  reporterId?: string;
  status: TaskStatus;
  priority: Priority;
  dueDate?: string;
  estimatedHours?: number;
  spentHours?: number;
  checklist: ChecklistItem[];
  tags: string[];
  createdAt?: string;
  completedAt?: string;
  overdue?: boolean;
}

export interface IssueComment {
  authorId?: string;
  authorName?: string;
  message: string;
  createdAt?: string;
}

export interface Issue {
  id: string;
  reference: string;
  productId: string;
  stageType?: StageType;
  title: string;
  category?: IssueCategory;
  severity: Severity;
  status: IssueStatus;
  description?: string;
  reporterId?: string;
  assigneeId?: string;
  blocksStage: boolean;
  rootCause?: string;
  correctiveAction?: string;
  dueDate?: string;
  slaHours?: number;
  comments: IssueComment[];
  createdAt?: string;
  acknowledgedAt?: string;
  resolvedAt?: string;
  closedAt?: string;
  open?: boolean;
  slaBreached?: boolean;
  resolutionHours?: number;
}

export interface Approval {
  requiredRole: Role;
  decision: ApprovalDecision;
  approverId?: string;
  approverName?: string;
  comment?: string;
  decidedAt?: string;
  pending?: boolean;
}

export interface EngineeringChange {
  id: string;
  reference: string;
  productId: string;
  title: string;
  description?: string;
  reason?: string;
  type?: ChangeType;
  status: ChangeStatus;
  priority: Priority;
  requesterId?: string;
  impactDescription?: string;
  estimatedCost?: number;
  estimatedLeadTimeDays?: number;
  impactsTooling: boolean;
  impactsTestMeans: boolean;
  requiresCustomerApproval: boolean;
  approvals: Approval[];
  affectedDocumentIds: string[];
  effectiveDate?: string;
  createdAt?: string;
  submittedAt?: string;
  decidedAt?: string;
  implementedAt?: string;
  cycleTimeDays?: number;
}

export interface DocumentVersion {
  version: string;
  fileName: string;
  storedPath?: string;
  contentType?: string;
  sizeBytes?: number;
  uploadedBy?: string;
  uploadedByName?: string;
  uploadedAt?: string;
  changeNote?: string;
  status: DocumentStatus;
  approvedBy?: string;
  approvedAt?: string;
  engineeringChangeId?: string;
}

export interface TechnicalDocument {
  id: string;
  name: string;
  type: DocumentType;
  productId: string;
  stageType?: StageType;
  description?: string;
  currentVersion?: string;
  status: DocumentStatus;
  versions: DocumentVersion[];
  ownerId?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestResource {
  id: string;
  code: string;
  nom: string;
  type: ResourceType;
  status: ResourceStatus;
  location?: string;
  description?: string;
  ownerId?: string;
  compatibleProductIds: string[];
  lastMaintenanceDate?: string;
  nextMaintenanceDate?: string;
  requiresCalibration: boolean;
  calibrationExpiry?: string;
  calibrationExpired?: boolean;
  bookable?: boolean;
}

export interface Reservation {
  id: string;
  resourceId: string;
  requesterId?: string;
  productId?: string;
  purpose?: string;
  startDate: string;
  endDate: string;
  status: ReservationStatus;
  decidedBy?: string;
  decisionComment?: string;
  decidedAt?: string;
  createdAt?: string;
  durationHours?: number;
}

export interface AppNotification {
  id: string;
  recipientId: string;
  type: string;
  title: string;
  message: string;
  link?: string;
  severity: Severity;
  read: boolean;
  readAt?: string;
  actorId?: string;
  actorName?: string;
  createdAt: string;
}

export interface AuditLog {
  id: string;
  entityType: string;
  entityId: string;
  action: string;
  actorId?: string;
  actorName?: string;
  summary: string;
  previousValue?: string;
  newValue?: string;
  productId?: string;
  timestamp: string;
}

// --------------------------------------------------------------- dashboard

export interface Metric { key: string; label: string; value: number; }

export interface KpiSummary {
  totalProducts: number;
  npiProducts: number;
  productionProducts: number;
  productsInMassProduction: number;
  blockedProducts: number;
  productsAtRisk: number;
  averageProgress: number;
  openIssues: number;
  criticalIssues: number;
  slaBreaches: number;
  averageResolutionHours: number;
  openTasks: number;
  overdueTasks: number;
  overdueStages: number;
  pendingApprovals: number;
  pendingReservations: number;
  availableResources: number;
  totalResources: number;
  resourceUtilisation: number;
  openChanges: number;
  averageChangeCycleDays: number;
  onTimeStageRate: number;
}

export interface AlertItem {
  type: string;
  severity: string;
  title: string;
  detail: string;
  link: string;
  date?: string;
}

export interface ActivityItem {
  actorName?: string;
  action: string;
  summary: string;
  entityType: string;
  productId?: string;
  timestamp: string;
}

export interface DashboardResponse {
  kpis: KpiSummary;
  productsByStage: Metric[];
  productsByStatus: Metric[];
  productsByCustomer: Metric[];
  issuesBySeverity: Metric[];
  issuesByCategory: Metric[];
  tasksByStatus: Metric[];
  resourcesByStatus: Metric[];
  monthlyThroughput: Metric[];
  alerts: AlertItem[];
  recentActivity: ActivityItem[];
}

export interface WorkloadItem {
  userId: string;
  name: string;
  role: Role;
  openTasks: number;
  overdueTasks: number;
  openIssues: number;
  ownedStages: number;
  products: number;
}

export interface PipelineStageTemplate {
  name: StageType;
  label: string;
  order: number;
  defaultOwnerRole: Role;
  nominalDurationDays: number;
  requiredDeliverables: DocumentType[];
  approverRoles: Role[];
}
