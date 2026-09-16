import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/services/auth.service';
import { NotificationService } from '../core/services/notification.service';
import { ToastService } from '../core/services/toast.service';
import { LabelPipe, TimeAgoPipe } from '../shared/pipes';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  /** Empty means every signed-in role may see it. */
  roles?: string[];
}

interface NavSection {
  title: string;
  items: NavItem[];
}

/**
 * Application frame: role-filtered sidebar, top bar with the notification inbox,
 * and the toast stack. Every authenticated route renders inside its outlet.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, LabelPipe, TimeAgoPipe],
  template: `
    <div class="app-shell">
      <aside class="sidebar" [class.open]="menuOpen()">
        <div class="sidebar-brand">
          <div class="brand-mark">CMRT</div>
          <div class="brand-text">
            <strong>Engineering</strong>
            <span>Plateforme PFE</span>
          </div>
        </div>

        <nav class="sidebar-nav">
          <div class="nav-section" *ngFor="let section of visibleSections()">
            <div class="nav-section-title">{{ section.title }}</div>
            <a *ngFor="let item of section.items"
               [routerLink]="item.path"
               routerLinkActive="active"
               (click)="menuOpen.set(false)"
               class="nav-link">
              <span class="nav-icon">{{ item.icon }}</span>
              <span>{{ item.label }}</span>
              <span class="nav-count alert" *ngIf="item.path === '/notifications' && unread() > 0">{{ unread() }}</span>
            </a>
          </div>
        </nav>

        <div class="sidebar-footer">
          CMRT &middot; Contrôle, Mesure et Régulation Tunisie
        </div>
      </aside>

      <div class="main-area">
        <header class="topbar">
          <button class="icon-btn menu-toggle" (click)="menuOpen.set(!menuOpen())" aria-label="Menu">☰</button>

          <div>
            <div class="topbar-title">{{ pageTitle() }}</div>
            <div class="topbar-sub">{{ today }}</div>
          </div>

          <div class="topbar-spacer"></div>

          <div style="position:relative">
            <button class="icon-btn" (click)="toggleInbox()" aria-label="Notifications">
              🔔
              <span class="dot" *ngIf="unread() > 0">{{ unread() > 99 ? '99+' : unread() }}</span>
            </button>

            <div class="inbox card" *ngIf="inboxOpen()">
              <div class="card-head">
                <h3>Notifications</h3>
                <button class="btn btn-ghost btn-sm" (click)="markAllRead()" *ngIf="unread() > 0">
                  Tout marquer lu
                </button>
              </div>
              <div class="inbox-list">
                <div class="notif-item" *ngFor="let n of latest()"
                     [class.unread]="!n.read"
                     (click)="openNotification(n)">
                  <span class="badge" [ngClass]="n.severity === 'CRITICAL' || n.severity === 'BLOCKING' ? 'danger' : 'info'">
                    {{ n.severity | label }}
                  </span>
                  <div class="txt">
                    <strong>{{ n.title }}</strong>
                    <p>{{ n.message }}</p>
                    <span class="when">{{ n.createdAt | timeAgo }}</span>
                  </div>
                </div>
                <div class="empty" *ngIf="latest().length === 0">
                  <span class="icon">🔕</span>
                  <p>Aucune notification</p>
                </div>
              </div>
              <div class="card-head" style="border-top:1px solid var(--border);border-bottom:none">
                <a routerLink="/notifications" (click)="inboxOpen.set(false)" class="small">Voir toutes les notifications →</a>
              </div>
            </div>
          </div>

          <a routerLink="/profil" class="user-chip">
            <span class="avatar">{{ user()?.initials }}</span>
            <span class="user-chip-meta">
              <strong>{{ user()?.fullName }}</strong>
              <span>{{ user()?.role | label }}</span>
            </span>
          </a>

          <button class="icon-btn" (click)="logout()" title="Déconnexion" aria-label="Déconnexion">⏻</button>
        </header>

        <main class="page fade-in">
          <router-outlet></router-outlet>
        </main>
      </div>
    </div>

    <div class="toast-stack">
      <div class="toast" *ngFor="let t of toasts.toasts()" [ngClass]="t.kind" (click)="toasts.dismiss(t.id)">
        <span>{{ icons[t.kind] }}</span>
        <div>
          <strong>{{ t.title }}</strong>
          <p *ngIf="t.message">{{ t.message }}</p>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .inbox {
      position: absolute; right: 0; top: calc(100% + .5rem);
      width: 372px; max-width: 88vw;
      box-shadow: var(--shadow-lg);
      z-index: 60;
    }
    .inbox-list { max-height: 380px; overflow-y: auto; }

    /* The sidebar is permanent on desktop, so the toggle only exists on narrow screens. */
    .menu-toggle { display: none; }
    @media (max-width: 1024px) { .menu-toggle { display: grid; } }
  `]
})
export class ShellComponent implements OnInit, OnDestroy {
  private auth = inject(AuthService);
  private notifications = inject(NotificationService);
  private router = inject(Router);
  protected toasts = inject(ToastService);

  protected readonly user = this.auth.currentUser;
  protected readonly unread = this.notifications.unreadCount;
  protected readonly menuOpen = signal(false);
  protected readonly inboxOpen = signal(false);

  protected readonly today = new Date().toLocaleDateString('fr-FR', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
  });

  protected readonly icons: Record<string, string> = {
    success: '✅', error: '⛔', warning: '⚠️', info: 'ℹ️'
  };

  private readonly sections: NavSection[] = [
    {
      title: 'Pilotage',
      items: [
        { path: '/dashboard', label: 'Tableau de bord', icon: '▦' },
        { path: '/mes-projets', label: 'Mes projets', icon: '★' }
      ]
    },
    {
      title: 'Industrialisation',
      items: [
        { path: '/production', label: 'Méthode Production', icon: '⚙' },
        { path: '/npi', label: 'Méthode NPI', icon: '◈' },
        { path: '/control', label: 'Contrôle technique', icon: '⚡' }
      ]
    },
    {
      title: 'Suivi',
      items: [
        { path: '/taches', label: 'Tâches', icon: '☑' },
        { path: '/blocages', label: 'Blocages', icon: '⚠' },
        { path: '/modifications', label: 'Modifications', icon: '⇄' },
        { path: '/documents', label: 'Documents', icon: '🗂' }
      ]
    },
    {
      title: 'Compte',
      items: [
        { path: '/notifications', label: 'Notifications', icon: '🔔' },
        { path: '/profil', label: 'Mon profil', icon: '👤' },
        { path: '/admin', label: 'Administration', icon: '🛡', roles: ['ADMIN'] }
      ]
    }
  ];

  /** Sections with their role-restricted entries removed, empty ones dropped. */
  protected readonly visibleSections = computed<NavSection[]>(() => {
    // Read the signal so the menu recomputes when the profile changes.
    this.auth.currentUser();
    return this.sections
      .map(section => ({
        title: section.title,
        items: section.items.filter(item => !item.roles || this.auth.hasRole(...(item.roles as never[])))
      }))
      .filter(section => section.items.length > 0);
  });

  protected readonly latest = computed(() => this.notifications.items().slice(0, 8));

  protected readonly pageTitle = signal('Tableau de bord');

  ngOnInit(): void {
    this.notifications.startPolling();
    this.updateTitle(this.router.url);
    this.router.events.subscribe(() => this.updateTitle(this.router.url));
  }

  ngOnDestroy(): void {
    this.notifications.stopPolling();
  }

  protected toggleInbox() {
    const opening = !this.inboxOpen();
    this.inboxOpen.set(opening);
    if (opening) {
      this.notifications.list().subscribe();
    }
  }

  protected markAllRead() {
    this.notifications.markAllRead().subscribe();
  }

  protected openNotification(n: { id: string; read: boolean; link?: string }) {
    if (!n.read) {
      this.notifications.markRead(n.id).subscribe();
    }
    this.inboxOpen.set(false);
    if (n.link) {
      this.router.navigateByUrl(n.link);
    }
  }

  protected logout() {
    this.notifications.stopPolling();
    this.auth.logout();
  }

  private updateTitle(url: string) {
    const path = url.split('?')[0];
    const match = this.sections
      .flatMap(s => s.items)
      .find(item => path.startsWith(item.path));
    if (match) {
      this.pageTitle.set(match.label);
    } else if (path.startsWith('/products/')) {
      this.pageTitle.set('Fiche produit');
    }
  }
}
