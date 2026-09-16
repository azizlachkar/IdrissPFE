import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { progressClass } from '../../core/labels';
import {
  AuditLog, EngineeringChange, Issue, ProductDetail, StageView,
  Task, TechnicalDocument
} from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { ChangeService } from '../../core/services/change.service';
import { DocumentService } from '../../core/services/document.service';
import { IssueService } from '../../core/services/issue.service';
import { ProductService } from '../../core/services/product.service';
import { TaskService } from '../../core/services/task.service';
import { ToastService } from '../../core/services/toast.service';
import { BadgeClassPipe, DateFrPipe, DaysLeftPipe, FileSizePipe, LabelPipe, TimeAgoPipe } from '../../shared/pipes';
import { EmptyComponent, LoaderComponent, ModalComponent, ProgressComponent } from '../../shared/ui';
import { IssueFormComponent } from '../issues/issue-form.component';
import { ProductFormComponent } from './product-form.component';

type Tab = 'pipeline' | 'tasks' | 'issues' | 'documents' | 'changes' | 'history';

/**
 * The product workspace: the pipeline with its gates, and everything attached to the
 * product - tasks, blockages, controlled documents, change requests and the audit trail.
 * Every workflow action is issued from here and the whole view reloads afterwards so the
 * gate states stay in step with the server.
 */
