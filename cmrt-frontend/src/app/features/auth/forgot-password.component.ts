import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <section class="auth-hero">
        <h1>Mot de passe oublié</h1>
        <p>Indiquez votre adresse professionnelle : un lien de réinitialisation valable 30 minutes vous sera envoyé.</p>
      </section>

      <section class="auth-form-side">
        <div class="auth-card card">
          <div class="card-body">
            <h2 class="mb-3">Réinitialiser mon mot de passe</h2>

            <form [formGroup]="form" (ngSubmit)="submit()" *ngIf="!sent()">
              <div class="field">
                <label>Adresse email <span class="req">*</span></label>
                <input type="email" class="input" formControlName="email" placeholder="prenom.nom@cmrt.tn">
              </div>
              <button type="submit" class="btn btn-primary btn-block" [disabled]="loading() || form.invalid">
                {{ loading() ? 'Envoi…' : 'Envoyer le lien' }}
              </button>
            </form>

            <div *ngIf="sent()" class="seed-hint">{{ message() }}</div>

            <p class="small mt-2 center"><a routerLink="/login">Retour à la connexion</a></p>
          </div>
        </div>
      </section>
    </div>
  `
})
export class ForgotPasswordComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);

  protected loading = signal(false);
  protected sent = signal(false);
  protected message = signal('');

  protected form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]]
  });

  protected submit() {
    if (this.form.invalid) return;
    this.loading.set(true);

    this.auth.forgotPassword(this.form.getRawValue().email).subscribe({
      next: res => {
        this.loading.set(false);
        this.sent.set(true);
        this.message.set(res.message);
      },
      // The API answers identically for unknown addresses, so a failure here is a
      // transport problem rather than "no such account".
      error: () => {
        this.loading.set(false);
        this.sent.set(true);
        this.message.set("Si un compte existe pour cette adresse, un lien vient d'être envoyé.");
      }
    });
  }
}
