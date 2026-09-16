import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { label } from '../../core/labels';
import { DocumentStatus, DocumentType, ProductView, TechnicalDocument } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { DocumentService } from '../../core/services/document.service';
import { ProductService } from '../../core/services/product.service';
import { ToastService } from '../../core/services/toast.service';
import { BadgeClassPipe, DateFrPipe, FileSizePipe, LabelPipe, TimeAgoPipe } from '../../shared/pipes';
import { EmptyComponent, LoaderComponent, ModalComponent, StatComponent } from '../../shared/ui';

/**
 * Document register across all products. Each row is a controlled document; expanding it
 * shows its revision history. Only one revision is approved at a time - approving a new
 * one makes the previous obsolete, which is what the pipeline checks for its deliverables.
 */
@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink, ModalComponent,
    StatComponent, EmptyComponent, LoaderComponent,
    LabelPipe, BadgeClassPipe, DateFrPipe, TimeAgoPipe, FileSizePipe
  ],
  template: `
    <div class="page-head">
      <div>
        <h1>🗂 Documents</h1>
        <p>Plans, nomenclatures, gammes et rapports, avec leur historique de révisions.</p>
      </div>
      <div class="page-head-actions">
        <button class="btn btn-ghost" (click)="load()">↻ Actualiser</button>
        <button class="btn btn-primary" (click)="uploadOpen.set(true)">📎 Déposer un document</button>
      </div>
    </div>

    <div class="grid grid-4 mb-3">
      <app-stat label="Documents" [value]="documents().length"></app-stat>
      <app-stat label="Approuvés" [value]="countByStatus('APPROVED')" tone="ok"></app-stat>
      <app-stat label="En revue" [value]="countByStatus('IN_REVIEW')" tone="warn"></app-stat>
      <app-stat label="Révisions totales" [value]="totalVersions()" tone="info"></app-stat>
    </div>

    <div class="filters">
      <input class="input search" placeholder="🔍 Nom du document…"
             [ngModel]="search()" (ngModelChange)="search.set($event)">
      <select class="select" [ngModel]="productFilter()" (ngModelChange)="productFilter.set($event)">
        <option value="">Tous les produits</option>
        <option *ngFor="let p of products()" [value]="p.id">{{ p.reference }}</option>
      </select>
      <select class="select" [ngModel]="typeFilter()" (ngModelChange)="typeFilter.set($event)">
        <option value="">Tous les types</option>
        <option *ngFor="let t of docTypes" [value]="t">{{ t | label }}</option>
      </select>
      <span class="muted small" style="margin-left:auto">{{ filtered().length }} document(s)</span>
    </div>

    <app-loader *ngIf="loading()"></app-loader>

    <div class="card" *ngIf="!loading()">
      <div class="card-body flush">
        <div *ngIf="filtered().length; else none">
          <div *ngFor="let doc of filtered()" style="border-bottom:1px solid var(--border)">
            <div style="padding:.8rem 1.1rem;cursor:pointer" (click)="toggle(doc.id)">
              <div class="flex items-center gap-2 wrap">
                <span>{{ expanded() === doc.id ? '▾' : '▸' }}</span>
                <strong>{{ doc.name }}</strong>
                <span class="badge">{{ doc.type | label }}</span>
                <span class="badge" [ngClass]="doc.status | badgeClass">{{ doc.status | label }}</span>
                <span class="badge info" *ngIf="doc.currentVersion">Rév. {{ doc.currentVersion }}</span>
                <span class="badge purple" *ngIf="doc.stageType">{{ doc.stageType | label }}</span>
                <span class="tiny muted" style="margin-left:auto">
                  {{ referenceOf(doc.productId) }} · {{ doc.versions.length }} révision(s)
                </span>
              </div>
            </div>

            <div *ngIf="expanded() === doc.id" style="padding:0 1.1rem 1rem" class="table-wrap">
              <table class="table" style="font-size:.79rem">
                <thead>
                  <tr>
                    <th>Rév.</th><th>Fichier</th><th>Taille</th><th>Déposée par</th>
                    <th>Note de révision</th><th>Statut</th><th class="right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let v of doc.versions">
                    <td class="bold">{{ v.version }}</td>
                    <td class="truncate" style="max-width:220px">{{ v.fileName }}</td>
                    <td class="mono">{{ v.sizeBytes | fileSize }}</td>
                    <td>
                      <div class="small">{{ v.uploadedByName }}</div>
                      <div class="tiny muted">{{ v.uploadedAt | timeAgo }}</div>
                    </td>
                    <td class="tiny muted truncate" style="max-width:210px">{{ v.changeNote || '—' }}</td>
                    <td>
                      <span class="badge" [ngClass]="v.status | badgeClass">{{ v.status | label }}</span>
                      <div class="tiny muted" *ngIf="v.approvedAt">{{ v.approvedAt | dateFr }}</div>
                    </td>
                    <td class="right nowrap">
                      <button class="btn btn-ghost btn-sm" (click)="download(doc, v.version)" title="Télécharger">⤓</button>
                      <button class="btn btn-success btn-sm"
                              *ngIf="canApprove && v.status !== 'APPROVED' && v.status !== 'OBSOLETE'"
                              (click)="approve(doc, v.version)">✔ Approuver</button>
                      <button class="btn btn-danger btn-sm"
                              *ngIf="canApprove && v.status === 'IN_REVIEW'"
                              (click)="reject(doc, v.version)">✘</button>
                    </td>
                  </tr>
                </tbody>
              </table>

              <button class="btn btn-ghost btn-sm mt-1" (click)="uploadNewVersion(doc)">
                📎 Déposer la révision suivante
              </button>
              <a [routerLink]="['/products', doc.productId]" class="btn btn-ghost btn-sm mt-1">
                Ouvrir la fiche produit →
              </a>
            </div>
          </div>
        </div>
        <ng-template #none><app-empty icon="🗂" message="Aucun document ne correspond aux filtres."></app-empty></ng-template>
      </div>
    </div>

    <app-modal *ngIf="uploadOpen()" [title]="targetDoc() ? 'Nouvelle révision — ' + targetDoc()!.name : 'Déposer un document'"
               (closed)="closeUpload()">
      <div class="field">
        <label>Fichier <span class="req">*</span></label>
        <input type="file" class="input" (change)="onFile($event)">
        <div class="field-hint">25 Mo maximum.</div>
      </div>

      <ng-container *ngIf="!targetDoc()">
        <div class="field">
          <label>Produit <span class="req">*</span></label>
          <select class="select" [ngModel]="uploadProduct()" (ngModelChange)="uploadProduct.set($event)">
            <option value="">— Choisir —</option>
            <option *ngFor="let p of products()" [value]="p.id">{{ p.reference }} — {{ p.nom }}</option>
          </select>
        </div>
        <div class="form-row">
          <div class="field">
            <label>Type</label>
            <select class="select" [ngModel]="uploadType()" (ngModelChange)="uploadType.set($event)">
              <option *ngFor="let t of docTypes" [value]="t">{{ lbl(t) }}</option>
            </select>
          </div>
          <div class="field">
            <label>Nom du document</label>
            <input class="input" [ngModel]="uploadName()" (ngModelChange)="uploadName.set($event)"
                   placeholder="Laisser vide pour reprendre le nom du fichier">
          </div>
        </div>
      </ng-container>

      <div class="field">
        <label>Note de révision</label>
        <textarea class="textarea" [ngModel]="uploadNote()" (ngModelChange)="uploadNote.set($event)"
                  placeholder="Ce qui change par rapport à la révision précédente."></textarea>
      </div>

      <div class="seed-hint">
        La révision est déposée au statut « en revue » et ne compte comme livrable
        qu'une fois <b>approuvée</b>.
      </div>

      <ng-container modal-actions>
        <button class="btn btn-ghost" (click)="closeUpload()">Annuler</button>
        <button class="btn btn-primary" [disabled]="busy()" (click)="submitUpload()">
          {{ busy() ? 'Dépôt…' : 'Déposer' }}
        </button>
      </ng-container>
    </app-modal>
  `
})
export class DocumentsComponent implements OnInit {
  private api = inject(DocumentService);
  private productsApi = inject(ProductService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);

