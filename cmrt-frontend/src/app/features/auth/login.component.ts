import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <section class="auth-hero">
        <h1>Plateforme Engineering CMRT</h1>
        <p>
          Le pilotage complet de l'industrialisation : du lancement NPI jusqu'au passage
          en série, avec la production, la qualité et le contrôle technique sur le même outil.
        </p>
        <ul>
          <li>◈ <span><b>Pipeline en 10 jalons</b> — revue technique, BOM, matière, outillage, bancs de test, prototype, qualité, client, pilote, série.</span></li>
          <li>⚠ <span><b>Blocages tracés</b> — une réclamation gèle le jalon concerné et alerte les responsables.</span></li>
          <li>🗂 <span><b>Documents maîtrisés</b> — révisions A, B, C avec approbation et historique complet.</span></li>
          <li>⚡ <span><b>Bancs de test réservables</b> — créneaux, conflits détectés, notification automatique.</span></li>
        </ul>
      </section>

      <section class="auth-form-side">
        <div class="auth-card card">
          <div class="card-body">
            <h2 class="mb-1">Connexion</h2>
            <p class="secondary-text small mb-3">Accédez à votre espace de travail.</p>

            <form [formGroup]="form" (ngSubmit)="submit()">
              <div class="field">
                <label for="email">Adresse email <span class="req">*</span></label>
                <input id="email" type="email" class="input" formControlName="email"
                       [class.error]="invalid('email')" placeholder="prenom.nom@cmrt.tn" autocomplete="email">
                <div class="field-error" *ngIf="invalid('email')">Adresse email invalide.</div>
              </div>

              <div class="field">
                <label for="password">Mot de passe <span class="req">*</span></label>
                <input id="password" type="password" class="input" formControlName="password"
                       [class.error]="invalid('password')" placeholder="••••••••••••" autocomplete="current-password">
                <div class="field-error" *ngIf="invalid('password')">Le mot de passe est obligatoire.</div>
              </div>

              <div class="field-error mb-2" *ngIf="error()">{{ error() }}</div>

              <button type="submit" class="btn btn-primary btn-block" [disabled]="loading()">
                {{ loading() ? 'Connexion…' : 'Se connecter' }}
              </button>
            </form>

            <div class="flex justify-between mt-2 small">
              <a routerLink="/forgot-password">Mot de passe oublié ?</a>
              <a routerLink="/signup">Créer un compte</a>
            </div>

            <div class="seed-hint">
              <strong>Comptes de démonstration</strong><br>
              <code>admin&#64;cmrt.tn</code> · <code>chef.projet&#64;cmrt.tn</code> · <code>methodiste&#64;cmrt.tn</code> ·
              <code>qualite&#64;cmrt.tn</code> · <code>controle&#64;cmrt.tn</code><br>
              Mot de passe commun : <code>Cmrt&#64;2026!Pfe</code>
            </div>
          </div>
        </div>
      </section>
    </div>
  `
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private toast = inject(ToastService);
  private notifications = inject(NotificationService);

  protected loading = signal(false);
  protected error = signal('');

  protected form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  protected invalid(control: string): boolean {
    const field = this.form.get(control);
    return !!field && field.invalid && field.touched;
  }

  protected submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set('');

    this.auth.login(this.form.getRawValue()).subscribe({
      next: res => {
        this.loading.set(false);
        this.notifications.startPolling();
        this.toast.success(`Bienvenue ${res.user.prenom}`, 'Connexion réussie.');
        const redirect = this.route.snapshot.queryParamMap.get('redirect') ?? '/dashboard';
        this.router.navigateByUrl(redirect);
      },
      error: err => {
        this.loading.set(false);
        this.error.set(err.error?.message ?? 'Email ou mot de passe incorrect.');
      }
    });
  }
}
