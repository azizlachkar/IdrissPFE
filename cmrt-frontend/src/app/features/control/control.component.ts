import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { label } from '../../core/labels';
import {
  ProductView, Reservation, ResourceStatus, ResourceType, TestResource, UserProfile
} from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { ProductService } from '../../core/services/product.service';
import { ResourceService } from '../../core/services/resource.service';
import { ToastService } from '../../core/services/toast.service';
import { UserService } from '../../core/services/user.service';
import { BadgeClassPipe, DateFrPipe, LabelPipe } from '../../shared/pipes';
import { EmptyComponent, LoaderComponent, ModalComponent, StatComponent } from '../../shared/ui';

/**
 * Technical control: the state of every test bench, build board and tooling item, plus
 * the booking workflow. Requesting a slot notifies the asset owner; technical control
 * accepts or refuses, and overlapping slots are rejected by the API.
 */
@Component({
  selector: 'app-control',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ModalComponent,
    StatComponent, EmptyComponent, LoaderComponent,
    LabelPipe, BadgeClassPipe, DateFrPipe
  ],
  template: `
    <div class="page-head">
      <div>
        <h1>⚡ Contrôle technique</h1>
        <p>État des interfaces de test, organisation des build &amp; test boards et réservations.</p>
      </div>
      <div class="page-head-actions">
        <button class="btn btn-ghost" (click)="load()">↻ Actualiser</button>
        <button class="btn btn-primary" *ngIf="canManage" (click)="openResourceForm()">+ Nouveau moyen</button>
      </div>
    </div>

    <div class="grid grid-4 mb-3">
      <app-stat label="Moyens" [value]="resources().length"></app-stat>
      <app-stat label="Disponibles" [value]="countByStatus('AVAILABLE')" tone="ok"></app-stat>
      <app-stat label="En maintenance" [value]="countByStatus('MAINTENANCE')" tone="warn"></app-stat>
      <app-stat label="Demandes en attente" [value]="pendingCount()"
                [tone]="pendingCount() ? 'warn' : 'ok'"></app-stat>
    </div>

    <div class="tabs">
      <button class="tab" [class.active]="tab() === 'resources'" (click)="tab.set('resources')">Moyens de test</button>
      <button class="tab" [class.active]="tab() === 'reservations'" (click)="tab.set('reservations')">
        Réservations <span class="chip">{{ reservations().length }}</span>
      </button>
    </div>

    <app-loader *ngIf="loading()"></app-loader>

    <!-- ------------------------------------------------------- resources -->
    <div class="grid grid-3" *ngIf="tab() === 'resources' && !loading()">
      <div class="card" *ngFor="let r of resources()">
        <div class="card-body">
          <div class="flex items-center gap-2 mb-2">
            <span style="font-size:1.3rem">{{ icon(r.status) }}</span>
            <div style="min-width:0;flex:1">
              <strong class="truncate" style="display:block">{{ r.nom }}</strong>
              <span class="tiny muted mono">{{ r.code }}</span>
            </div>
          </div>

          <div class="flex gap-1 wrap mb-2">
            <span class="badge" [ngClass]="r.status | badgeClass">{{ r.status | label }}</span>
            <span class="badge">{{ r.type | label }}</span>
            <span class="badge danger" *ngIf="r.calibrationExpired">Calibration expirée</span>
          </div>

          <table class="table" style="font-size:.76rem">
            <tbody>
              <tr><td class="muted">Emplacement</td><td class="right">{{ r.location || '—' }}</td></tr>
              <tr><td class="muted">Responsable</td><td class="right">{{ nameOf(r.ownerId) }}</td></tr>
              <tr *ngIf="r.requiresCalibration">
                <td class="muted">Calibration</td>
                <td class="right" [style.color]="r.calibrationExpired ? 'var(--danger)' : null">
                  {{ r.calibrationExpiry | dateFr }}
                </td>
              </tr>
              <tr><td class="muted">Maintenance</td><td class="right">{{ r.nextMaintenanceDate | dateFr }}</td></tr>
            </tbody>
          </table>

          <div class="btn-group mt-2">
            <button class="btn btn-primary btn-sm" style="flex:1"
                    [disabled]="r.status === 'MAINTENANCE' || r.status === 'OUT_OF_SERVICE' || r.calibrationExpired"
                    (click)="openReservation(r)">
              📅 Réserver
            </button>
            <select class="select btn-sm" *ngIf="canManage" style="width:auto"
                    [ngModel]="r.status" (ngModelChange)="setStatus(r, $event)">
              <option *ngFor="let s of resourceStatuses" [value]="s">{{ lbl(s) }}</option>
            </select>
          </div>
        </div>
      </div>

      <app-empty *ngIf="!resources().length" icon="⚡" message="Aucun moyen de test enregistré."></app-empty>
    </div>

    <!-- ---------------------------------------------------- reservations -->
    <div class="card" *ngIf="tab() === 'reservations' && !loading()">
      <div class="card-body flush table-wrap">
        <table class="table" *ngIf="reservations().length; else noReservations">
          <thead>
            <tr>
              <th>Moyen</th><th>Demandeur</th><th>Produit</th><th>Créneau</th>
              <th>Durée</th><th>Objet</th><th>Statut</th><th class="right">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let res of reservations()">
              <td class="bold mono">{{ codeOf(res.resourceId) }}</td>
              <td class="small">{{ nameOf(res.requesterId) }}</td>
              <td class="small">{{ referenceOf(res.productId) }}</td>
              <td class="small">
                <div>{{ res.startDate | dateFr:true }}</div>
                <div class="tiny muted">→ {{ res.endDate | dateFr:true }}</div>
              </td>
              <td class="mono small">{{ res.durationHours != null ? (res.durationHours + ' h') : '—' }}</td>
              <td class="tiny muted truncate" style="max-width:170px">{{ res.purpose }}</td>
              <td><span class="badge" [ngClass]="res.status | badgeClass">{{ res.status | label }}</span></td>
              <td class="right nowrap">
                <ng-container *ngIf="canManage">
                  <button class="btn btn-success btn-sm" *ngIf="res.status === 'PENDING'"
                          (click)="decide(res, true)">✔</button>
                  <button class="btn btn-danger btn-sm" *ngIf="res.status === 'PENDING'"
                          (click)="decide(res, false)">✘</button>
                  <button class="btn btn-ghost btn-sm" *ngIf="res.status === 'APPROVED'"
                          (click)="start(res)">▶ Démarrer</button>
                  <button class="btn btn-ghost btn-sm" *ngIf="res.status === 'IN_PROGRESS'"
                          (click)="complete(res)">■ Terminer</button>
                </ng-container>
                <button class="btn btn-ghost btn-sm"
                        *ngIf="res.status === 'PENDING' || res.status === 'APPROVED'"
                        (click)="cancel(res)">Annuler</button>
              </td>
            </tr>
          </tbody>
        </table>
        <ng-template #noReservations>
          <app-empty icon="📅" message="Aucune réservation enregistrée."></app-empty>
        </ng-template>
      </div>
    </div>

    <!-- ------------------------------------------------------- dialogs -->
    <app-modal *ngIf="reservationTarget() as target"
               [title]="'Réserver — ' + target.nom" (closed)="reservationTarget.set(null)">
      <div class="seed-hint mb-3">
        Le responsable du moyen recevra une notification et devra accepter le créneau.
        Un créneau qui chevauche une réservation existante sera refusé.
      </div>

      <div class="form-row">
        <div class="field">
          <label>Début <span class="req">*</span></label>
          <input type="datetime-local" class="input" [ngModel]="slotStart()" (ngModelChange)="slotStart.set($event)">
        </div>
        <div class="field">
          <label>Fin <span class="req">*</span></label>
          <input type="datetime-local" class="input" [ngModel]="slotEnd()" (ngModelChange)="slotEnd.set($event)">
        </div>
      </div>

      <div class="field">
        <label>Produit concerné</label>
        <select class="select" [ngModel]="slotProduct()" (ngModelChange)="slotProduct.set($event)">
          <option [ngValue]="null">— Aucun —</option>
          <option *ngFor="let p of products()" [ngValue]="p.id">{{ p.reference }} — {{ p.nom }}</option>
        </select>
      </div>

      <div class="field">
        <label>Objet de la réservation</label>
        <textarea class="textarea" [ngModel]="slotPurpose()" (ngModelChange)="slotPurpose.set($event)"
                  placeholder="Ex. Essai de continuité sur le prototype révision B."></textarea>
      </div>

      <ng-container modal-actions>
        <button class="btn btn-ghost" (click)="reservationTarget.set(null)">Annuler</button>
        <button class="btn btn-primary" [disabled]="busy()" (click)="submitReservation()">
          {{ busy() ? 'Envoi…' : 'Demander le créneau' }}
        </button>
      </ng-container>
    </app-modal>

    <app-modal *ngIf="resourceFormOpen()" title="Nouveau moyen de test" (closed)="resourceFormOpen.set(false)">
      <div class="form-row">
        <div class="field">
          <label>Code <span class="req">*</span></label>
          <input class="input" [ngModel]="draft.code" (ngModelChange)="draft.code = $event" placeholder="BT-HT-07">
        </div>
        <div class="field">
          <label>Type</label>
          <select class="select" [ngModel]="draft.type" (ngModelChange)="draft.type = $event">
            <option *ngFor="let t of resourceTypes" [value]="t">{{ lbl(t) }}</option>
          </select>
        </div>
      </div>

      <div class="field">
        <label>Désignation <span class="req">*</span></label>
        <input class="input" [ngModel]="draft.nom" (ngModelChange)="draft.nom = $event">
      </div>

      <div class="form-row">
        <div class="field">
          <label>Emplacement</label>
          <input class="input" [ngModel]="draft.location" (ngModelChange)="draft.location = $event"
                 placeholder="Zone 1 - Build Board">
        </div>
        <div class="field">
          <label>Responsable</label>
          <select class="select" [ngModel]="draft.ownerId" (ngModelChange)="draft.ownerId = $event">
            <option [ngValue]="undefined">— Non affecté —</option>
            <option *ngFor="let u of users()" [ngValue]="u.id">{{ u.fullName }}</option>
          </select>
        </div>
      </div>

      <label class="checkbox mb-2">
        <input type="checkbox" [ngModel]="draft.requiresCalibration"
               (ngModelChange)="draft.requiresCalibration = $event">
        <span>Nécessite une calibration périodique</span>
      </label>

      <div class="field" *ngIf="draft.requiresCalibration">
        <label>Validité de la calibration</label>
        <input type="date" class="input" [ngModel]="draft.calibrationExpiry"
               (ngModelChange)="draft.calibrationExpiry = $event">
        <div class="field-hint">Une calibration expirée bloque toute nouvelle réservation.</div>
      </div>

      <ng-container modal-actions>
        <button class="btn btn-ghost" (click)="resourceFormOpen.set(false)">Annuler</button>
        <button class="btn btn-primary" [disabled]="busy()" (click)="createResource()">Enregistrer</button>
      </ng-container>
    </app-modal>
  `
})
export class ControlComponent implements OnInit {
  private api = inject(ResourceService);
  private productsApi = inject(ProductService);
  private usersApi = inject(UserService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);

