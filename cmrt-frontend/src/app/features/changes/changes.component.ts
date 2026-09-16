import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { label } from '../../core/labels';
import { ChangeStatus, ChangeType, EngineeringChange, Priority, ProductView } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { ChangeService } from '../../core/services/change.service';
import { ProductService } from '../../core/services/product.service';
import { ToastService } from '../../core/services/toast.service';
import { BadgeClassPipe, DateFrPipe, LabelPipe } from '../../shared/pipes';
import { EmptyComponent, LoaderComponent, ModalComponent, StatComponent } from '../../shared/ui';

/**
 * Engineering change control. A request is drafted, its impact assessed, then submitted:
 * the approval board is derived from that impact, so a change touching tooling or test
 * means automatically pulls in the right people.
 */
@Component({
  selector: 'app-changes',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ModalComponent,
    StatComponent, EmptyComponent, LoaderComponent,
    LabelPipe, BadgeClassPipe, DateFrPipe
  ],
  template: `
    <div class="page-head">
      <div>
        <h1>⇄ Gestion des modifications</h1>
        <p>Demandes de modification technique (ECR / ECO) et leur circuit d'approbation.</p>
      </div>
      <div class="page-head-actions">
        <button class="btn btn-ghost" (click)="load()">↻ Actualiser</button>
        <button class="btn btn-primary" *ngIf="canCreate" (click)="openCreate()">+ Nouvelle demande</button>
      </div>
    </div>

    <div class="grid grid-4 mb-3">
      <app-stat label="Total" [value]="changes().length"></app-stat>
      <app-stat label="En revue" [value]="countByStatus('UNDER_REVIEW')" tone="warn"></app-stat>
      <app-stat label="Approuvées" [value]="countByStatus('APPROVED')" tone="ok"></app-stat>
      <app-stat label="Délai moyen de décision" [value]="averageCycle()" unit="j" tone="info"></app-stat>
    </div>

    <div class="filters">
      <select class="select" [ngModel]="statusFilter()" (ngModelChange)="statusFilter.set($event)">
        <option value="">Tous les statuts</option>
        <option *ngFor="let s of statuses" [value]="s">{{ s | label }}</option>
      </select>
      <select class="select" [ngModel]="productFilter()" (ngModelChange)="productFilter.set($event)">
        <option value="">Tous les produits</option>
        <option *ngFor="let p of products()" [value]="p.id">{{ p.reference }}</option>
      </select>
      <span class="muted small" style="margin-left:auto">{{ filtered().length }} demande(s)</span>
    </div>

    <app-loader *ngIf="loading()"></app-loader>

    <div class="card" *ngIf="!loading()">
      <div class="card-body flush table-wrap">
        <table class="table" *ngIf="filtered().length; else none">
          <thead>
            <tr>
              <th>Référence</th><th>Objet</th><th>Produit</th><th>Type</th>
              <th>Impact</th><th>Priorité</th><th>Statut</th><th>Créée</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let c of filtered()" class="row-link" (click)="open(c)">
              <td class="bold mono">{{ c.reference }}</td>
              <td class="truncate" style="max-width:230px">{{ c.title }}</td>
              <td class="small">{{ referenceOf(c.productId) }}</td>
              <td class="small">{{ c.type | label }}</td>
              <td>
                <div class="flex gap-1 wrap">
                  <span class="badge warning" *ngIf="c.impactsTooling">Outillage</span>
                  <span class="badge info" *ngIf="c.impactsTestMeans">Moyens de test</span>
                  <span class="badge purple" *ngIf="c.requiresCustomerApproval">Client</span>
                </div>
              </td>
              <td><span class="badge" [ngClass]="c.priority | badgeClass">{{ c.priority | label }}</span></td>
              <td><span class="badge" [ngClass]="c.status | badgeClass">{{ c.status | label }}</span></td>
              <td class="small">{{ c.createdAt | dateFr }}</td>
            </tr>
          </tbody>
        </table>
        <ng-template #none><app-empty icon="⇄" message="Aucune demande de modification."></app-empty></ng-template>
      </div>
    </div>

    <!-- Detail -->
    <app-modal *ngIf="selected() as c" [title]="c.reference + ' — ' + c.title" [wide]="true"
               (closed)="selected.set(null)">
      <div class="flex gap-1 wrap mb-3">
        <span class="badge" [ngClass]="c.status | badgeClass">{{ c.status | label }}</span>
        <span class="badge">{{ c.type | label }}</span>
        <span class="badge" [ngClass]="c.priority | badgeClass">{{ c.priority | label }}</span>
        <span class="badge info">{{ referenceOf(c.productId) }}</span>
      </div>

      <div class="field">
        <label>Description</label>
        <p class="small secondary-text">{{ c.description || '—' }}</p>
      </div>
      <div class="field">
        <label>Motif</label>
        <p class="small secondary-text">{{ c.reason || '—' }}</p>
      </div>
      <div class="field">
        <label>Analyse d'impact</label>
        <p class="small secondary-text">{{ c.impactDescription || 'Non renseignée.' }}</p>
      </div>

      <div class="form-row mb-3">
        <div>
          <div class="stat-label">Coût estimé</div>
          <div class="small bold">{{ c.estimatedCost != null ? (c.estimatedCost + ' TND') : '—' }}</div>
        </div>
        <div>
          <div class="stat-label">Délai estimé</div>
          <div class="small bold">{{ c.estimatedLeadTimeDays != null ? (c.estimatedLeadTimeDays + ' j') : '—' }}</div>
        </div>
        <div>
          <div class="stat-label">Date d'effet</div>
          <div class="small bold">{{ c.effectiveDate | dateFr }}</div>
        </div>
        <div>
          <div class="stat-label">Délai de décision</div>
          <div class="small bold">{{ c.cycleTimeDays != null ? (c.cycleTimeDays + ' j') : 'en cours' }}</div>
        </div>
      </div>

      <div class="field" *ngIf="c.approvals.length">
        <label>Circuit d'approbation</label>
        <table class="table" style="font-size:.8rem">
          <tbody>
            <tr *ngFor="let a of c.approvals">
              <td>{{ a.requiredRole | label }}</td>
              <td><span class="badge" [ngClass]="a.decision | badgeClass">{{ a.decision | label }}</span></td>
              <td class="small">{{ a.approverName || '—' }}</td>
              <td class="tiny muted">{{ a.comment }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="field" *ngIf="needsDecision(c)">
        <label>Commentaire de décision</label>
        <textarea class="textarea" [ngModel]="decisionComment()" (ngModelChange)="decisionComment.set($event)"
                  placeholder="Obligatoire en cas de refus."></textarea>
      </div>

      <ng-container modal-actions>
        <button class="btn btn-ghost" (click)="selected.set(null)">Fermer</button>
        <button class="btn btn-accent" *ngIf="c.status === 'DRAFT'" (click)="submit(c)">⇪ Soumettre à revue</button>
        <button class="btn btn-danger" *ngIf="needsDecision(c)" (click)="reject(c)">✘ Refuser</button>
        <button class="btn btn-success" *ngIf="needsDecision(c)" (click)="approve(c)">✔ Approuver</button>
        <button class="btn btn-primary" *ngIf="c.status === 'APPROVED'" (click)="implement(c)">Déclarer mise en œuvre</button>
        <button class="btn btn-ghost" *ngIf="c.status === 'IMPLEMENTED'" (click)="close(c)">Clôturer</button>
      </ng-container>
    </app-modal>

    <!-- Create -->
    <app-modal *ngIf="formOpen()" title="Nouvelle demande de modification" [wide]="true"
               (closed)="formOpen.set(false)">
      <div class="form-row">
        <div class="field">
          <label>Produit <span class="req">*</span></label>
          <select class="select" [ngModel]="draft.productId" (ngModelChange)="draft.productId = $event">
            <option [ngValue]="undefined">— Choisir —</option>
            <option *ngFor="let p of products()" [ngValue]="p.id">{{ p.reference }} — {{ p.nom }}</option>
          </select>
        </div>
        <div class="field">
          <label>Type <span class="req">*</span></label>
          <select class="select" [ngModel]="draft.type" (ngModelChange)="draft.type = $event">
            <option *ngFor="let t of types" [value]="t">{{ lbl(t) }}</option>
          </select>
        </div>
        <div class="field">
          <label>Priorité</label>
          <select class="select" [ngModel]="draft.priority" (ngModelChange)="draft.priority = $event">
            <option *ngFor="let p of priorities" [value]="p">{{ lbl(p) }}</option>
          </select>
        </div>
      </div>

      <div class="field">
        <label>Objet <span class="req">*</span></label>
        <input class="input" [ngModel]="draft.title" (ngModelChange)="draft.title = $event"
               placeholder="Ex. Remplacement du connecteur DT06-12S par une référence équivalente">
      </div>

      <div class="field">
        <label>Description</label>
        <textarea class="textarea" [ngModel]="draft.description" (ngModelChange)="draft.description = $event"></textarea>
      </div>

      <div class="field">
        <label>Motif</label>
        <textarea class="textarea" [ngModel]="draft.reason" (ngModelChange)="draft.reason = $event"
                  placeholder="Obsolescence, demande client, amélioration process…"></textarea>
      </div>

      <div class="field">
        <label>Analyse d'impact <span class="req">*</span></label>
        <textarea class="textarea" [ngModel]="draft.impactDescription"
                  (ngModelChange)="draft.impactDescription = $event"
                  placeholder="Conséquences sur la conception, la production, la qualité et les moyens."></textarea>
        <div class="field-hint">Obligatoire avant de pouvoir soumettre la demande à revue.</div>
      </div>

      <div class="form-row">
        <div class="field">
          <label>Coût estimé (TND)</label>
          <input type="number" class="input" [ngModel]="draft.estimatedCost"
                 (ngModelChange)="draft.estimatedCost = $event">
        </div>
        <div class="field">
          <label>Délai estimé (jours)</label>
          <input type="number" class="input" [ngModel]="draft.estimatedLeadTimeDays"
                 (ngModelChange)="draft.estimatedLeadTimeDays = $event">
        </div>
        <div class="field">
          <label>Date d'effet</label>
          <input type="date" class="input" [ngModel]="draft.effectiveDate"
                 (ngModelChange)="draft.effectiveDate = $event">
        </div>
      </div>

      <div class="flex gap-3 wrap">
        <label class="checkbox">
          <input type="checkbox" [ngModel]="draft.impactsTooling" (ngModelChange)="draft.impactsTooling = $event">
          <span>Impacte l'outillage</span>
        </label>
        <label class="checkbox">
          <input type="checkbox" [ngModel]="draft.impactsTestMeans" (ngModelChange)="draft.impactsTestMeans = $event">
          <span>Impacte les moyens de test</span>
        </label>
        <label class="checkbox">
          <input type="checkbox" [ngModel]="draft.requiresCustomerApproval"
                 (ngModelChange)="draft.requiresCustomerApproval = $event">
          <span>Nécessite l'accord du client</span>
        </label>
      </div>
      <div class="field-hint mt-1">
        Ces impacts déterminent qui devra approuver la demande.
      </div>

      <ng-container modal-actions>
        <button class="btn btn-ghost" (click)="formOpen.set(false)">Annuler</button>
        <button class="btn btn-primary" [disabled]="saving()" (click)="create()">
          {{ saving() ? 'Création…' : 'Créer le brouillon' }}
        </button>
      </ng-container>
    </app-modal>
  `
})
export class ChangesComponent implements OnInit {
  private changesApi = inject(ChangeService);
  private productsApi = inject(ProductService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);

