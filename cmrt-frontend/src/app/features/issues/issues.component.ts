import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { label } from '../../core/labels';
import { Issue, IssueStatus, ProductView, Severity, UserProfile } from '../../core/models';
import { IssueService } from '../../core/services/issue.service';
import { ProductService } from '../../core/services/product.service';
import { ToastService } from '../../core/services/toast.service';
import { UserService } from '../../core/services/user.service';
import { BadgeClassPipe, DateFrPipe, LabelPipe, TimeAgoPipe } from '../../shared/pipes';
import { EmptyComponent, LoaderComponent, ModalComponent, StatComponent } from '../../shared/ui';

/**
 * Blockage tracker. The detail dialog carries the whole resolution record - root cause,
 * corrective action, discussion - because that is what makes a closed blockage reusable
 * knowledge rather than just a state change.
 */
@Component({
  selector: 'app-issues',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ModalComponent,
    StatComponent, EmptyComponent, LoaderComponent,
    LabelPipe, BadgeClassPipe, DateFrPipe, TimeAgoPipe
  ],
  template: `
    <div class="page-head">
      <div>
        <h1>⚠ Blocages</h1>
        <p>Toutes les réclamations remontées depuis la production et l'engineering.</p>
      </div>
      <div class="page-head-actions">
        <button class="btn btn-ghost" (click)="load()">↻ Actualiser</button>
      </div>
    </div>

    <div class="grid grid-4 mb-3">
      <app-stat label="Ouverts" [value]="openCount()" [tone]="openCount() ? 'bad' : 'ok'"></app-stat>
      <app-stat label="Bloquants" [value]="blockingCount()" [tone]="blockingCount() ? 'bad' : 'ok'"></app-stat>
      <app-stat label="SLA dépassés" [value]="slaCount()" [tone]="slaCount() ? 'warn' : 'ok'"></app-stat>
      <app-stat label="Résolus" [value]="resolvedCount()" tone="ok"></app-stat>
    </div>

    <div class="filters">
      <input class="input search" placeholder="🔍 Référence ou objet…"
             [ngModel]="search()" (ngModelChange)="search.set($event)">

      <select class="select" [ngModel]="statusFilter()" (ngModelChange)="statusFilter.set($event)">
        <option value="">Tous les statuts</option>
        <option *ngFor="let s of statuses" [value]="s">{{ s | label }}</option>
      </select>

      <select class="select" [ngModel]="severityFilter()" (ngModelChange)="severityFilter.set($event)">
        <option value="">Toutes les gravités</option>
        <option *ngFor="let s of severities" [value]="s">{{ s | label }}</option>
      </select>

      <label class="checkbox">
        <input type="checkbox" [ngModel]="onlyBlocking()" (ngModelChange)="onlyBlocking.set($event)">
        <span>Uniquement bloquants</span>
      </label>

      <span class="muted small" style="margin-left:auto">{{ filtered().length }} blocage(s)</span>
    </div>

    <app-loader *ngIf="loading()"></app-loader>

    <div class="card" *ngIf="!loading()">
      <div class="card-body flush table-wrap">
        <table class="table" *ngIf="filtered().length; else none">
          <thead>
            <tr>
              <th>Référence</th><th>Objet</th><th>Produit</th><th>Catégorie</th>
              <th>Gravité</th><th>Responsable</th><th>Déclaré</th><th>Statut</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let i of filtered()" class="row-link" (click)="open(i)">
              <td class="bold mono">{{ i.reference }}</td>
              <td>
                <div class="truncate" style="max-width:250px">{{ i.title }}</div>
                <div class="tiny" style="color:var(--danger)" *ngIf="i.blocksStage">⛔ Gèle le jalon</div>
              </td>
              <td class="small">{{ referenceOf(i.productId) }}</td>
              <td class="small">{{ i.category | label }}</td>
              <td><span class="badge" [ngClass]="i.severity | badgeClass">{{ i.severity | label }}</span></td>
              <td class="small">{{ nameOf(i.assigneeId) }}</td>
              <td>
                <div class="small">{{ i.createdAt | dateFr }}</div>
                <div class="tiny muted">{{ i.createdAt | timeAgo }}</div>
              </td>
              <td>
                <div class="flex gap-1 wrap">
                  <span class="badge" [ngClass]="i.status | badgeClass">{{ i.status | label }}</span>
                  <span class="badge solid-danger" *ngIf="i.slaBreached && i.open">SLA</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <ng-template #none>
          <app-empty icon="✅" message="Aucun blocage ne correspond aux filtres."></app-empty>
        </ng-template>
      </div>
    </div>

    <!-- Detail / resolution -->
    <app-modal *ngIf="selected() as issue" [title]="issue.reference + ' — ' + issue.title"
               [wide]="true" (closed)="selected.set(null)">
      <div class="flex gap-1 wrap mb-3">
        <span class="badge" [ngClass]="issue.status | badgeClass">{{ issue.status | label }}</span>
        <span class="badge" [ngClass]="issue.severity | badgeClass">{{ issue.severity | label }}</span>
        <span class="badge">{{ issue.category | label }}</span>
        <span class="badge info" *ngIf="issue.stageType">{{ issue.stageType | label }}</span>
        <span class="badge danger" *ngIf="issue.blocksStage">⛔ Gèle le jalon</span>
        <span class="badge solid-danger" *ngIf="issue.slaBreached && issue.open">SLA dépassé</span>
      </div>

      <div class="form-row mb-3">
        <div>
          <div class="stat-label">Produit</div>
          <div class="small bold">{{ referenceOf(issue.productId) }}</div>
        </div>
        <div>
          <div class="stat-label">Déclaré par</div>
          <div class="small bold">{{ nameOf(issue.reporterId) }}</div>
          <div class="tiny muted">{{ issue.createdAt | dateFr:true }}</div>
        </div>
        <div>
          <div class="stat-label">Délai de réponse</div>
          <div class="small bold">{{ issue.slaHours }} h</div>
          <div class="tiny muted">Échéance {{ issue.dueDate | dateFr }}</div>
        </div>
        <div>
          <div class="stat-label">Temps de résolution</div>
          <div class="small bold">{{ issue.resolutionHours != null ? issue.resolutionHours + ' h' : 'en cours' }}</div>
        </div>
      </div>

      <div class="field">
        <label>Description</label>
        <p class="small secondary-text">{{ issue.description || 'Aucune description fournie.' }}</p>
      </div>

      <div class="field">
        <label>Responsable du traitement</label>
        <select class="select" [ngModel]="assignee()" (ngModelChange)="reassign($event)">
          <option [ngValue]="null">— Non affecté —</option>
          <option *ngFor="let u of users()" [ngValue]="u.id">{{ u.fullName }} ({{ u.role | label }})</option>
        </select>
      </div>

      <div class="form-row">
        <div class="field">
          <label>Cause racine</label>
          <textarea class="textarea" [ngModel]="rootCause()" (ngModelChange)="rootCause.set($event)"
                    placeholder="Pourquoi le problème est-il survenu ?"></textarea>
        </div>
        <div class="field">
          <label>Action corrective</label>
          <textarea class="textarea" [ngModel]="correctiveAction()" (ngModelChange)="correctiveAction.set($event)"
                    placeholder="Que fait-on pour le résoudre et éviter qu'il revienne ?"></textarea>
        </div>
      </div>

      <button class="btn btn-ghost btn-sm mb-3" (click)="saveAnalysis()">💾 Enregistrer l'analyse</button>

      <!-- Discussion -->
      <div class="field">
        <label>Suivi ({{ issue.comments.length }})</label>
        <div style="max-height:190px;overflow-y:auto;border:1px solid var(--border);border-radius:var(--radius-sm)">
          <div *ngFor="let c of issue.comments" style="padding:.6rem .8rem;border-bottom:1px solid var(--border)">
            <div class="flex justify-between">
              <strong class="small">{{ c.authorName }}</strong>
              <span class="tiny muted">{{ c.createdAt | timeAgo }}</span>
            </div>
            <p class="small secondary-text">{{ c.message }}</p>
          </div>
          <div class="empty tiny" *ngIf="!issue.comments.length" style="padding:1rem">Aucun échange pour le moment.</div>
        </div>
      </div>

      <div class="flex gap-2">
        <input class="input" [ngModel]="newComment()" (ngModelChange)="newComment.set($event)"
               placeholder="Ajouter un commentaire…" (keyup.enter)="addComment()">
        <button class="btn btn-ghost" (click)="addComment()">Envoyer</button>
      </div>

      <ng-container modal-actions>
        <button class="btn btn-ghost" (click)="selected.set(null)">Fermer</button>
        <button class="btn btn-ghost" *ngIf="issue.status === 'OPEN'"
                (click)="setStatus(issue, 'ACKNOWLEDGED')">Prendre en charge</button>
        <button class="btn btn-primary" *ngIf="issue.status === 'ACKNOWLEDGED'"
                (click)="setStatus(issue, 'IN_PROGRESS')">Démarrer le traitement</button>
        <button class="btn btn-success" *ngIf="issue.open"
                (click)="setStatus(issue, 'RESOLVED')">✔ Résoudre</button>
        <button class="btn btn-ghost" *ngIf="issue.status === 'RESOLVED'"
                (click)="setStatus(issue, 'CLOSED')">Clôturer</button>
      </ng-container>
    </app-modal>
  `
})
export class IssuesComponent implements OnInit {
  private issuesApi = inject(IssueService);
  private productsApi = inject(ProductService);
  private usersApi = inject(UserService);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  protected loading = signal(true);
  protected issues = signal<Issue[]>([]);
  protected products = signal<ProductView[]>([]);
  protected users = signal<UserProfile[]>([]);
  protected selected = signal<Issue | null>(null);

