import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: number;
  kind: 'success' | 'error' | 'warning' | 'info';
  title: string;
  message?: string;
}

/** Transient feedback messages, rendered by the toast stack in the shell. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private counter = 0;
  readonly toasts = signal<Toast[]>([]);

  success(title: string, message?: string) { this.push('success', title, message); }
  error(title: string, message?: string) { this.push('error', title, message, 7000); }
  warning(title: string, message?: string) { this.push('warning', title, message); }
  info(title: string, message?: string) { this.push('info', title, message); }

  dismiss(id: number) {
    this.toasts.update(list => list.filter(t => t.id !== id));
  }

  private push(kind: Toast['kind'], title: string, message?: string, ttl = 4500) {
    const id = ++this.counter;
    this.toasts.update(list => [...list, { id, kind, title, message }]);
    setTimeout(() => this.dismiss(id), ttl);
  }
}