  protected loading = signal(true);
  protected saving = signal(false);
  protected changes = signal<EngineeringChange[]>([]);
  protected products = signal<ProductView[]>([]);
  protected selected = signal<EngineeringChange | null>(null);
  protected formOpen = signal(false);
  protected decisionComment = signal('');
  protected draft: Partial<EngineeringChange> = {};

  protected statusFilter = signal('');
  protected productFilter = signal('');

  protected lbl = label;
  protected readonly types: ChangeType[] = ['DESIGN', 'PROCESS', 'MATERIAL', 'DOCUMENTATION', 'CUSTOMER_REQUEST', 'TOOLING'];
  protected readonly priorities: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  protected readonly statuses: ChangeStatus[] = ['DRAFT', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'IMPLEMENTED', 'CLOSED'];

  protected readonly canCreate = this.auth.hasRole(
    'CHEF_PROJET', 'METHODISTE', 'QUALITICIEN', 'CONTROLE_TECHNIQUE'
  );

  protected filtered = computed(() => {
    const status = this.statusFilter();
    const productId = this.productFilter();
    return this.changes().filter(c => {
      if (status && c.status !== status) return false;
      if (productId && c.productId !== productId) return false;
      return true;
    });
  });

  ngOnInit(): void {
    this.load();
    this.productsApi.list().subscribe({ next: list => this.products.set(list) });
  }

