import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { label } from '../../core/labels';
import { ProductView } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { ProductService } from '../../core/services/product.service';
import { ToastService } from '../../core/services/toast.service';
import { UserService } from '../../core/services/user.service';
import { LabelPipe } from '../../shared/pipes';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, LabelPipe],
  template: `
    <div class="page-head">
      <div>
        <h1>👤 Mon profil</h1>
        <p>Vos informations, votre rattachement et votre mot de passe.</p>
      </div>
    </div>

    <div class="grid grid-2">
      <div class="card">
        <div class="card-body">
          <div class="flex items-center gap-3 mb-3">
            <span class="avatar lg">{{ user()?.initials }}</span>
            <div>
              <h2>{{ user()?.fullName }}</h2>
              <div class="flex gap-1 wrap mt-1">
                <span class="badge info">{{ user()?.role | label }}</span>
                <span class="badge">{{ user()?.departement | label }}</span>
                <span class="badge">{{ user()?.serviceUnit | label }}</span>
              </div>
            </div>
          </div>

          <form [formGroup]="profileForm" (ngSubmit)="saveProfile()">
            <div class="form-row">
              <div class="field">
                <label>Prénom</label>
                <input class="input" formControlName="prenom">
              </div>
              <div class="field">
                <label>Nom</label>
                <input class="input" formControlName="nom">
              </div>
            </div>
            <div class="form-row">
              <div class="field">
                <label>Matricule</label>
                <input class="input" formControlName="matricule">
              </div>
              <div class="field">
                <label>Téléphone</label>
                <input class="input" formControlName="telephone">
              </div>
            </div>
            <div class="field">
              <label>Adresse email</label>
              <input class="input" [value]="user()?.email" disabled>
              <div class="field-hint">L'adresse email ne peut pas être modifiée.</div>
            </div>

            <button type="submit" class="btn btn-primary" [disabled]="savingProfile()">
              {{ savingProfile() ? 'Enregistrement…' : 'Enregistrer mes informations' }}
            </button>
          </form>
        </div>
      </div>

      <div class="flex-col gap-3">
        <div class="card">
          <div class="card-head"><h3>Changer mon mot de passe</h3></div>
          <div class="card-body">
            <form [formGroup]="passwordForm" (ngSubmit)="changePassword()">
              <div class="field">
                <label>Mot de passe actuel <span class="req">*</span></label>
                <input type="password" class="input" formControlName="currentPassword">
              </div>
              <div class="field">
                <label>Nouveau mot de passe <span class="req">*</span></label>
                <input type="password" class="input" formControlName="newPassword">
                <div class="field-hint">12 caractères minimum, avec une lettre, un chiffre et un symbole.</div>
              </div>
              <div class="field">
                <label>Confirmation <span class="req">*</span></label>
                <input type="password" class="input" formControlName="confirm">
                <div class="field-error" *ngIf="mismatch()">Les deux mots de passe ne correspondent pas.</div>
              </div>

              <button type="submit" class="btn btn-primary" [disabled]="savingPassword()">
                {{ savingPassword() ? 'Modification…' : 'Modifier le mot de passe' }}
              </button>
            </form>
          </div>
        </div>

        <div class="card">
          <div class="card-head">
            <h3>Mes produits</h3>
            <span class="badge info">{{ myProducts().length }}</span>
          </div>
          <div class="card-body flush">
            <table class="table" *ngIf="myProducts().length; else noProducts">
              <tbody>
                <tr *ngFor="let p of myProducts()">
                  <td>
                    <div class="bold">{{ p.reference }}</div>
                    <div class="tiny muted truncate" style="max-width:230px">{{ p.nom }}</div>
                  </td>
                  <td class="right">
                    <span class="badge">{{ p.progressPercent }}%</span>
                  </td>
                </tr>
              </tbody>
            </table>
            <ng-template #noProducts>
              <div class="empty tiny" style="padding:1.2rem">Aucun produit ne vous est affecté.</div>
            </ng-template>
          </div>
        </div>
      </div>
    </div>
  `
})
export class ProfileComponent implements OnInit {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private users = inject(UserService);
  private products = inject(ProductService);
  private toast = inject(ToastService);

  protected readonly user = this.auth.currentUser;
  protected savingProfile = signal(false);
  protected savingPassword = signal(false);
  protected myProducts = signal<ProductView[]>([]);
  protected lbl = label;

  protected profileForm = this.fb.nonNullable.group({
    prenom: [''],
    nom: [''],
    matricule: [''],
    telephone: ['']
  });

  protected passwordForm = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(12)]],
    confirm: ['', Validators.required]
  });

  ngOnInit(): void {
    const current = this.user();
    if (current) {
      this.profileForm.patchValue({
        prenom: current.prenom ?? '',
        nom: current.nom ?? '',
        matricule: current.matricule ?? '',
        telephone: current.telephone ?? ''
      });
    }
    this.products.mine().subscribe({ next: list => this.myProducts.set(list) });
  }

  protected mismatch(): boolean {
    const { newPassword, confirm } = this.passwordForm.getRawValue();
    return !!confirm && newPassword !== confirm;
  }

  protected saveProfile() {
    const current = this.user();
    if (!current) return;
    this.savingProfile.set(true);

    this.users.update(current.id, this.profileForm.getRawValue()).subscribe({
      next: () => {
        // Pull the profile back so the shell header shows the new name immediately.
        this.auth.refreshProfile().subscribe();
        this.savingProfile.set(false);
        this.toast.success('Profil mis à jour');
      },
      error: err => {
        this.savingProfile.set(false);
        this.toast.error('Enregistrement impossible', err.error?.message);
      }
    });
  }

  protected changePassword() {
    if (this.passwordForm.invalid || this.mismatch()) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    this.savingPassword.set(true);
    const { currentPassword, newPassword } = this.passwordForm.getRawValue();

    this.auth.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.savingPassword.set(false);
        this.passwordForm.reset();
        this.toast.success('Mot de passe modifié');
      },
      error: err => {
        this.savingPassword.set(false);
        this.toast.error('Modification impossible', err.error?.message);
      }
    });
  }
}
