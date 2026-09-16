import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { AuditLog, ProductDetail, ProductRequest, ProductView, ProjectType, StageType } from '../models';

/** Products and their pipeline transitions. */
@Injectable({ providedIn: 'root' })
export class ProductService {
  private http = inject(HttpClient);
  private base = `${API_URL}/products`;

  list(type?: ProjectType, search?: string): Observable<ProductView[]> {
    let params = new HttpParams();
    if (type) params = params.set('type', type);
    if (search) params = params.set('search', search);
    return this.http.get<ProductView[]>(this.base, { params });
  }

  mine(): Observable<ProductView[]> {
    return this.http.get<ProductView[]>(`${this.base}/mine`);
  }

  detail(id: string): Observable<ProductDetail> {
    return this.http.get<ProductDetail>(`${this.base}/${id}`);
  }

  history(id: string): Observable<AuditLog[]> {
    return this.http.get<AuditLog[]>(`${this.base}/${id}/history`);
  }

  create(payload: ProductRequest): Observable<ProductDetail> {
    return this.http.post<ProductDetail>(this.base, payload);
  }

  update(id: string, payload: ProductRequest): Observable<ProductDetail> {
    return this.http.put<ProductDetail>(`${this.base}/${id}`, payload);
  }

  assign(id: string, payload: { methodisteId?: string; qualiticienId?: string; chefProjetId?: string }): Observable<ProductDetail> {
    return this.http.patch<ProductDetail>(`${this.base}/${id}/assign`, payload);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  // ------------------------------------------------------------ pipeline

  startStage(id: string, stage: StageType): Observable<unknown> {
    return this.http.post(`${this.base}/${id}/stages/${stage}/start`, {});
  }

  submitStage(id: string, stage: StageType): Observable<unknown> {
    return this.http.post(`${this.base}/${id}/stages/${stage}/submit`, {});
  }

  approveStage(id: string, stage: StageType, comment?: string): Observable<unknown> {
    return this.http.post(`${this.base}/${id}/stages/${stage}/approve`, { comment });
  }

  rejectStage(id: string, stage: StageType, comment: string): Observable<unknown> {
    return this.http.post(`${this.base}/${id}/stages/${stage}/reject`, { comment });
  }

  skipStage(id: string, stage: StageType, comment: string): Observable<unknown> {
    return this.http.post(`${this.base}/${id}/stages/${stage}/skip`, { comment });
  }

  updateStage(id: string, stage: StageType, payload: Record<string, unknown>): Observable<unknown> {
    return this.http.put(`${this.base}/${id}/stages/${stage}`, payload);
  }
}
