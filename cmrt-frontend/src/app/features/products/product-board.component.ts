import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { progressClass } from '../../core/labels';
import { Customer, ProductStatus, ProductView, ProjectType } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { ProductService } from '../../core/services/product.service';
import { ToastService } from '../../core/services/toast.service';
import { BadgeClassPipe, DateFrPipe, DaysLeftPipe, LabelPipe } from '../../shared/pipes';
import { EmptyComponent, LoaderComponent, ProgressComponent, StatComponent } from '../../shared/ui';
import { IssueFormComponent } from '../issues/issue-form.component';
import { ProductFormComponent } from './product-form.component';

/**
 * The "Méthode Production" and "Méthode NPI" boards. Both render the same table -
 * only the project type they filter on differs - so one component serves both routes.
 */
@Component({
  selector: 'app-product-board',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ProductFormComponent, IssueFormComponent,
    StatComponent, EmptyComponent, LoaderComponent, ProgressComponent,
    LabelPipe, BadgeClassPipe, DateFrPipe, DaysLeftPipe
  ],
  template: `
    <div class="page-head">
      <div>
        <h1>{{ isNpi() ? '◈ Méthode NPI' : '⚙ Méthode Production' }}</h1>
        <p>
          {{ isNpi()
            ? 'Nouveaux produits en cours d\\'industrialisation, de la revue technique au lancement série.'
            : 'Produits en vie série : évolutions, industrialisation continue et remontée des blocages.' }}
        </p>
      </div>
      <div class="page-head-actions">
        <button class="btn btn-ghost" (click)="load()">↻ Actualiser</button>
        <button class="btn btn-primary" *ngIf="canCreate" (click)="openCreate()">+ Nouveau produit</button>
      </div>
    </div>

    <div class="grid grid-4 mb-3">
      <app-stat label="Produits" [value]="products().length"></app-stat>
      <app-stat label="Avancement moyen" [value]="averageProgress()" unit="%" tone="info"></app-stat>
      <app-stat label="Bloqués" [value]="blockedCount()" [tone]="blockedCount() ? 'bad' : 'ok'"></app-stat>
      <app-stat label="À risque" [value]="atRiskCount()" [tone]="atRiskCount() ? 'warn' : 'ok'"></app-stat>
    </div>

    <div class="filters">
      <input class="input search" placeholder="🔍 Rechercher une référence ou une désignation…"
             [ngModel]="search()" (ngModelChange)="search.set($event)">

      <select class="select" [ngModel]="customerFilter()" (ngModelChange)="customerFilter.set($event)">
        <option value="">Tous les clients</option>
        <option *ngFor="let c of customers" [value]="c">{{ c | label }}</option>
      </select>

      <select class="select" [ngModel]="statusFilter()" (ngModelChange)="statusFilter.set($event)">
        <option value="">Tous les statuts</option>
        <option *ngFor="let s of statuses" [value]="s">{{ s | label }}</option>
      </select>

      <label class="checkbox">
        <input type="checkbox" [ngModel]="onlyProblems()" (ngModelChange)="onlyProblems.set($event)">
        <span>Uniquement bloqués ou en retard</span>
      </label>

      <span class="muted small" style="margin-left:auto">{{ filtered().length }} produit(s)</span>
    </div>

    <app-loader *ngIf="loading()"></app-loader>

    <div class="card" *ngIf="!loading()">
      <div class="card-body flush table-wrap">
        <table class="table" *ngIf="filtered().length; else noProducts">
          <thead>
            <tr>
              <th>Référence</th>
              <th>Désignation</th>
              <th>Client</th>
              <th>Jalon en cours</th>
              <th style="min-width:130px">Avancement</th>
              <th>Méthodiste</th>
              <th>Qualiticien</th>
              <th>SOP cible</th>
              <th>État</th>
              <th class="right">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let p of filtered()" class="row-link">
              <td (click)="open(p)">
                <div class="bold">{{ p.reference }}</div>
                <div class="tiny muted">{{ p.family | label }}</div>
              </td>
              <td (click)="open(p)">
                <div class="truncate" style="max-width:220px">{{ p.nom }}</div>
                <div class="tiny muted" *ngIf="p.programme">{{ p.programme }}</div>
              </td>
              <td (click)="open(p)"><span class="badge info">{{ p.customer | label }}</span></td>
              <td (click)="open(p)">
                <div class="small">{{ p.currentStageLabel || '—' }}</div>
                <div class="tiny muted">{{ p.completedStages }}/{{ p.totalStages }} jalons</div>
              </td>
              <td (click)="open(p)">
                <app-progress [value]="p.progressPercent" [tone]="tone(p)"></app-progress>
              </td>
              <td (click)="open(p)" class="small">{{ p.methodisteName }}</td>
              <td (click)="open(p)" class="small">{{ p.qualiticienName }}</td>
              <td (click)="open(p)">
                <div class="small">{{ p.targetSopDate | dateFr }}</div>
                <div class="tiny" [class.muted]="!p.atRisk" [style.color]="p.atRisk ? 'var(--danger)' : null">
                  {{ p.targetSopDate | daysLeft }}
                </div>
              </td>
              <td (click)="open(p)">
                <div class="flex gap-1 wrap">
                  <span class="badge" [ngClass]="p.status | badgeClass">{{ p.status | label }}</span>
                  <span class="badge danger" *ngIf="p.blocked">⛔ Bloqué</span>
                  <span class="badge danger" *ngIf="p.openIssues > 0">{{ p.openIssues }} blocage(s)</span>
                </div>
              </td>
              <td class="right nowrap">
                <button class="btn btn-danger btn-sm" (click)="declare(p, $event)" title="Déclarer un blocage">
                  🚨 Réclamation
                </button>
              </td>
            </tr>
          </tbody>
        </table>

        <ng-template #noProducts>
          <app-empty icon="📦"
                     [message]="products().length
                       ? 'Aucun produit ne correspond aux filtres.'
                       : 'Aucun produit sur ce périmètre pour le moment.'">
          </app-empty>
        </ng-template>
      </div>
    </div>

    <app-product-form *ngIf="formOpen()"
                      [product]="editing()"
                      [defaultProjectType]="projectType()"
                      (closed)="formOpen.set(false)"
                      (saved)="onSaved()">
    </app-product-form>

    <app-issue-form *ngIf="issueTarget() as target"
                    [product]="target"
                    (closed)="issueTarget.set(null)"
                    (created)="onIssueCreated()">
    </app-issue-form>
  `
})
export class ProductBoardComponent implements OnInit {
  private products_ = inject(ProductService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private auth = inject(AuthService);
  private toast = inject(ToastService);

  protected loading = signal(true);
  protected products = signal<ProductView[]>([]);
  protected projectType = signal<ProjectType>('NPI');

  protected formOpen = signal(false);
  protected editing = signal<ProductView | null>(null);
  protected issueTarget = signal<ProductView | null>(null);

  // Signals rather than plain fields: the filtered list is a computed() and must
  // recompute when any of these changes.
  protected search = signal('');
  protected customerFilter = signal('');
  protected statusFilter = signal('');
  protected onlyProblems = signal(false);

  protected readonly customers: Customer[] = ['CUMMINS', 'WAUKESHA', 'WABTEC', 'AUTRE'];
  protected readonly statuses: ProductStatus[] = [
    'DRAFT', 'IN_DEVELOPMENT', 'PILOT', 'MASS_PRODUCTION', 'ON_HOLD', 'CANCELLED'
  ];

  protected readonly canCreate = this.auth.hasRole('CHEF_PROJET', 'METHODISTE');

  protected isNpi = computed(() => this.projectType() === 'NPI');

  /** Filters run client-side: a board holds tens of rows, not thousands. */
  protected filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    const customer = this.customerFilter();
    const status = this.statusFilter();
    const problemsOnly = this.onlyProblems();

    return this.products().filter(p => {
      if (term && !`${p.reference} ${p.nom} ${p.programme ?? ''}`.toLowerCase().includes(term)) return false;
      if (customer && p.customer !== customer) return false;
      if (status && p.status !== status) return false;
      if (problemsOnly && !p.blocked && !p.atRisk) return false;
      return true;
    });
  });

  protected averageProgress = computed(() => {
    const list = this.products();
    if (!list.length) return 0;
    return Math.round(list.reduce((sum, p) => sum + p.progressPercent, 0) / list.length);
  });

  protected blockedCount = computed(() => this.products().filter(p => p.blocked).length);
  protected atRiskCount = computed(() => this.products().filter(p => p.atRisk).length);

  ngOnInit(): void {
    // One component serves both boards; the route data says which one.
    this.route.data.subscribe(data => {
      this.projectType.set((data['projectType'] as ProjectType) ?? 'NPI');
      this.load();
    });
  }

  protected load() {
    this.loading.set(true);
    this.products_.list(this.projectType()).subscribe({
      next: list => {
        this.products.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  protected tone(p: ProductView): string {
    return progressClass(p.progressPercent, p.blocked || p.atRisk);
  }

  protected open(p: ProductView) {
    this.router.navigate(['/products', p.id]);
  }

  protected openCreate() {
    this.editing.set(null);
    this.formOpen.set(true);
  }

  protected declare(p: ProductView, event: MouseEvent) {
    // Keep the row-click navigation from firing behind the dialog.
    event.stopPropagation();
    this.issueTarget.set(p);
  }

  protected onSaved() {
    this.formOpen.set(false);
    this.load();
  }

  protected onIssueCreated() {
    this.issueTarget.set(null);
    this.load();
  }
}
