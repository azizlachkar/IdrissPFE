import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { label } from '../../core/labels';
import { Priority, ProductView, Task, TaskStatus, UserProfile } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { ProductService } from '../../core/services/product.service';
import { TaskService } from '../../core/services/task.service';
import { ToastService } from '../../core/services/toast.service';
import { UserService } from '../../core/services/user.service';
import { BadgeClassPipe, DateFrPipe, LabelPipe } from '../../shared/pipes';
import { EmptyComponent, LoaderComponent, ModalComponent, StatComponent } from '../../shared/ui';

/** Task board. Columns are the task statuses; cards move by drag and drop. */
@Component({
  selector: 'app-tasks',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ModalComponent,
    StatComponent, EmptyComponent, LoaderComponent,
    LabelPipe, BadgeClassPipe, DateFrPipe
  ],
  template: `
    <div class="page-head">
      <div>
        <h1>☑ Tâches</h1>
        <p>Le travail à réaliser sur les projets, réparti par état d'avancement.</p>
      </div>
      <div class="page-head-actions">
        <button class="btn btn-ghost" (click)="load()">↻ Actualiser</button>
        <button class="btn btn-primary" (click)="openCreate()">+ Nouvelle tâche</button>
      </div>
    </div>

    <div class="grid grid-4 mb-3">
      <app-stat label="Total" [value]="tasks().length"></app-stat>
      <app-stat label="En cours" [value]="countByStatus('IN_PROGRESS')" tone="info"></app-stat>
      <app-stat label="En retard" [value]="overdueCount()" [tone]="overdueCount() ? 'bad' : 'ok'"></app-stat>
      <app-stat label="Terminées" [value]="countByStatus('DONE')" tone="ok"></app-stat>
    </div>

    <div class="filters">
      <select class="select" [ngModel]="productFilter()" (ngModelChange)="productFilter.set($event)">
        <option value="">Tous les produits</option>
        <option *ngFor="let p of products()" [value]="p.id">{{ p.reference }} — {{ p.nom }}</option>
      </select>

      <label class="checkbox">
        <input type="checkbox" [ngModel]="onlyMine()" (ngModelChange)="onlyMine.set($event)">
        <span>Uniquement mes tâches</span>
      </label>

      <span class="muted small" style="margin-left:auto">{{ filtered().length }} tâche(s)</span>
    </div>

    <app-loader *ngIf="loading()"></app-loader>

    <div class="kanban" *ngIf="!loading()">
      <div class="kanban-col" *ngFor="let col of columns"
           [class.drop-target]="dragOver() === col.status"
           (dragover)="onDragOver($event, col.status)"
           (dragleave)="dragOver.set(null)"
           (drop)="onDrop(col.status)">
        <div class="kanban-col-head">
          <span>{{ col.label }}</span>
          <span class="count">{{ inColumn(col.status).length }}</span>
        </div>
        <div class="kanban-items">
          <div class="kanban-card"
               *ngFor="let t of inColumn(col.status)"
               [ngClass]="'p-' + t.priority"
               [class.overdue]="t.overdue"
               draggable="true"
               (dragstart)="dragged.set(t)"
               (click)="openEdit(t)">
            <h4>{{ t.title }}</h4>
            <div class="meta">
              <span class="badge" [ngClass]="t.priority | badgeClass">{{ t.priority | label }}</span>
              <span *ngIf="t.dueDate" [style.color]="t.overdue ? 'var(--danger)' : null">
                📅 {{ t.dueDate | dateFr }}
              </span>
            </div>
            <div class="meta mt-1" *ngIf="t.productId">
              <span class="tiny muted">{{ referenceOf(t.productId) }}</span>
              <span class="tiny muted" *ngIf="t.assigneeId">· {{ nameOf(t.assigneeId) }}</span>
            </div>
          </div>

          <div class="empty tiny" *ngIf="!inColumn(col.status).length" style="padding:1rem .5rem">
            Aucune tâche
          </div>
        </div>
      </div>
    </div>

    <!-- Create / edit -->
    <app-modal *ngIf="formOpen()" [title]="editing() ? 'Modifier la tâche' : 'Nouvelle tâche'"
               (closed)="formOpen.set(false)">
      <div class="field">
        <label>Titre <span class="req">*</span></label>
        <input class="input" [ngModel]="draft.title" (ngModelChange)="draft.title = $event"
               placeholder="Ex. Vérifier la nomenclature des connecteurs">
      </div>

      <div class="field">
        <label>Description</label>
        <textarea class="textarea" [ngModel]="draft.description" (ngModelChange)="draft.description = $event"></textarea>
      </div>

      <div class="form-row">
        <div class="field">
          <label>Produit</label>
          <select class="select" [ngModel]="draft.productId" (ngModelChange)="draft.productId = $event">
            <option [ngValue]="undefined">— Aucun —</option>
            <option *ngFor="let p of products()" [ngValue]="p.id">{{ p.reference }}</option>
          </select>
        </div>
        <div class="field">
          <label>Responsable</label>
          <select class="select" [ngModel]="draft.assigneeId" (ngModelChange)="draft.assigneeId = $event">
            <option [ngValue]="undefined">— Non affecté —</option>
            <option *ngFor="let u of users()" [ngValue]="u.id">{{ u.fullName }}</option>
          </select>
        </div>
      </div>

      <div class="form-row">
        <div class="field">
          <label>Priorité</label>
          <select class="select" [ngModel]="draft.priority" (ngModelChange)="draft.priority = $event">
            <option *ngFor="let p of priorities" [value]="p">{{ lbl(p) }}</option>
          </select>
        </div>
        <div class="field">
          <label>Échéance</label>
          <input type="date" class="input" [ngModel]="draft.dueDate" (ngModelChange)="draft.dueDate = $event">
        </div>
        <div class="field">
          <label>Charge estimée (h)</label>
          <input type="number" class="input" [ngModel]="draft.estimatedHours"
                 (ngModelChange)="draft.estimatedHours = $event">
        </div>
      </div>

      <ng-container modal-actions>
        <button class="btn btn-danger" *ngIf="editing()" (click)="remove()">Supprimer</button>
        <button class="btn btn-ghost" (click)="formOpen.set(false)">Annuler</button>
        <button class="btn btn-primary" [disabled]="saving()" (click)="save()">
          {{ saving() ? 'Enregistrement…' : 'Enregistrer' }}
        </button>
      </ng-container>
    </app-modal>
  `
})
export class TasksComponent implements OnInit {
  private tasksApi = inject(TaskService);
  private productsApi = inject(ProductService);
  private usersApi = inject(UserService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);

