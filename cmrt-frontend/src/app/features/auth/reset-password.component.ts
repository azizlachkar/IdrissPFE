import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <section class="auth-hero">
        <h1>Nouveau mot de passe</h1>
        <p>Choisissez un mot de passe d'au moins 12 caractères comportant une lettre, un chiffre et un symbole.</p>
      </section>

      <section class="auth-form-side">
        <div class="auth-card card">
          <div class="card-body">
            <h2 class="mb-3">Définir un nouveau mot de passe</h2>

            <form [formGroup]="form" (ngSubmit)="submit()">
              <div class="field">
                <label>Nouveau mot de passe <span class="req">*</span></label>
                <input type="password" class="input" formControlName="newPassword">
              </div>
              <div class="field">
                <label>Confirmation <span class="req">*</span></label>
                <input type="password" class="input" formControlName="confirm">
                <div class="field-error" *ngIf="mismatch()">Les deux mots de passe ne correspondent pas.</div>
              </div>

              <div class="field-error mb-2" *ngIf="error()">{{ error() }}</div>

              <button type="submit" class="btn btn-primary btn-block" [disabled]="loading()">
                {{ loading() ? 'Enregistrement…' : 'Valider' }}
              </button>
            </form>

            <p class="small mt-2 center"><a routerLink="/login">Retour à la connexion</a></p>
          </div>
        </div>
      </section>
    </div>
  `
})
export class ResetPasswordComponent implements OnInit {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private toast = inject(ToastService);

  protected loading = signal(false);
  protected error = signal('');
  private token = '';

  protected form = this.fb.nonNullable.group({
    newPassword: ['', [Validators.required, Validators.minLength(12)]],
    confirm: ['', Validators.required]
  });

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token') ?? '';
    if (!this.token) {
      this.error.set('Lien de réinitialisation incomplet.');
    }
  }

  protected mismatch(): boolean {
    const { newPassword, confirm } = this.form.getRawValue();
    return !!confirm && newPassword !== confirm;
  }

  protected submit() {
    if (this.form.invalid || this.mismatch() || !this.token) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set('');

    this.auth.resetPassword(this.token, this.form.getRawValue().newPassword).subscribe({
      next: () => {
        this.loading.set(false);
        this.toast.success('Mot de passe modifié', 'Vous pouvez maintenant vous connecter.');
        this.router.navigate(['/login']);
      },
      error: err => {
        this.loading.set(false);
        this.error.set(err.error?.message ?? 'Impossible de réinitialiser le mot de passe.');
      }
    });
  }
}