  protected load() {
    this.loading.set(true);
    this.changesApi.list().subscribe({
      next: list => {
        this.changes.set(list);
        this.loading.set(false);
        const id = this.route.snapshot.paramMap.get('id');
        if (id) {
          const match = list.find(c => c.id === id);
          if (match) this.open(match);
        }
      },
      error: () => this.loading.set(false)
    });
  }

  protected open(change: EngineeringChange) {
    this.selected.set(change);
    this.decisionComment.set('');
  }

  protected openCreate() {
    this.draft = {
      type: 'DESIGN', priority: 'MEDIUM',
      impactsTooling: false, impactsTestMeans: false, requiresCustomerApproval: false
    };
    this.formOpen.set(true);
  }

  protected referenceOf(productId: string): string {
    return this.products().find(p => p.id === productId)?.reference ?? '—';
  }

  protected countByStatus(status: ChangeStatus): number {
    return this.changes().filter(c => c.status === status).length;
  }

  protected averageCycle(): number {
    const decided = this.changes().map(c => c.cycleTimeDays).filter((d): d is number => d != null);
    if (!decided.length) return 0;
    return Math.round((decided.reduce((a, b) => a + b, 0) / decided.length) * 10) / 10;
  }

  /** True when this user still owes a sign-off on a change under review. */
  protected needsDecision(change: EngineeringChange): boolean {
    if (change.status !== 'UNDER_REVIEW' && change.status !== 'SUBMITTED') return false;
    if (this.auth.hasRole('ADMIN')) return true;
    const role = this.auth.currentUser()?.role;
    return change.approvals.some(a => a.requiredRole === role && a.decision === 'PENDING');
  }