@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink,
    ProductFormComponent, IssueFormComponent,
    EmptyComponent, LoaderComponent, ProgressComponent, ModalComponent,
    LabelPipe, BadgeClassPipe, DateFrPipe, DaysLeftPipe, TimeAgoPipe, FileSizePipe
  ],
  templateUrl: './product-detail.component.html'
})
export class ProductDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private products = inject(ProductService);
  private tasksApi = inject(TaskService);
  private issuesApi = inject(IssueService);
  private documentsApi = inject(DocumentService);
  private changesApi = inject(ChangeService);
  private toast = inject(ToastService);
  private auth = inject(AuthService);

  protected productId = '';
  protected loading = signal(true);
  protected detail = signal<ProductDetail | null>(null);
  protected tab = signal<Tab>('pipeline');

  protected tasks = signal<Task[]>([]);
  protected issues = signal<Issue[]>([]);
  protected documents = signal<TechnicalDocument[]>([]);
  protected changes = signal<EngineeringChange[]>([]);
  protected history = signal<AuditLog[]>([]);

  protected selectedStage = signal<StageView | null>(null);
  protected editOpen = signal(false);
  protected issueOpen = signal(false);
  protected decisionModal = signal<'reject' | 'skip' | null>(null);
  protected decisionComment = signal('');
  protected uploadOpen = signal(false);
  protected uploadFile = signal<File | null>(null);
  protected uploadType = signal('DRAWING');
  protected uploadNote = signal('');
  protected busy = signal(false);

  protected readonly canManage = this.auth.hasRole('CHEF_PROJET', 'METHODISTE');
  protected readonly canApprove = this.auth.hasRole(
    'CHEF_PROJET', 'METHODISTE', 'QUALITICIEN', 'RESPONSABLE_PRODUCTION', 'CONTROLE_TECHNIQUE'
  );

  protected product = computed(() => this.detail()?.product ?? null);

  /**
   * The gate the user is looking at; defaults to the one currently in play, and falls
   * back to the last gate once the pipeline is finished. Explicitly typed nullable
   * because the pipeline is empty while the detail is still loading.
   */
  protected activeStage = computed<StageView | null>(() => {
    const stages = this.detail()?.stages ?? [];
    if (!stages.length) return null;

    const chosen = this.selectedStage();
    if (chosen) {
      return stages.find(s => s.stageType === chosen.stageType) ?? chosen;
    }
    return stages.find(s => s.status !== 'COMPLETED' && s.status !== 'SKIPPED')
      ?? stages[stages.length - 1];
  });

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      this.productId = params.get('id') ?? '';
      this.loadAll();
    });
  }

  protected loadAll() {
    this.loading.set(true);
    this.products.detail(this.productId).subscribe({
      next: detail => {
        this.detail.set(detail);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.toast.error('Produit introuvable');
        this.router.navigate(['/dashboard']);
      }
    });

    this.tasksApi.list({ productId: this.productId }).subscribe({ next: list => this.tasks.set(list) });
    this.issuesApi.list({ productId: this.productId }).subscribe({ next: list => this.issues.set(list) });
    this.documentsApi.list(this.productId).subscribe({ next: list => this.documents.set(list) });
    this.changesApi.list(this.productId).subscribe({ next: list => this.changes.set(list) });
    this.products.history(this.productId).subscribe({ next: list => this.history.set(list) });
  }

  // ------------------------------------------------------------ pipeline

  protected stageClass(stage: StageView): string {
    if (stage.status === 'COMPLETED' || stage.status === 'SKIPPED') return 'done';
    if (stage.blocked || stage.status === 'BLOCKED') return 'blocked';
    if (stage.status === 'PENDING_APPROVAL') return 'review';
    if (stage.status === 'IN_PROGRESS') return 'active';
    return '';
  }

  protected select(stage: StageView) {
    this.selectedStage.set(stage);
  }

  protected tone(percent: number, risky: boolean): string {
    return progressClass(percent, risky);
  }

  /** True when the signed-in user still owes a sign-off on this gate. */
  protected canSignOff(stage: StageView): boolean {
    if (stage.status !== 'PENDING_APPROVAL') return false;
    if (this.auth.hasRole('ADMIN')) return true;
    const role = this.auth.currentUser()?.role;
    return stage.approvals.some(a => a.requiredRole === role && a.decision === 'PENDING');
  }

  protected startStage(stage: StageView) {
    this.run(this.products.startStage(this.productId, stage.stageType),
      `Jalon « ${stage.label} » démarré`);
  }

  protected submitStage(stage: StageView) {
    this.run(this.products.submitStage(this.productId, stage.stageType),
      `Jalon « ${stage.label} » soumis à validation`);
  }

  protected approveStage(stage: StageView) {
    this.run(this.products.approveStage(this.productId, stage.stageType),
      `Approbation enregistrée pour « ${stage.label} »`);
  }

  protected openDecision(kind: 'reject' | 'skip') {
    this.decisionComment.set('');
    this.decisionModal.set(kind);
  }

  protected confirmDecision() {
    const stage = this.activeStage();
    const kind = this.decisionModal();
    const comment = this.decisionComment().trim();
    if (!stage || !kind) return;
    if (!comment) {
      this.toast.warning('Motif obligatoire', 'Indiquez la raison de votre décision.');
      return;
    }

    const request$ = kind === 'reject'
      ? this.products.rejectStage(this.productId, stage.stageType, comment)
      : this.products.skipStage(this.productId, stage.stageType, comment);

    this.decisionModal.set(null);
    this.run(request$, kind === 'reject'
      ? `Jalon « ${stage.label} » renvoyé à son responsable`
      : `Jalon « ${stage.label} » marqué comme non applicable`);
  }

  // ----------------------------------------------------------- documents

  protected onFileChosen(event: Event) {
    const input = event.target as HTMLInputElement;
    this.uploadFile.set(input.files?.[0] ?? null);
  }

  protected submitUpload() {
    const file = this.uploadFile();
    if (!file) {
      this.toast.warning('Aucun fichier', 'Choisissez un fichier à déposer.');
      return;
    }
    this.busy.set(true);

    this.documentsApi.upload({
      file,
      productId: this.productId,
      type: this.uploadType() as never,
      stageType: this.activeStage()?.stageType,
      changeNote: this.uploadNote() || undefined
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.uploadOpen.set(false);
        this.uploadFile.set(null);
        this.uploadNote.set('');
        this.toast.success('Document déposé', 'La révision attend son approbation.');
        this.loadAll();
      },
      error: err => {
        this.busy.set(false);
        this.toast.error('Dépôt impossible', err.error?.message ?? 'Veuillez réessayer.');
      }
    });
  }

  protected approveDocument(doc: TechnicalDocument, version: string) {
    this.run(this.documentsApi.approve(doc.id, version),
      `Révision ${version} de « ${doc.name} » approuvée`);
  }

  protected download(doc: TechnicalDocument, version?: string) {
    this.documentsApi.download(doc.id, version).subscribe({
      next: blob => {
        // Anchor + object URL: the request needs the bearer token, so a plain href won't do.
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = doc.versions.find(v => v.version === version)?.fileName ?? doc.name;
        anchor.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.toast.error('Téléchargement impossible')
    });
  }

  // --------------------------------------------------------------- misc

  protected resolveIssue(issue: Issue) {
    this.run(this.issuesApi.changeStatus(issue.id, 'RESOLVED', 'Résolu depuis la fiche produit'),
      `Blocage ${issue.reference} résolu`);
  }

  protected openTask(task: Task) {
    this.router.navigate(['/taches'], { queryParams: { productId: this.productId, taskId: task.id } });
  }

  protected docTypes = [
    'DRAWING', 'BOM', 'WORK_INSTRUCTION', 'CONTROL_PLAN', 'TEST_PROCEDURE',
    'FAI_REPORT', 'PPAP', 'CUSTOMER_SPEC', 'TOOLING_DRAWING', 'ROUTING',
    'LAYOUT', 'QUALITY_REPORT', 'OTHER'
  ];

  /**
   * Runs a workflow call, reports the outcome and refreshes the whole workspace.
   * Reloading everything is deliberate: approving one gate can open the next, unblock
   * a product and change its progress, so a targeted update would drift.
   */
  private run(request$: Observable<unknown>, successMessage: string) {
    this.busy.set(true);
    request$.subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.success(successMessage);
        this.loadAll();
      },
      error: err => {
        this.busy.set(false);
        this.toast.error('Action refusée', err.error?.message ?? 'Veuillez réessayer.');
      }
    });
  }
}
