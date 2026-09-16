import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { API_URL } from '../api.config';
import { AppNotification } from '../models';

/**
 * Notification inbox. The unread count is polled on a timer so the navbar badge
 * reflects events raised by other people without a page reload.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private http = inject(HttpClient);
  private base = `${API_URL}/notifications`;
  private timer?: ReturnType<typeof setInterval>;

  readonly unreadCount = signal(0);
  readonly items = signal<AppNotification[]>([]);

  list(unreadOnly = false): Observable<AppNotification[]> {
    let params = new HttpParams();
    if (unreadOnly) params = params.set('unreadOnly', 'true');
    return this.http.get<AppNotification[]>(this.base, { params }).pipe(
      tap(list => this.items.set(list))
    );
  }

  refreshCount(): void {
    this.http.get<{ count: number }>(`${this.base}/unread-count`).subscribe({
      next: res => this.unreadCount.set(res.count),
      // A failed poll must never surface as an error toast.
      error: () => undefined
    });
  }

  /** Starts polling; safe to call more than once. */
  startPolling(intervalMs = 45000): void {
    this.refreshCount();
    if (this.timer) return;
    this.timer = setInterval(() => this.refreshCount(), intervalMs);
  }

  stopPolling(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = undefined;
    }
    this.unreadCount.set(0);
    this.items.set([]);
  }

  markRead(id: string): Observable<void> {
    return this.http.patch<void>(`${this.base}/${id}/read`, {}).pipe(
      tap(() => {
        this.items.update(list => list.map(n => (n.id === id ? { ...n, read: true } : n)));
        this.unreadCount.update(count => Math.max(0, count - 1));
      })
    );
  }

  markAllRead(): Observable<void> {
    return this.http.patch<void>(`${this.base}/read-all`, {}).pipe(
      tap(() => {
        this.items.update(list => list.map(n => ({ ...n, read: true })));
        this.unreadCount.set(0);
      })
    );
  }
}