  protected create() {
    if (!this.draft.title?.trim() || !this.draft.productId) {
      this.toast.warning('Champs manquants', 'Le produit et l\'objet sont obligatoires.');
      return;
    }
    this.saving.set(true);
    this.changesApi.create(this.draft).subscribe({
      next: () => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.toast.success('Demande créée', 'Elle est au statut brouillon : soumettez-la pour lancer la revue.');
        this.load();
      },
      error: err => {
        this.saving.set(false);
        this.toast.error('Création impossible', err.error?.message);
      }
    });
  }

  protected submit(change: EngineeringChange) {
    this.act(this.changesApi.submit(change.id), 'Demande soumise à revue');
  }

  protected approve(change: EngineeringChange) {
    this.act(this.changesApi.approve(change.id, this.decisionComment()), 'Approbation enregistrée');
  }

  protected reject(change: EngineeringChange) {
    const comment = this.decisionComment().trim();
    if (!comment) {
      this.toast.warning('Motif obligatoire', 'Expliquez le refus avant de valider.');
      return;
    }
    this.act(this.changesApi.reject(change.id, comment), 'Demande refusée');
  }

  protected implement(change: EngineeringChange) {
    this.act(this.changesApi.implement(change.id), 'Modification déclarée mise en œuvre');
  }

  protected close(change: EngineeringChange) {
    this.act(this.changesApi.close(change.id), 'Demande clôturée');
  }

  private act(request$: import('rxjs').Observable<EngineeringChange>, message: string) {
    request$.subscribe({
      next: updated => {
        this.selected.set(updated);
        this.toast.success(message);
        this.load();
      },
      error: err => this.toast.error('Action refusée', err.error?.message)
    });
  }
}
