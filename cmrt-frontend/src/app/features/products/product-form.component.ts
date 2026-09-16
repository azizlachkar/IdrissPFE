import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { label } from '../../core/labels';
import {
  Customer, Priority, ProductFamily, ProductStatus, ProductView, ProjectType, UserProfile
} from '../../core/models';
import { ProductService } from '../../core/services/product.service';
import { ToastService } from '../../core/services/toast.service';
import { UserService } from '../../core/services/user.service';
import { ModalComponent } from '../../shared/ui';

/**
 * Create or edit a product. On creation the backend immediately lays out the whole
 * pipeline from the start date, which is why that field is required here.
 */
@Component({
  selector: 'app-product-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ModalComponent],
  template: `
    <app-modal [title]="product ? 'Modifier ' + product.reference : 'Nouveau produit'"
               [wide]="true" (closed)="closed.emit()">
      <form [formGroup]="form" id="product-form" (ngSubmit)="submit()">
        <div class="form-row">
          <div class="field">
            <label>Référence <span class="req">*</span></label>
            <input class="input" formControlName="reference" placeholder="FA-CUM-1042" style="text-transform:uppercase">
            <div class="field-error" *ngIf="invalid('reference')">La référence est obligatoire.</div>
          </div>
          <div class="field">
            <label>Désignation <span class="req">*</span></label>
            <input class="input" formControlName="nom" placeholder="Faisceau moteur Cummins QSK60">
            <div class="field-error" *ngIf="invalid('nom')">La désignation est obligatoire.</div>
          </div>
        </div>

        <div class="form-row">
          <div class="field">
            <label>Famille <span class="req">*</span></label>
            <select class="select" formControlName="family">
              <option *ngFor="let f of families" [value]="f">{{ lbl(f) }}</option>
            </select>
          </div>
          <div class="field">
            <label>Client <span class="req">*</span></label>
            <select class="select" formControlName="customer">
              <option *ngFor="let c of customers" [value]="c">{{ lbl(c) }}</option>
            </select>
          </div>
          <div class="field">
            <label>Type de projet <span class="req">*</span></label>
            <select class="select" formControlName="projectType">
              <option *ngFor="let t of projectTypes" [value]="t">{{ lbl(t) }}</option>
            </select>
            <div class="field-hint">NPI déroule les 10 jalons ; Production en déroule 8.</div>
          </div>
        </div>

        <div class="form-row">
          <div class="field">
            <label>Statut</label>
            <select class="select" formControlName="status">
              <option *ngFor="let s of statuses" [value]="s">{{ lbl(s) }}</option>
            </select>
          </div>
          <div class="field">
            <label>Priorité</label>
            <select class="select" formControlName="priority">
              <option *ngFor="let p of priorities" [value]="p">{{ lbl(p) }}</option>
            </select>
          </div>
          <div class="field">
            <label>Programme</label>
            <input class="input" formControlName="programme" placeholder="QSK60 Tier 4">
          </div>
        </div>

        <div class="form-row">
          <div class="field">
            <label>Chef de projet</label>
            <select class="select" formControlName="chefProjetId">
              <option [ngValue]="null">— Non affecté —</option>
              <option *ngFor="let u of byRole('CHEF_PROJET')" [ngValue]="u.id">{{ u.fullName }}</option>
            </select>
          </div>
          <div class="field">
            <label>Méthodiste</label>
            <select class="select" formControlName="methodisteId">
              <option [ngValue]="null">— Non affecté —</option>
              <option *ngFor="let u of byRole('METHODISTE')" [ngValue]="u.id">{{ u.fullName }}</option>
            </select>
          </div>
          <div class="field">
            <label>Qualiticien</label>
            <select class="select" formControlName="qualiticienId">
              <option [ngValue]="null">— Non affecté —</option>
              <option *ngFor="let u of byRole('QUALITICIEN')" [ngValue]="u.id">{{ u.fullName }}</option>
            </select>
          </div>
        </div>

        <div class="form-row">
          <div class="field">
            <label>Date de lancement <span class="req">*</span></label>
            <input type="date" class="input" formControlName="startDate">
            <div class="field-hint">Point de départ du planning des jalons.</div>
          </div>
          <div class="field">
            <label>SOP cible</label>
            <input type="date" class="input" formControlName="targetSopDate">
            <div class="field-hint">Démarrage série contractuel.</div>
          </div>
          <div class="field">
            <label>Volume annuel</label>
            <input type="number" class="input" formControlName="annualVolume" placeholder="12000">
          </div>
        </div>

        <div class="field">
          <label>Description</label>
          <textarea class="textarea" formControlName="description"
                    placeholder="Contexte, particularités techniques, exigences client…"></textarea>
        </div>
      </form>

      <ng-container modal-actions>
        <button type="button" class="btn btn-ghost" (click)="closed.emit()">Annuler</button>
        <button type="submit" form="product-form" class="btn btn-primary" [disabled]="saving()">
          {{ saving() ? 'Enregistrement…' : (product ? 'Enregistrer' : 'Créer et générer le pipeline') }}
        </button>
      </ng-container>
    </app-modal>
  `
})
export class ProductFormComponent implements OnInit {
  @Input() product?: ProductView | null;
  /** Pre-selects the board the form was opened from. */
  @Input() defaultProjectType: ProjectType = 'NPI';
  @Output() closed = new EventEmitter<void>();
  @Output() saved = new EventEmitter<void>();

