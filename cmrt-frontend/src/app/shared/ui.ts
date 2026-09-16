import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

/** Small presentational building blocks shared across the feature screens. */

@Component({
  selector: 'app-stat',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="stat" [ngClass]="tone">
      <span class="stat-label">{{ label }}</span>
      <span class="stat-value">{{ value }}<small *ngIf="unit"> {{ unit }}</small></span>
      <span class="stat-hint" *ngIf="hint">{{ hint }}</span>
    </div>
  `
})
export class StatComponent {
  @Input({ required: true }) label = '';
  @Input({ required: true }) value: string | number = 0;
  @Input() unit = '';
  @Input() hint = '';
  /** Colour of the left rail: '', ok, warn, bad, info, purple. */
  @Input() tone = '';
}

@Component({
  selector: 'app-empty',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="empty">
      <span class="icon">{{ icon }}</span>
      <p>{{ message }}</p>
      <ng-content></ng-content>
    </div>
  `
})
export class EmptyComponent {
  @Input() icon = '∅';
  @Input() message = 'Aucun élément à afficher';
}

@Component({
  selector: 'app-loader',
  standalone: true,
  template: `<div class="spinner" role="status" aria-label="Chargement"></div>`
})
export class LoaderComponent {}

/**
 * Modal dialog. Closing is routed through {@link closed} so the host owns the
 * open/closed state and the backdrop click, escape key and close button behave alike.
 */
@Component({
  selector: 'app-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <div class="modal" [class.wide]="wide" role="dialog" aria-modal="true">
        <div class="modal-head">
          <h3>{{ title }}</h3>
          <button type="button" class="modal-close" (click)="closed.emit()" aria-label="Fermer">&times;</button>
        </div>
        <div class="modal-body">
          <ng-content></ng-content>
        </div>
        <div class="modal-foot" *ngIf="!hideFooter">
          <ng-content select="[modal-actions]"></ng-content>
        </div>
      </div>
    </div>
  `,
  host: { '(document:keydown.escape)': 'closed.emit()' }
})
export class ModalComponent {
  @Input({ required: true }) title = '';
  @Input() wide = false;
  @Input() hideFooter = false;
  @Output() closed = new EventEmitter<void>();

  protected onBackdrop(event: MouseEvent) {
    // Only a click on the backdrop itself closes; clicks inside the panel bubble here too.
    if ((event.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.closed.emit();
    }
  }
}

@Component({
  selector: 'app-progress',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="progress-label">
      <div class="progress" [ngClass]="tone">
        <span [style.width.%]="clamped"></span>
      </div>
      <span class="pct" *ngIf="showValue">{{ clamped }}%</span>
    </div>
  `
})
export class ProgressComponent {
  @Input({ required: true }) set value(v: number) {
    this.clamped = Math.max(0, Math.min(100, Math.round(v ?? 0)));
  }
  @Input() tone = '';
  @Input() showValue = true;

  protected clamped = 0;
}
