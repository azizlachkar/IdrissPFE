import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { DashboardResponse, Task, WorkloadItem } from '../../core/models';
import { TaskService } from '../../core/services/task.service';
import { BarChartComponent, DonutChartComponent, LineChartComponent } from '../../shared/charts';
import { DateFrPipe, LabelPipe, TimeAgoPipe } from '../../shared/pipes';
import { EmptyComponent, LoaderComponent, StatComponent } from '../../shared/ui';

/**
 * Executive view: the indicators, the pipeline funnel, what needs attention today,
 * and the caller's own workload.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule, RouterLink,
    StatComponent, EmptyComponent, LoaderComponent,
    DonutChartComponent, BarChartComponent, LineChartComponent,
    LabelPipe, DateFrPipe, TimeAgoPipe
  ],
  template: `
    <div class="page-head">
      <div>
        <h1>Bonjour {{ user()?.prenom }} 👋</h1>
        <p>Vue d'ensemble de l'industrialisation au {{ today | dateFr }}.</p>
      </div>
      <div class="page-head-actions">
        <button class="btn btn-ghost" (click)="load()">↻ Actualiser</button>
        <a routerLink="/npi" class="btn btn-primary">Projets NPI</a>
      </div>
    </div>

    <app-loader *ngIf="loading()"></app-loader>

    <ng-container *ngIf="data() as d">
      <!-- Headline indicators -->
      <div class="grid grid-4 mb-3">
        <app-stat label="Produits suivis" [value]="d.kpis.totalProducts"
                  [hint]="d.kpis.npiProducts + ' NPI · ' + d.kpis.productionProducts + ' série'"></app-stat>
        <app-stat label="Avancement moyen" [value]="d.kpis.averageProgress" unit="%"
                  tone="info" [hint]="d.kpis.productsInMassProduction + ' produit(s) en série'"></app-stat>
        <app-stat label="Produits bloqués" [value]="d.kpis.blockedProducts"
                  [tone]="d.kpis.blockedProducts > 0 ? 'bad' : 'ok'"
                  [hint]="d.kpis.productsAtRisk + ' projet(s) à risque'"></app-stat>
        <app-stat label="Jalons en retard" [value]="d.kpis.overdueStages"
                  [tone]="d.kpis.overdueStages > 0 ? 'warn' : 'ok'"
                  [hint]="'Respect des délais : ' + d.kpis.onTimeStageRate + '%'"></app-stat>
      </div>

      <div class="grid grid-4 mb-3">
        <app-stat label="Blocages ouverts" [value]="d.kpis.openIssues"
                  [tone]="d.kpis.criticalIssues > 0 ? 'bad' : ''"
                  [hint]="d.kpis.criticalIssues + ' critique(s) · ' + d.kpis.slaBreaches + ' SLA dépassé(s)'"></app-stat>
        <app-stat label="Tâches ouvertes" [value]="d.kpis.openTasks" tone="purple"
                  [hint]="d.kpis.overdueTasks + ' en retard'"></app-stat>
        <app-stat label="Validations en attente" [value]="d.kpis.pendingApprovals"
                  [tone]="d.kpis.pendingApprovals > 0 ? 'warn' : 'ok'"
                  [hint]="d.kpis.openChanges + ' modification(s) en cours'"></app-stat>
        <app-stat label="Moyens de test" [value]="d.kpis.availableResources + ' / ' + d.kpis.totalResources"
                  tone="ok"
                  [hint]="'Utilisation ' + d.kpis.resourceUtilisation + '% · ' + d.kpis.pendingReservations + ' demande(s)'"></app-stat>
      </div>

      <!-- Pipeline funnel + attention list -->
      <div class="grid grid-2 mb-3">
        <div class="card">
          <div class="card-head"><h3>Répartition par jalon</h3>
            <span class="badge info">{{ d.kpis.totalProducts }} produits</span>
          </div>
          <div class="card-body">
            <app-bar-chart [data]="d.productsByStage" [monochrome]="true"></app-bar-chart>
          </div>
        </div>

        <div class="card">
          <div class="card-head">
            <h3>À traiter en priorité</h3>
            <span class="badge" [ngClass]="d.alerts.length ? 'danger' : 'success'">{{ d.alerts.length }}</span>
          </div>
          <div class="card-body flush" style="max-height:330px;overflow-y:auto">
            <div class="alert-row" *ngFor="let a of d.alerts"
                 [class.critical]="a.severity === 'CRITICAL'"
                 (click)="go(a.link)">
              <span class="bar"></span>
              <div class="txt">
                <strong>{{ a.title }}</strong>
                <span>{{ a.detail }}</span>
              </div>
            </div>
            <app-empty *ngIf="!d.alerts.length" icon="✅" message="Rien d'urgent : tous les jalons sont dans les temps."></app-empty>
          </div>
        </div>
      </div>

      <!-- Distributions -->
      <div class="grid grid-3 mb-3">
        <div class="card">
          <div class="card-head"><h3>Blocages par gravité</h3></div>
          <div class="card-body">
            <app-donut-chart [data]="d.issuesBySeverity" caption="blocages"
                             ariaLabel="Répartition des blocages par gravité"></app-donut-chart>
          </div>
        </div>

        <div class="card">
          <div class="card-head"><h3>Produits par client</h3></div>
          <div class="card-body">
            <app-donut-chart [data]="d.productsByCustomer" caption="produits"
                             ariaLabel="Répartition des produits par client"></app-donut-chart>
          </div>
        </div>

        <div class="card">
          <div class="card-head"><h3>Moyens de test</h3></div>
          <div class="card-body">
            <app-donut-chart [data]="d.resourcesByStatus" caption="moyens"
                             ariaLabel="État des moyens de test"></app-donut-chart>
          </div>
        </div>
      </div>

      <div class="grid grid-2 mb-3">
        <div class="card">
          <div class="card-head"><h3>Jalons clôturés par mois</h3>
            <span class="badge">6 derniers mois</span>
          </div>
          <div class="card-body">
            <app-line-chart [data]="d.monthlyThroughput" ariaLabel="Jalons clôturés par mois"></app-line-chart>
          </div>
        </div>

        <div class="card">
          <div class="card-head"><h3>Blocages par catégorie</h3></div>
          <div class="card-body">
            <app-bar-chart [data]="d.issuesByCategory"></app-bar-chart>
          </div>
        </div>
      </div>

      <!-- Personal workload + activity trail -->
      <div class="grid grid-2">
        <div class="card">
          <div class="card-head">
            <h3>Mes tâches en cours</h3>
            <a routerLink="/taches" class="small">Tout voir →</a>
          </div>
          <div class="card-body flush">
            <table class="table" *ngIf="myTasks().length; else noTasks">
              <tbody>
                <tr *ngFor="let t of myTasks().slice(0, 6)">
                  <td>
                    <div class="bold">{{ t.title }}</div>
                    <div class="tiny muted">{{ t.status | label }}</div>
                  </td>
                  <td class="right nowrap">
                    <span class="badge" [ngClass]="t.overdue ? 'danger' : ''">
                      {{ t.dueDate | dateFr }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
            <ng-template #noTasks>
              <app-empty icon="☑" message="Aucune tâche ouverte à votre nom."></app-empty>
            </ng-template>
          </div>
        </div>

        <div class="card">
          <div class="card-head"><h3>Activité récente</h3></div>
          <div class="card-body" style="max-height:330px;overflow-y:auto">
            <div class="timeline" *ngIf="d.recentActivity.length; else noActivity">
              <div class="timeline-item" *ngFor="let a of d.recentActivity"
                   [class.success]="a.action === 'APPROVE'"
                   [class.danger]="a.action === 'REJECT' || a.action === 'DELETE'">
                <div class="what">{{ a.summary }}</div>
                <div class="who">{{ a.actorName }} · <span class="when">{{ a.timestamp | timeAgo }}</span></div>
              </div>
            </div>
            <ng-template #noActivity>
              <app-empty icon="🕘" message="Aucune activité enregistrée."></app-empty>
            </ng-template>
          </div>
        </div>
      </div>

      <!-- Team workload, for leads only -->
      <div class="card mt-3" *ngIf="workload().length">
        <div class="card-head"><h3>Charge de l'équipe</h3></div>
        <div class="card-body flush table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th>Collaborateur</th><th>Rôle</th>
                <th class="num">Tâches</th><th class="num">En retard</th>
                <th class="num">Blocages</th><th class="num">Jalons</th><th class="num">Produits</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let w of workload()">
                <td class="bold">{{ w.name }}</td>
                <td><span class="badge">{{ w.role | label }}</span></td>
                <td class="num">{{ w.openTasks }}</td>
                <td class="num">
                  <span class="badge" [ngClass]="w.overdueTasks > 0 ? 'danger' : ''">{{ w.overdueTasks }}</span>
                </td>
                <td class="num">{{ w.openIssues }}</td>
                <td class="num">{{ w.ownedStages }}</td>
                <td class="num">{{ w.products }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </ng-container>
  `
})
export class DashboardComponent implements OnInit {
  private dashboard = inject(DashboardService);
  private tasks = inject(TaskService);
  private auth = inject(AuthService);
  private router = inject(Router);

  protected readonly user = this.auth.currentUser;
  protected readonly today = new Date();

  protected loading = signal(true);
  protected data = signal<DashboardResponse | null>(null);
  protected myTasks = signal<Task[]>([]);
  protected workload = signal<WorkloadItem[]>([]);

  ngOnInit(): void {
    this.load();
  }

  protected load() {
    this.loading.set(true);

    this.dashboard.overview().subscribe({
      next: res => {
        this.data.set(res);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });

    this.tasks.mine().subscribe({ next: list => this.myTasks.set(list) });

    // Team workload is restricted server-side; ask only when the role allows it.
    if (this.auth.hasRole('CHEF_PROJET', 'RESPONSABLE_PRODUCTION')) {
      this.dashboard.workload().subscribe({
        next: list => this.workload.set(list),
        error: () => this.workload.set([])
      });
    }
  }

  protected go(link: string) {
    if (link) {
      this.router.navigateByUrl(link);
    }
  }
}
