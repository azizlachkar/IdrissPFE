import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AppNotification } from '../../core/models';
import { NotificationService } from '../../core/services/notification.service';
import { BadgeClassPipe, DateFrPipe, LabelPipe, TimeAgoPipe } from '../../shared/pipes';
import { EmptyComponent, LoaderComponent } from '../../shared/ui';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule, EmptyComponent, LoaderComponent, LabelPipe, BadgeClassPipe, DateFrPipe, TimeAgoPipe],
  template: `
    <div class="page-head">
      <div>
        <h1>🔔 Notifications</h1>
        <p>Validations demandées, blocages, échéances et réservations qui vous concernent.</p>
      </div>
      <div class="page-head-actions">
        <button class="btn btn-ghost" (click)="load()">↻ Actualiser</button>
        <button class="btn btn-primary" [disabled]="!unreadCount()" (click)="markAllRead()">
          Tout marquer comme lu
        </button>
      </div>
    </div>

    <div class="filters">
      <button class="btn" [ngClass]="filter() === 'all' ? 'btn-primary' : 'btn-ghost'"
              (click)="filter.set('all')">Toutes ({{ items().length }})</button>
      <button class="btn" [ngClass]="filter() === 'unread' ? 'btn-primary' : 'btn-ghost'"
              (click)="filter.set('unread')">Non lues ({{ unreadCount() }})</button>
    </div>

    <app-loader *ngIf="loading()"></app-loader>

    <div class="card" *ngIf="!loading()">
      <div class="card-body flush">
        <div class="notif-item" *ngFor="let n of filtered()"
             [class.unread]="!n.read"
             (click)="open(n)">
          <span class="badge" [ngClass]="n.severity | badgeClass">{{ n.severity | label }}</span>
          <div class="txt" style="flex:1;min-width:0">
            <strong>{{ n.title }}</strong>
            <p>{{ n.message }}</p>
            <span class="when">
              {{ n.actorName }} · {{ n.createdAt | timeAgo }} · {{ n.createdAt | dateFr:true }}
            </span>
          </div>
          <span class="dot-mark" *ngIf="!n.read" style="color:var(--info);margin-top:.4rem"></span>
        </div>

        <app-empty *ngIf="!filtered().length"
                   icon="🔕"
                   [message]="filter() === 'unread' ? 'Aucune notification non lue.' : 'Aucune notification.'">
        </app-empty>
      </div>
    </div>
  `
})
export class NotificationsComponent implements OnInit {
  private api = inject(NotificationService);
  private router = inject(Router);

  protected loading = signal(true);
  protected filter = signal<'all' | 'unread'>('all');
  protected readonly items = this.api.items;

  protected unreadCount = computed(() => this.items().filter(n => !n.read).length);

  protected filtered = computed(() =>
    this.filter() === 'unread' ? this.items().filter(n => !n.read) : this.items()
  );

  ngOnInit(): void {
    this.load();
  }

  protected load() {
    this.loading.set(true);
    this.api.list().subscribe({
      next: () => this.loading.set(false),
      error: () => this.loading.set(false)
    });
  }

  protected markAllRead() {
    this.api.markAllRead().subscribe();
  }

  protected open(notification: AppNotification) {
    if (!notification.read) {
      this.api.markRead(notification.id).subscribe();
    }
    if (notification.link) {
      this.router.navigateByUrl(notification.link);
    }
  }
}
