import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { label } from '../../core/labels';
import { Issue, IssueCategory, ProductView, Severity, StageType, UserProfile } from '../../core/models';
import { IssueService } from '../../core/services/issue.service';
import { ToastService } from '../../core/services/toast.service';
import { UserService } from '../../core/services/user.service';
import { ModalComponent } from '../../shared/ui';

/**
 * The "Réclamation" dialog. Reused from the product boards and from the product
 * workspace, so a blockage is always declared with the same fields wherever the
 * user happens to be.
 */
@Component({
  selector: 'app-issue-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ModalComponent],
  template: `
    <app-modal [title]="'Déclarer un blocage — ' + (product?.reference || '')" (closed)="closed.emit()">
      <form [formGroup]="form" id="issue-form" (ngSubmit)="submit()">
        <div class="field">
          <label>Objet du blocage <span class="req">*</span></label>
          <input class="input" formControlName="title"
                 placeholder="Ex. Rupture de stock sur le connecteur DT06-12S">
          <div class="field-error" *ngIf="invalid('title')">L'objet est obligatoire.</div>
        </div>

        <div class="form-row">
          <div class="field">
            <label>Catégorie <span class="req">*</span></label>
            <select class="select" formControlName="category">
              <option *ngFor="let c of categories" [value]="c">{{ lbl(c) }}</option>
            </select>
          </div>

          <div class="field">
            <label>Gravité <span class="req">*</span></label>
            <select class="select" formControlName="severity">
              <option *ngFor="let s of severities" [value]="s">{{ lbl(s) }}</option>
            </select>
            <div class="field-hint">Délai de réponse : {{ slaHint() }}</div>
          </div>
        </div>

        <div class="form-row">
          <div class="field">
            <label>Jalon concerné</label>
            <select class="select" formControlName="stageType">
              <option [ngValue]="null">— Aucun jalon précis —</option>
              <option *ngFor="let s of stages" [ngValue]="s">{{ lbl(s) }}</option>
            </select>
          </div>

          <div class="field">
            <label>Affecter à</label>
            <select class="select" formControlName="assigneeId">
              <option [ngValue]="null">— Affectation automatique —</option>
              <option *ngFor="let u of users()" [ngValue]="u.id">{{ u.fullName }} ({{ lbl(u.role) }})</option>
            </select>
          </div>
        </div>

        <div class="field">
          <label>Description détaillée</label>
          <textarea class="textarea" formControlName="description"
                    placeholder="Décrivez le constat, l'impact sur la production et ce qui a déjà été tenté."></textarea>
        </div>

        <label class="checkbox">
          <input type="checkbox" formControlName="blocksStage">
          <span>
            <b>Ce blocage gèle le jalon</b><br>
            <span class="tiny muted">
              Le jalon passe en statut « bloqué » et ne pourra pas être soumis à validation
              tant que le blocage n'est pas résolu.
            </span>
          </span>
        </label>
      </form>

      <ng-container modal-actions>
        <button type="button" class="btn btn-ghost" (click)="closed.emit()">Annuler</button>
        <button type="submit" form="issue-form" class="btn btn-danger" [disabled]="saving()">
          {{ saving() ? 'Envoi…' : '🚨 Déclarer le blocage' }}
        </button>
      </ng-container>
    </app-modal>
  `
})
export class IssueFormComponent implements OnInit {
  @Input() product?: ProductView | { id: string; reference: string; currentStage?: StageType };
  /** Pre-selects the gate when opened from a specific pipeline step. */
  @Input() stageType?: StageType | null;
  @Output() closed = new EventEmitter<void>();
  @Output() created = new EventEmitter<Issue>();

  private fb = inject(FormBuilder);
  private issues = inject(IssueService);
  private users_ = inject(UserService);
  private toast = inject(ToastService);

  protected saving = signal(false);
  protected users = signal<UserProfile[]>([]);
  protected lbl = label;

  protected readonly categories: IssueCategory[] = [
    'TECHNIQUE', 'MATIERE', 'OUTILLAGE', 'QUALITE', 'DOCUMENTATION',
    'MOYEN_DE_TEST', 'FOURNISSEUR', 'PROCESS', 'SECURITE', 'AUTRE'
  ];
  protected readonly severities: Severity[] = ['MINOR', 'MAJOR', 'CRITICAL', 'BLOCKING'];
  protected readonly stages: StageType[] = [
    'ENGINEERING_REVIEW', 'BOM_VALIDATION', 'MATERIAL_AVAILABILITY', 'TOOLING_PREPARATION',
    'TEST_BOARD_DESIGN', 'PROTOTYPE_MANUFACTURING', 'QUALITY_VALIDATION',
    'CUSTOMER_APPROVAL', 'PILOT_PRODUCTION', 'MASS_PRODUCTION_RELEASE'
  ];

  protected form = this.fb.group({
    title: ['', Validators.required],
    category: ['TECHNIQUE' as IssueCategory, Validators.required],
    severity: ['MAJOR' as Severity, Validators.required],
    stageType: [null as StageType | null],
    assigneeId: [null as string | null],
    description: [''],
    blocksStage: [false]
  });

  ngOnInit(): void {
    this.form.patchValue({
      stageType: this.stageType ?? (this.product as ProductView)?.currentStage ?? null
    });
    this.users_.directory().subscribe({
      next: dir => this.users.set(Object.values(dir).flat()),
      error: () => this.users.set([])
    });
  }

  /** Mirrors the server-side SLA table so the reporter knows what they are committing to. */
  protected slaHint(): string {
    switch (this.form.get('severity')?.value) {
      case 'BLOCKING': return '4 heures';
      case 'CRITICAL': return '8 heures';
      case 'MAJOR': return '24 heures';
      default: return '72 heures';
    }
  }

  protected invalid(control: string): boolean {
    const field = this.form.get(control);
    return !!field && field.invalid && field.touched;
  }

  protected submit() {
    if (this.form.invalid || !this.product) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);

    const raw = this.form.getRawValue();
    this.issues.create({
      productId: this.product.id,
      title: raw.title!,
      category: raw.category!,
      severity: raw.severity!,
      stageType: raw.stageType ?? undefined,
      assigneeId: raw.assigneeId ?? undefined,
      description: raw.description ?? undefined,
      blocksStage: !!raw.blocksStage
    }).subscribe({
      next: issue => {
        this.saving.set(false);
        this.toast.success(`Blocage ${issue.reference} déclaré`,
          'Les responsables du produit viennent d\'être notifiés.');
        this.created.emit(issue);
      },
      error: err => {
        this.saving.set(false);
        this.toast.error('Déclaration impossible', err.error?.message ?? 'Veuillez réessayer.');
      }
    });
  }
}