  protected loading = signal(true);
  protected busy = signal(false);
  protected documents = signal<TechnicalDocument[]>([]);
  protected products = signal<ProductView[]>([]);
  protected expanded = signal<string | null>(null);

  protected search = signal('');
  protected productFilter = signal('');
  protected typeFilter = signal('');

  protected uploadOpen = signal(false);
  protected targetDoc = signal<TechnicalDocument | null>(null);
  protected file = signal<File | null>(null);
  protected uploadProduct = signal('');
  protected uploadType = signal<DocumentType>('DRAWING');
  protected uploadName = signal('');
  protected uploadNote = signal('');

  protected lbl = label;
  protected readonly docTypes: DocumentType[] = [
    'DRAWING', 'BOM', 'WORK_INSTRUCTION', 'CONTROL_PLAN', 'TEST_PROCEDURE',
    'FAI_REPORT', 'PPAP', 'CUSTOMER_SPEC', 'TOOLING_DRAWING', 'ROUTING',
    'LAYOUT', 'QUALITY_REPORT', 'OTHER'
  ];

  protected readonly canApprove = this.auth.hasRole(
    'QUALITICIEN', 'CHEF_PROJET', 'METHODISTE', 'CONTROLE_TECHNIQUE'
  );

  protected filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    const productId = this.productFilter();
    const type = this.typeFilter();
    return this.documents().filter(d => {
      if (term && !d.name.toLowerCase().includes(term)) return false;
      if (productId && d.productId !== productId) return false;
      if (type && d.type !== type) return false;
      return true;
    });
  });

  ngOnInit(): void {
    this.load();
    this.productsApi.list().subscribe({ next: list => this.products.set(list) });
  }

  protected load() {
    this.loading.set(true);
    this.api.list().subscribe({
      next: list => {
        this.documents.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  protected toggle(id: string) {
    this.expanded.set(this.expanded() === id ? null : id);
  }

  protected countByStatus(status: DocumentStatus): number {
    return this.documents().filter(d => d.status === status).length;
  }

  protected totalVersions(): number {
    return this.documents().reduce((sum, d) => sum + d.versions.length, 0);
  }

  protected referenceOf(productId: string): string {
    return this.products().find(p => p.id === productId)?.reference ?? '—';
  }

  protected onFile(event: Event) {
    this.file.set((event.target as HTMLInputElement).files?.[0] ?? null);
  }

  protected uploadNewVersion(doc: TechnicalDocument) {
    this.targetDoc.set(doc);
    this.file.set(null);
    this.uploadNote.set('');
    this.uploadOpen.set(true);
  }

  protected closeUpload() {
    this.uploadOpen.set(false);
    this.targetDoc.set(null);
    this.file.set(null);
    this.uploadNote.set('');
    this.uploadName.set('');
  }

  protected submitUpload() {
    const file = this.file();
    const target = this.targetDoc();
    const productId = target?.productId ?? this.uploadProduct();

    if (!file) {
      this.toast.warning('Aucun fichier sélectionné');
      return;
    }
    if (!productId) {
      this.toast.warning('Produit obligatoire', 'Choisissez le produit auquel rattacher le document.');
      return;
    }
    this.busy.set(true);

    this.api.upload({
      file,
      productId,
      documentId: target?.id,
      type: target?.type ?? this.uploadType(),
      stageType: target?.stageType,
      name: this.uploadName() || undefined,
      changeNote: this.uploadNote() || undefined
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.closeUpload();
        this.toast.success('Document déposé', 'La révision attend son approbation.');
        this.load();
      },
      error: err => {
        this.busy.set(false);
        this.toast.error('Dépôt impossible', err.error?.message);
      }
    });
  }

  protected approve(doc: TechnicalDocument, version: string) {
    this.api.approve(doc.id, version).subscribe({
      next: () => {
        this.toast.success(`Révision ${version} approuvée`,
          'Les révisions précédentes passent en obsolète.');
        this.load();
      },
      error: err => this.toast.error('Approbation impossible', err.error?.message)
    });
  }

  protected reject(doc: TechnicalDocument, version: string) {
    this.api.reject(doc.id, version, 'Révision non conforme').subscribe({
      next: () => {
        this.toast.success(`Révision ${version} refusée`);
        this.load();
      },
      error: err => this.toast.error('Action impossible', err.error?.message)
    });
  }

  protected download(doc: TechnicalDocument, version: string) {
    this.api.download(doc.id, version).subscribe({
      next: blob => {
        // The download needs the bearer token, so it goes through XHR and an object URL.
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = doc.versions.find(v => v.version === version)?.fileName ?? doc.name;
        anchor.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.toast.error('Téléchargement impossible')
    });
  }
}
