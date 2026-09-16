import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { DocumentType, StageType, TechnicalDocument } from '../models';

/** Controlled documents and their revisions. */
@Injectable({ providedIn: 'root' })
export class DocumentService {
  private http = inject(HttpClient);
  private base = `${API_URL}/documents`;

  list(productId?: string): Observable<TechnicalDocument[]> {
    let params = new HttpParams();
    if (productId) params = params.set('productId', productId);
    return this.http.get<TechnicalDocument[]>(this.base, { params });
  }

  /**
   * Uploads a revision. Passing {@code documentId} appends to an existing
   * document instead of creating a new one at revision A.
   */
  upload(options: {
    file: File;
    productId: string;
    documentId?: string;
    type?: DocumentType;
    stageType?: StageType;
    name?: string;
    changeNote?: string;
  }): Observable<TechnicalDocument> {
    const body = new FormData();
    body.append('file', options.file);

    let params = new HttpParams().set('productId', options.productId);
    if (options.documentId) params = params.set('documentId', options.documentId);
    if (options.type) params = params.set('type', options.type);
    if (options.stageType) params = params.set('stageType', options.stageType);
    if (options.name) params = params.set('name', options.name);
    if (options.changeNote) params = params.set('changeNote', options.changeNote);

    return this.http.post<TechnicalDocument>(this.base, body, { params });
  }

  approve(id: string, version: string): Observable<TechnicalDocument> {
    return this.http.post<TechnicalDocument>(`${this.base}/${id}/versions/${version}/approve`, {});
  }

  reject(id: string, version: string, comment: string): Observable<TechnicalDocument> {
    return this.http.post<TechnicalDocument>(`${this.base}/${id}/versions/${version}/reject`, { comment });
  }

  /** Pulls the file as a blob so the bearer token is still applied to the request. */
  download(id: string, version?: string): Observable<Blob> {
    let params = new HttpParams();
    if (version) params = params.set('version', version);
    return this.http.get(`${this.base}/${id}/download`, { params, responseType: 'blob' });
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
