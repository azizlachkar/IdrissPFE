import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { label } from '../../core/labels';
import { AuditLog, PipelineStageTemplate, Role, UserProfile } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { ToastService } from '../../core/services/toast.service';
import { UserService } from '../../core/services/user.service';
import { BadgeClassPipe, DateFrPipe, LabelPipe, TimeAgoPipe } from '../../shared/pipes';
import { EmptyComponent, LoaderComponent, StatComponent } from '../../shared/ui';

/**
 * Administration: accounts and roles, the audit trail, and the pipeline template the
 * workflow engine applies to every new product.
 */
@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    StatComponent, EmptyComponent, LoaderComponent,
    LabelPipe, BadgeClassPipe, DateFrPipe, TimeAgoPipe
  ],
  template: `
    <div class="page-head">
      <div>
        <h1>🛡 Administration</h1>
        <p>Comptes, habilitations, traçabilité globale et référentiel du pipeline.</p>
      </div>
      <div class="page-head-actions">
        <button class="btn btn-ghost" (click)="load()">↻ Actualiser</button>
      </div>
    </div>

    <div class="grid grid-4 mb-3">
      <app-stat label="Comptes" [value]="users().length"></app-stat>
      <app-stat label="Actifs" [value]="activeCount()" tone="ok"></app-stat>
      <app-stat label="En attente de validation" [value]="pendingCount()"
                [tone]="pendingCount() ? 'warn' : 'ok'"></app-stat>
      <app-stat label="Événements tracés" [value]="audit().length" tone="info"></app-stat>
    </div>

    <div class="tabs">
      <button class="tab" [class.active]="tab() === 'users'" (click)="tab.set('users')">Utilisateurs</button>
      <button class="tab" [class.active]="tab() === 'audit'" (click)="tab.set('audit')">Traçabilité</button>
      <button class="tab" [class.active]="tab() === 'pipeline'" (click)="tab.set('pipeline')">Référentiel pipeline</button>
    </div>

    <app-loader *ngIf="loading()"></app-loader>

    <!-- ----------------------------------------------------------- users -->
    <div class="card" *ngIf="tab() === 'users' && !loading()">
      <div class="card-body flush table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>Collaborateur</th><th>Matricule</th><th>Email</th>
              <th>Département</th><th>Rôle</th><th>État</th><th class="right">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let u of users()">
              <td>
                <div class="flex items-center gap-2">
                  <span class="avatar sm">{{ u.initials }}</span>
                  <div>
                    <div class="bold">{{ u.fullName }}</div>
                    <div class="tiny muted">{{ u.poste | label }}</div>
                  </div>
                </div>
              </td>
              <td class="mono small">{{ u.matricule || '—' }}</td>
              <td class="small truncate" style="max-width:190px">{{ u.email }}</td>
              <td class="small">
                {{ u.departement | label }}
                <div class="tiny muted">{{ u.serviceUnit | label }}</div>
              </td>
              <td>
                <select class="select btn-sm" style="width:auto"
                        [ngModel]="u.role" (ngModelChange)="changeRole(u, $event)">
                  <option *ngFor="let r of roles" [value]="r">{{ lbl(r) }}</option>
                </select>
              </td>
              <td>
                <div class="flex gap-1 wrap">
                  <span class="badge" [ngClass]="u.active ? 'success' : 'danger'">
                    {{ u.active ? 'Actif' : 'Suspendu' }}
                  </span>
                  <span class="badge warning" *ngIf="!u.enabled">Email non vérifié</span>
                </div>
              </td>
              <td class="right nowrap">
                <button class="btn btn-success btn-sm" *ngIf="!u.enabled" (click)="confirm(u)">
                  ✔ Valider le compte
                </button>
                <button class="btn btn-sm" [ngClass]="u.active ? 'btn-danger' : 'btn-ghost'"
                        (click)="toggleActive(u)">
                  {{ u.active ? 'Suspendre' : 'Réactiver' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ----------------------------------------------------------- audit -->
    <div class="card" *ngIf="tab() === 'audit' && !loading()">
      <div class="card-body flush table-wrap">
        <table class="table" *ngIf="audit().length; else noAudit">
          <thead>
            <tr><th>Horodatage</th><th>Auteur</th><th>Action</th><th>Objet</th><th>Détail</th></tr>
          </thead>
          <tbody>
            <tr *ngFor="let a of audit()">
              <td class="small nowrap">
                <div>{{ a.timestamp | dateFr:true }}</div>
                <div class="tiny muted">{{ a.timestamp | timeAgo }}</div>
              </td>
              <td class="small bold">{{ a.actorName }}</td>
              <td><span class="badge" [ngClass]="a.action | badgeClass">{{ a.action | label }}</span></td>
              <td class="small">{{ a.entityType }}</td>
              <td class="small">
                {{ a.summary }}
                <div class="tiny muted" *ngIf="a.previousValue || a.newValue">
                  {{ a.previousValue }} → {{ a.newValue }}
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <ng-template #noAudit><app-empty icon="🕘" message="Aucun événement enregistré."></app-empty></ng-template>
      </div>
    </div>

    <!-- -------------------------------------------------------- pipeline -->
    <div class="card" *ngIf="tab() === 'pipeline' && !loading()">
      <div class="card-head">
        <h3>Modèle de pipeline appliqué aux nouveaux produits</h3>
      </div>
      <div class="card-body flush table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>#</th><th>Jalon</th><th>Responsable par défaut</th>
              <th class="num">Durée</th><th>Livrables requis</th><th>Approbations requises</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let s of pipeline()">
              <td class="bold">{{ s.order }}</td>
              <td class="bold">{{ s.name | label }}</td>
              <td><span class="badge">{{ s.defaultOwnerRole | label }}</span></td>
              <td class="num mono">{{ s.nominalDurationDays }} j</td>
              <td>
                <div class="flex gap-1 wrap">
                  <span class="badge info" *ngFor="let d of s.requiredDeliverables">{{ d | label }}</span>
                  <span class="tiny muted" *ngIf="!s.requiredDeliverables.length">Aucun</span>
                </div>
              </td>
              <td>
                <div class="flex gap-1 wrap">
                  <span class="badge purple" *ngFor="let r of s.approverRoles">{{ r | label }}</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="card-body">
        <div class="seed-hint">
          Les projets <b>NPI</b> déroulent les 10 jalons. Les projets <b>Production</b> sautent
          « Fabrication prototype » et « Approbation client », soit 8 jalons.
          Les durées sont exprimées en jours ouvrés et servent à planifier les échéances
          à partir de la date de lancement du produit.
        </div>
      </div>
    </div>
  `
})
export class AdminComponent implements OnInit {
  private usersApi = inject(UserService);
  private dashboard = inject(DashboardService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);