  protected loading = signal(true);
  protected busy = signal(false);
  protected tab = signal<'resources' | 'reservations'>('resources');

  protected resources = signal<TestResource[]>([]);
  protected reservations = signal<Reservation[]>([]);
  protected products = signal<ProductView[]>([]);
  protected users = signal<UserProfile[]>([]);

  protected reservationTarget = signal<TestResource | null>(null);
  protected slotStart = signal('');
  protected slotEnd = signal('');
  protected slotPurpose = signal('');
  protected slotProduct = signal<string | null>(null);

  protected resourceFormOpen = signal(false);
  protected draft: Partial<TestResource> = {};

  protected lbl = label;
  protected readonly resourceTypes: ResourceType[] = ['TEST_BOARD', 'BUILD_BOARD', 'TEST_INTERFACE', 'TOOLING', 'FIXTURE'];
  protected readonly resourceStatuses: ResourceStatus[] = ['AVAILABLE', 'RESERVED', 'IN_USE', 'MAINTENANCE', 'OUT_OF_SERVICE'];

  protected readonly canManage = this.auth.hasRole('CONTROLE_TECHNIQUE');

  protected pendingCount = computed(() => this.reservations().filter(r => r.status === 'PENDING').length);

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
    this.api.list().subscribe({
      next: list => {
        this.resources.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
    this.api.listReservations().subscribe({ next: list => this.reservations.set(list) });
  }

  protected countByStatus(status: ResourceStatus): number {
    return this.resources().filter(r => r.status === status).length;
  }

  protected icon(status: ResourceStatus): string {
    switch (status) {
      case 'AVAILABLE': return '🟢';
      case 'IN_USE': return '🔵';
      case 'RESERVED': return '🟡';
      case 'MAINTENANCE': return '🟠';
      default: return '🔴';
    }
  }

  protected nameOf(userId?: string): string {
    if (!userId) return 'Non affecté';
    return this.users().find(u => u.id === userId)?.fullName ?? '—';
  }

  protected codeOf(resourceId: string): string {
    return this.resources().find(r => r.id === resourceId)?.code ?? '—';
  }

  protected referenceOf(productId?: string): string {
    if (!productId) return '—';
    return this.products().find(p => p.id === productId)?.reference ?? '—';
  }

  // ------------------------------------------------------------ booking

  protected openReservation(resource: TestResource) {
    // Default to a two-hour slot starting on the next hour.
    const start = new Date();
    start.setMinutes(0, 0, 0);
    start.setHours(start.getHours() + 1);
    const end = new Date(start.getTime() + 2 * 3600_000);

    this.slotStart.set(this.toLocalInput(start));
    this.slotEnd.set(this.toLocalInput(end));
    this.slotPurpose.set('');
    this.slotProduct.set(null);
    this.reservationTarget.set(resource);
  }

  private toLocalInput(date: Date): string {
    const offset = date.getTimezoneOffset() * 60000;
    return new Date(date.getTime() - offset).toISOString().slice(0, 16);
  }

  protected submitReservation() {
    const resource = this.reservationTarget();
    if (!resource) return;
    if (!this.slotStart() || !this.slotEnd()) {
      this.toast.warning('Créneau incomplet', 'Indiquez le début et la fin.');
      return;
    }
    this.busy.set(true);

    this.api.reserve({
      resourceId: resource.id,
      startDate: `${this.slotStart()}:00`,
      endDate: `${this.slotEnd()}:00`,
      purpose: this.slotPurpose() || undefined,
      productId: this.slotProduct() ?? undefined
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.reservationTarget.set(null);
        this.toast.success('Demande envoyée', 'Le contrôle technique a été notifié.');
        this.load();
      },
      error: err => {
        this.busy.set(false);
        this.toast.error('Réservation refusée', err.error?.message ?? 'Veuillez choisir un autre créneau.');
      }
    });
  }

  protected decide(reservation: Reservation, approved: boolean) {
    const request$ = approved
      ? this.api.approveReservation(reservation.id)
      : this.api.rejectReservation(reservation.id, 'Créneau non disponible');
    this.act(request$, approved ? 'Réservation acceptée' : 'Réservation refusée');
  }

  protected start(reservation: Reservation) {
    this.act(this.api.startReservation(reservation.id), 'Utilisation démarrée');
  }

  protected complete(reservation: Reservation) {
    this.act(this.api.completeReservation(reservation.id), 'Moyen libéré');
  }

  protected cancel(reservation: Reservation) {
    this.act(this.api.cancelReservation(reservation.id), 'Réservation annulée');
  }

  // ---------------------------------------------------------- resources

  protected setStatus(resource: TestResource, status: ResourceStatus) {
    if (resource.status === status) return;
    this.act(this.api.changeStatus(resource.id, status), `${resource.code} → ${label(status)}`);
  }

  protected openResourceForm() {
    this.draft = { type: 'TEST_BOARD', requiresCalibration: false };
    this.resourceFormOpen.set(true);
  }

  protected createResource() {
    if (!this.draft.code?.trim() || !this.draft.nom?.trim()) {
      this.toast.warning('Champs manquants', 'Le code et la désignation sont obligatoires.');
      return;
    }
    this.busy.set(true);
    this.api.create(this.draft).subscribe({
      next: () => {
        this.busy.set(false);
        this.resourceFormOpen.set(false);
        this.toast.success('Moyen enregistré');
        this.load();
      },
      error: err => {
        this.busy.set(false);
        this.toast.error('Enregistrement impossible', err.error?.message);
      }
    });
  }

  private act(request$: import('rxjs').Observable<unknown>, message: string) {
    request$.subscribe({
      next: () => {
        this.toast.success(message);
        this.load();
      },
      error: err => this.toast.error('Action refusée', err.error?.message)
    });
  }
}