  protected search = signal('');
  protected statusFilter = signal('');
  protected severityFilter = signal('');
  protected onlyBlocking = signal(false);

  protected rootCause = signal('');
  protected correctiveAction = signal('');
  protected newComment = signal('');
  protected assignee = signal<string | null>(null);

  protected readonly statuses: IssueStatus[] = ['OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED'];
  protected readonly severities: Severity[] = ['MINOR', 'MAJOR', 'CRITICAL', 'BLOCKING'];

  protected filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    const status = this.statusFilter();
    const severity = this.severityFilter();
    const blockingOnly = this.onlyBlocking();

    return this.issues().filter(i => {
      if (term && !`${i.reference} ${i.title}`.toLowerCase().includes(term)) return false;
      if (status && i.status !== status) return false;
      if (severity && i.severity !== severity) return false;
      if (blockingOnly && !i.blocksStage) return false;
      return true;
    });
  });

  protected openCount = computed(() => this.issues().filter(i => i.open).length);
  protected blockingCount = computed(() => this.issues().filter(i => i.blocksStage && i.open).length);
  protected slaCount = computed(() => this.issues().filter(i => i.slaBreached && i.open).length);
  protected resolvedCount = computed(() =>
    this.issues().filter(i => i.status === 'RESOLVED' || i.status === 'CLOSED').length);

  ngOnInit(): void {
    this.load();
    this.productsApi.list().subscribe({ next: list => this.products.set(list) });
    this.usersApi.directory().subscribe({
      next: dir => this.users.set(Object.values(dir).flat()),
      error: () => this.users.set([])
    });
  }

  protected load() {
    this.loading.set(true);
    this.issuesApi.list().subscribe({
      next: list => {
        this.issues.set(list);
        this.loading.set(false);
        // Deep link from a notification: /issues/:id opens straight onto the record.
        const id = this.route.snapshot.paramMap.get('id');
        if (id) {
          const match = list.find(i => i.id === id);
          if (match) this.open(match);
        }
      },
      error: () => this.loading.set(false)
    });
  }

  protected open(issue: Issue) {
    this.selected.set(issue);
    this.rootCause.set(issue.rootCause ?? '');
    this.correctiveAction.set(issue.correctiveAction ?? '');
    this.assignee.set(issue.assigneeId ?? null);
    this.newComment.set('');
  }

  protected referenceOf(productId: string): string {
    return this.products().find(p => p.id === productId)?.reference ?? '—';
  }

  protected nameOf(userId?: string): string {
    if (!userId) return 'Non affecté';
    return this.users().find(u => u.id === userId)?.fullName ?? 'Utilisateur inconnu';
  }

  protected saveAnalysis() {
    const issue = this.selected();
    if (!issue) return;
    this.issuesApi.update(issue.id, {
      rootCause: this.rootCause(),
      correctiveAction: this.correctiveAction(),
      blocksStage: issue.blocksStage
    }).subscribe({
      next: updated => {
        this.selected.set(updated);
        this.toast.success('Analyse enregistrée');
        this.load();
      },
      error: err => this.toast.error('Enregistrement impossible', err.error?.message)
    });
  }

  protected reassign(userId: string | null) {
    const issue = this.selected();
    if (!issue || !userId) return;
    this.assignee.set(userId);
    this.issuesApi.assign(issue.id, userId).subscribe({
      next: updated => {
        this.selected.set(updated);
        this.toast.success('Blocage réaffecté', this.nameOf(userId) + ' a été notifié.');
        this.load();
      },
      error: err => this.toast.error('Affectation impossible', err.error?.message)
    });
  }

  protected setStatus(issue: Issue, status: IssueStatus) {
    // Resolving requires a corrective action: the API refuses otherwise.
    const comment = status === 'RESOLVED' ? this.correctiveAction() : undefined;
    this.issuesApi.changeStatus(issue.id, status, comment).subscribe({
      next: updated => {
        this.selected.set(updated);
        this.toast.success(`Blocage ${label(status).toLowerCase()}`);
        this.load();
      },
      error: err => this.toast.error('Changement de statut refusé', err.error?.message)
    });
  }

  protected addComment() {
    const issue = this.selected();
    const message = this.newComment().trim();
    if (!issue || !message) return;

    this.issuesApi.comment(issue.id, message).subscribe({
      next: updated => {
        this.selected.set(updated);
        this.newComment.set('');
      },
      error: err => this.toast.error('Commentaire non envoyé', err.error?.message)
    });
  }
}
