import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { progressClass } from '../../core/labels';
import { ProductView } from '../../core/models';
import { ProductService } from '../../core/services/product.service';
import { BadgeClassPipe, DateFrPipe, DaysLeftPipe, LabelPipe } from '../../shared/pipes';
import { EmptyComponent, LoaderComponent, ProgressComponent } from '../../shared/ui';

/** Products where the signed-in user is methodiste, qualiticien or project lead. */
@Component({
  selector: 'app-my-projects',
  standalone: true,
  imports: [
    CommonModule, EmptyComponent, LoaderComponent, ProgressComponent,
    LabelPipe, BadgeClassPipe, DateFrPipe, DaysLeftPipe
  ],
  template: `
    <div class="page-head">
      <div>
        <h1>★ Mes projets</h1>
        <p>Les produits dont vous êtes responsable, tous périmètres confondus.</p>
      </div>
      <div class="page-head-actions">
        <button class="btn btn-ghost" (click)="load()">↻ Actualiser</button>
      </div>
    </div>

    <app-loader *ngIf="loading()"></app-loader>

    <div class="grid grid-3" *ngIf="!loading()">
      <div class="card" *ngFor="let p of products()" style="cursor:pointer" (click)="open(p)">
        <div class="card-body">
          <div class="flex items-center gap-2 wrap mb-2">
            <strong>{{ p.reference }}</strong>
            <span class="badge" [ngClass]="p.projectType | badgeClass">{{ p.projectType | label }}</span>
            <span class="badge danger" *ngIf="p.blocked">⛔</span>
          </div>

          <div class="small mb-2 truncate">{{ p.nom }}</div>

          <app-progress [value]="p.progressPercent"
                        [tone]="tone(p)"></app-progress>

          <div class="tiny muted mt-1">
            {{ p.currentStageLabel || '—' }} · {{ p.completedStages }}/{{ p.totalStages }} jalons
          </div>

          <div class="flex justify-between mt-2 tiny">
            <span class="muted">SOP {{ p.targetSopDate | dateFr }}</span>
            <span [style.color]="p.atRisk ? 'var(--danger)' : 'var(--text-muted)'">
              {{ p.targetSopDate | daysLeft }}
            </span>
          </div>

          <div class="flex gap-1 mt-2 wrap">
            <span class="badge danger" *ngIf="p.openIssues">{{ p.openIssues }} blocage(s)</span>
            <span class="badge purple" *ngIf="p.openTasks">{{ p.openTasks }} tâche(s)</span>
          </div>
        </div>
      </div>
    </div>

    <app-empty *ngIf="!loading() && !products().length"
               icon="★"
               message="Aucun produit ne vous est affecté pour le moment.">
    </app-empty>
  `
})
export class MyProjectsComponent implements OnInit {
  private products_ = inject(ProductService);
  private router = inject(Router);

  protected loading = signal(true);
  protected products = signal<ProductView[]>([]);

  ngOnInit(): void {
    this.load();
  }

  protected load() {
    this.loading.set(true);
    this.products_.mine().subscribe({
      next: list => {
        this.products.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  protected tone(p: ProductView): string {
    return progressClass(p.progressPercent, p.blocked || p.atRisk);
  }

  protected open(p: ProductView) {
    this.router.navigate(['/products', p.id]);
  }
}