  protected loading = signal(true);
  protected tab = signal<'users' | 'audit' | 'pipeline'>('users');

  protected users = signal<UserProfile[]>([]);
  protected audit = signal<AuditLog[]>([]);
  protected pipeline = signal<PipelineStageTemplate[]>([]);

  protected lbl = label;
  protected readonly roles: Role[] = [
    'ADMIN', 'CHEF_PROJET', 'METHODISTE', 'QUALITICIEN',
    'TECHNICIEN', 'RESPONSABLE_PRODUCTION', 'CONTROLE_TECHNIQUE', 'VIEWER'
  ];

  protected activeCount = computed(() => this.users().filter(u => u.active).length);
  protected pendingCount = computed(() => this.users().filter(u => !u.enabled).length);

  ngOnInit(): void {
    this.load();
  }

  protected load() {
    this.loading.set(true);
    this.usersApi.list().subscribe({
      next: list => {
        this.users.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
    this.dashboard.activity(100).subscribe({ next: list => this.audit.set(list) });
    this.usersApi.pipelineTemplate().subscribe({ next: list => this.pipeline.set(list) });
  }

  protected changeRole(user: UserProfile, role: Role) {
    if (user.role === role) return;
    this.usersApi.changeRole(user.id, role).subscribe({
      next: () => {
        this.toast.success('Rôle modifié', `${user.fullName} est désormais ${label(role)}.`);
        this.load();
      },
      error: err => {
        this.toast.error('Modification impossible', err.error?.message);
        this.load();
      }
    });
  }

  protected toggleActive(user: UserProfile) {
    this.usersApi.setActive(user.id, !user.active).subscribe({
      next: () => {
        this.toast.success(user.active ? 'Compte suspendu' : 'Compte réactivé');
        this.load();
      },
      error: err => this.toast.error('Action impossible', err.error?.message)
    });
  }

  protected confirm(user: UserProfile) {
    this.usersApi.confirm(user.id).subscribe({
      next: () => {
        this.toast.success('Compte validé', `${user.fullName} peut maintenant se connecter.`);
        this.load();
      },
      error: err => this.toast.error('Validation impossible', err.error?.message)
    });
  }
}