  private fb = inject(FormBuilder);
  private products = inject(ProductService);
  private users = inject(UserService);
  private toast = inject(ToastService);

  protected saving = signal(false);
  protected directory = signal<Record<string, UserProfile[]>>({});
  protected lbl = label;

  protected readonly families: ProductFamily[] = ['FAISCEAU', 'PIPE', 'CABLE', 'SOUS_ENSEMBLE'];
  protected readonly customers: Customer[] = ['CUMMINS', 'WAUKESHA', 'WABTEC', 'AUTRE'];
  protected readonly projectTypes: ProjectType[] = ['NPI', 'PRODUCTION'];
  protected readonly statuses: ProductStatus[] = ['DRAFT', 'IN_DEVELOPMENT', 'PILOT', 'MASS_PRODUCTION', 'ON_HOLD'];
  protected readonly priorities: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

  protected form = this.fb.group({
    reference: ['', Validators.required],
    nom: ['', Validators.required],
    description: [''],
    family: ['FAISCEAU' as ProductFamily, Validators.required],
    customer: ['CUMMINS' as Customer, Validators.required],
    projectType: ['NPI' as ProjectType, Validators.required],
    status: ['DRAFT' as ProductStatus],
    priority: ['MEDIUM' as Priority],
    chefProjetId: [null as string | null],
    methodisteId: [null as string | null],
    qualiticienId: [null as string | null],
    startDate: [new Date().toISOString().slice(0, 10), Validators.required],
    targetSopDate: [null as string | null],
    annualVolume: [null as number | null],
    programme: ['']
  });

  ngOnInit(): void {
    this.users.directory().subscribe({
      next: dir => this.directory.set(dir),
      error: () => this.directory.set({})
    });

    if (this.product) {
      this.form.patchValue({
        reference: this.product.reference,
        nom: this.product.nom,
        description: this.product.description ?? '',
        family: this.product.family,
        customer: this.product.customer,
        projectType: this.product.projectType,
        status: this.product.status,
        priority: this.product.priority,
        chefProjetId: this.product.chefProjetId ?? null,
        methodisteId: this.product.methodisteId ?? null,
        qualiticienId: this.product.qualiticienId ?? null,
        startDate: this.product.startDate ?? new Date().toISOString().slice(0, 10),
        targetSopDate: this.product.targetSopDate ?? null,
        annualVolume: this.product.annualVolume ?? null,
        programme: this.product.programme ?? ''
      });
    } else {
      this.form.patchValue({ projectType: this.defaultProjectType });
    }
  }

  protected byRole(role: string): UserProfile[] {
    return this.directory()[role] ?? [];
  }

  protected invalid(control: string): boolean {
    const field = this.form.get(control);
    return !!field && field.invalid && field.touched;
  }

  protected submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);

    const payload = { ...this.form.getRawValue(), reference: this.form.getRawValue().reference!.toUpperCase() };
    const request$ = this.product
      ? this.products.update(this.product.id, payload as never)
      : this.products.create(payload as never);

    request$.subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(
          this.product ? 'Produit mis à jour' : 'Produit créé',
          this.product ? '' : 'Le pipeline d\'industrialisation a été généré.'
        );
        this.saved.emit();
      },
      error: err => {
        this.saving.set(false);
        this.toast.error('Enregistrement impossible', err.error?.message ?? 'Veuillez réessayer.');
      }
    });
  }
}
