import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="auth-page">
      <section class="auth-hero">
        <h1>Activation du compte</h1>
        <p>Un clic et votre accès à la plateforme Engineering est ouvert.</p>
      </section>

      <section class="auth-form-side">
        <div class="auth-card card">
          <div class="card-body center">
            <div *ngIf="state() === 'pending'">
              <div class="spinner"></div>
              <p class="secondary-text">Vérification du lien en cours…</p>
            </div>

            <div *ngIf="state() === 'ok'">
              <div style="font-size:2.6rem">✅</div>
              <h2 class="mt-2 mb-1">Compte activé</h2>
              <p class="secondary-text small mb-3">{{ message() }}</p>
              <a routerLink="/login" class="btn btn-primary btn-block">Se connecter</a>
            </div>

            <div *ngIf="state() === 'error'">
              <div style="font-size:2.6rem">⛔</div>
              <h2 class="mt-2 mb-1">Lien invalide</h2>
              <p class="secondary-text small mb-3">{{ message() }}</p>
              <a routerLink="/login" class="btn btn-ghost btn-block">Retour à la connexion</a>
            </div>
          </div>
        </div>
      </section>
    </div>
  `
})
export class VerifyEmailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private auth = inject(AuthService);

  protected state = signal<'pending' | 'ok' | 'error'>('pending');
  protected message = signal('');

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.state.set('error');
      this.message.set('Aucun jeton de vérification fourni.');
      return;
    }

    this.auth.verifyEmail(token).subscribe({
      next: res => {
        this.state.set('ok');
        this.message.set(res.message ?? 'Votre compte est désormais actif.');
      },
      error: err => {
        this.state.set('error');
        this.message.set(err.error?.message ?? 'Ce lien est invalide ou a déjà été utilisé.');
      }
    });
  }
}