  protected loading = signal(true);
  protected saving = signal(false);
  protected tasks = signal<Task[]>([]);
  protected products = signal<ProductView[]>([]);
  protected users = signal<UserProfile[]>([]);

  protected productFilter = signal('');
  protected onlyMine = signal(false);

  protected formOpen = signal(false);
  protected editing = signal<Task | null>(null);
  protected draft: Partial<Task> = {};

  protected dragged = signal<Task | null>(null);
  protected dragOver = signal<TaskStatus | null>(null);

  protected lbl = label;
  protected readonly priorities: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  protected readonly columns: { status: TaskStatus; label: string }[] = [
    { status: 'TODO', label: 'À faire' },
    { status: 'IN_PROGRESS', label: 'En cours' },
    { status: 'IN_REVIEW', label: 'En revue' },
    { status: 'DONE', label: 'Terminé' }
  ];

  protected filtered = computed(() => {
    const productId = this.productFilter();
    const mine = this.onlyMine();
    const me = this.auth.currentUser()?.id;
    return this.tasks().filter(t => {
      if (productId && t.productId !== productId) return false;
      if (mine && t.assigneeId !== me) return false;
      return true;
    });
  });

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
    this.tasksApi.list().subscribe({
      next: list => {
        this.tasks.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  protected inColumn(status: TaskStatus): Task[] {
    return this.filtered().filter(t => t.status === status);
  }

  protected countByStatus(status: TaskStatus): number {
    return this.tasks().filter(t => t.status === status).length;
  }

  protected overdueCount(): number {
    return this.tasks().filter(t => t.overdue).length;
  }

  protected referenceOf(productId?: string): string {
    return this.products().find(p => p.id === productId)?.reference ?? '';
  }

  protected nameOf(userId?: string): string {
    return this.users().find(u => u.id === userId)?.fullName ?? '';
  }

  // --------------------------------------------------------- drag & drop

  protected onDragOver(event: DragEvent, status: TaskStatus) {
    // Without preventDefault the browser refuses the drop.
    event.preventDefault();
    this.dragOver.set(status);
  }

  protected onDrop(status: TaskStatus) {
    const task = this.dragged();
    this.dragOver.set(null);
    this.dragged.set(null);
    if (!task || task.status === status) return;

    // Optimistic move, rolled back by the reload if the server refuses.
    this.tasks.update(list => list.map(t => (t.id === task.id ? { ...t, status } : t)));
    this.tasksApi.changeStatus(task.id, status).subscribe({
      next: () => this.toast.success('Tâche déplacée', `« ${task.title} » → ${label(status)}`),
      error: () => {
        this.toast.error('Déplacement refusé');
        this.load();
      }
    });
  }

  // ------------------------------------------------------------- editing

  protected openCreate() {
    this.editing.set(null);
    this.draft = { priority: 'MEDIUM', status: 'TODO' };
    this.formOpen.set(true);
  }

  protected openEdit(task: Task) {
    this.editing.set(task);
    this.draft = { ...task };
    this.formOpen.set(true);
  }

  protected save() {
    if (!this.draft.title?.trim()) {
      this.toast.warning('Titre obligatoire');
      return;
    }
    this.saving.set(true);

    const current = this.editing();
    const request$ = current
      ? this.tasksApi.update(current.id, this.draft)
      : this.tasksApi.create(this.draft);

    request$.subscribe({
      next: () => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.toast.success(current ? 'Tâche mise à jour' : 'Tâche créée');
        this.load();
      },
      error: err => {
        this.saving.set(false);
        this.toast.error('Enregistrement impossible', err.error?.message);
      }
    });
  }

  protected remove() {
    const current = this.editing();
    if (!current) return;
    this.tasksApi.remove(current.id).subscribe({
      next: () => {
        this.formOpen.set(false);
        this.toast.success('Tâche supprimée');
        this.load();
      },
      error: err => this.toast.error('Suppression impossible', err.error?.message)
    });
  }
}
