import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { EngineeringChange } from '../models';

/** Engineering change control (ECR / ECO). */
@Injectable({ providedIn: 'root' })
export class ChangeService {
  private http = inject(HttpClient);
  private base = `${API_URL}/changes`;

  list(productId?: string): Observable<EngineeringChange[]> {
    let params = new HttpParams();
    if (productId) params = params.set('productId', productId);
    return this.http.get<EngineeringChange[]>(this.base, { params });
  }

  get(id: string): Observable<EngineeringChange> {
    return this.http.get<EngineeringChange>(`${this.base}/${id}`);
  }

  create(payload: Partial<EngineeringChange>): Observable<EngineeringChange> {
    return this.http.post<EngineeringChange>(this.base, payload);
  }

  update(id: string, payload: Partial<EngineeringChange>): Observable<EngineeringChange> {
    return this.http.put<EngineeringChange>(`${this.base}/${id}`, payload);
  }

  submit(id: string): Observable<EngineeringChange> {
    return this.http.post<EngineeringChange>(`${this.base}/${id}/submit`, {});
  }

  approve(id: string, comment?: string): Observable<EngineeringChange> {
    return this.http.post<EngineeringChange>(`${this.base}/${id}/approve`, { comment });
  }

  reject(id: string, comment: string): Observable<EngineeringChange> {
    return this.http.post<EngineeringChange>(`${this.base}/${id}/reject`, { comment });
  }

  implement(id: string): Observable<EngineeringChange> {
    return this.http.post<EngineeringChange>(`${this.base}/${id}/implement`, {});
  }

  close(id: string): Observable<EngineeringChange> {
    return this.http.post<EngineeringChange>(`${this.base}/${id}/close`, {});
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
