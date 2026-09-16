import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { label } from '../../core/labels';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <section class="auth-hero">
        <h1>Rejoindre la plateforme</h1>
        <p>
          Créez votre compte pour suivre les projets d'industrialisation, remonter les
          blocages et réserver les moyens de test.
        </p>
        <ul>
          <li>①<span> Renseignez votre identité et votre rattachement.</span></li>
          <li>②<span> Un email d'activation vous est envoyé.</span></li>
          <li>③<span> Un administrateur confirme votre rôle et vos accès.</span></li>
        </ul>
      </section>

      <section class="auth-form-side">
        <div class="auth-card card" style="max-width:520px">
          <div class="card-body">
            <h2 class="mb-1">Créer un compte</h2>
            <p class="secondary-text small mb-3">Tous les champs marqués d'une étoile sont obligatoires.</p>

            <form [formGroup]="form" (ngSubmit)="submit()">
              <div class="form-row">
                <div class="field">
                  <label>Prénom <span class="req">*</span></label>
                  <input class="input" formControlName="prenom" [class.error]="invalid('prenom')">
                </div>
                <div class="field">
                  <label>Nom <span class="req">*</span></label>
                  <input class="input" formControlName="nom" [class.error]="invalid('nom')">
                </div>
              </div>

              <div class="form-row">
                <div class="field">
                  <label>Matricule</label>
                  <input class="input" formControlName="matricule" placeholder="MET001">
                </div>
                <div class="field">
                  <label>Téléphone</label>
                  <input class="input" formControlName="telephone" placeholder="+216 ...">
                </div>
              </div>

              <div class="field">
                <label>Adresse email <span class="req">*</span></label>
                <input type="email" class="input" formControlName="email" [class.error]="invalid('email')"
                       placeholder="prenom.nom@cmrt.tn">
                <div class="field-error" *ngIf="invalid('email')">Adresse email invalide.</div>
              </div>

              <div class="form-row">
                <div class="field">
                  <label>Département</label>
                  <select class="select" formControlName="departement">
                    <option *ngFor="let d of departements" [value]="d">{{ lbl(d) }}</option>
                  </select>
                </div>
                <div class="field">
                  <label>Service</label>
                  <select class="select" formControlName="serviceUnit">
                    <option *ngFor="let s of services" [value]="s">{{ lbl(s) }}</option>
                  </select>
                </div>
              </div>

              <div class="form-row">
                <div class="field">
                  <label>Poste</label>
                  <select class="select" formControlName="poste">
                    <option *ngFor="let p of postes" [value]="p">{{ lbl(p) }}</option>
                  </select>
                </div>
                <div class="field">
                  <label>Rôle demandé</label>
                  <select class="select" formControlName="role">
                    <option *ngFor="let r of roles" [value]="r">{{ lbl(r) }}</option>
                  </select>
                  <div class="field-hint">Confirmé par un administrateur.</div>
                </div>
              </div>

              <div class="field">
                <label>Mot de passe <span class="req">*</span></label>
                <input type="password" class="input" formControlName="password" [class.error]="invalid('password')">
                <div class="field-hint">
                  12 caractères minimum, avec au moins une lettre, un chiffre et un symbole.
                </div>
              </div>

              <div class="field-error mb-2" *ngIf="error()">{{ error() }}</div>

              <button type="submit" class="btn btn-primary btn-block" [disabled]="loading()">
                {{ loading() ? 'Création…' : 'Créer mon compte' }}
              </button>
            </form>

            <p class="small mt-2 center">
              Déjà inscrit ? <a routerLink="/login">Se connecter</a>
            </p>
          </div>
        </div>
      </section>
    </div>
  `
})
export class SignupComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  private toast = inject(ToastService);

  protected loading = signal(false);
  protected error = signal('');

  protected readonly departements = ['ENGINEERING', 'PRODUCTION', 'QHSE', 'SUPPLY_CHAIN', 'MAINTENANCE'];
  protected readonly services = ['METHODE', 'NPI', 'CONTROLE_TECHNIQUE', 'QUALITE', 'PRODUCTION', 'SUPPLY_CHAIN', 'MAINTENANCE'];
  protected readonly postes = ['INGENIEUR', 'TECHNICIEN', 'CHEF_PROJET', 'RESPONSABLE', 'SUPERVISEUR', 'MONITRICE', 'OPERATEUR', 'DIRECTEUR'];
  // ADMIN is deliberately absent: it is granted by an administrator, never requested.
  protected readonly roles = ['METHODISTE', 'QUALITICIEN', 'TECHNICIEN', 'CHEF_PROJET', 'CONTROLE_TECHNIQUE', 'RESPONSABLE_PRODUCTION', 'VIEWER'];

  protected form = this.fb.nonNullable.group({
    prenom: ['', Validators.required],
    nom: ['', Validators.required],
    matricule: [''],
    telephone: [''],
    email: ['', [Validators.required, Validators.email]],
    departement: ['ENGINEERING'],
    serviceUnit: ['METHODE'],
    poste: ['INGENIEUR'],
    role: ['VIEWER'],
    password: ['', [Validators.required, Validators.minLength(12)]]
  });

  protected lbl = label;

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

    this.auth.signup(this.form.getRawValue()).subscribe({
      next: () => {
        this.loading.set(false);
        this.toast.success('Compte créé', "Vérifiez votre boîte mail pour activer votre compte.");
        this.router.navigate(['/login']);
      },
      error: err => {
        this.loading.set(false);
        this.error.set(err.error?.message ?? 'Impossible de créer le compte.');
      }
    });
  }
}
