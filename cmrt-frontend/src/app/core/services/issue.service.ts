import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { Issue, IssueStatus } from '../models';

/** Blockage tracking. */
@Injectable({ providedIn: 'root' })
export class IssueService {
  private http = inject(HttpClient);
  private base = `${API_URL}/issues`;

  list(options: { productId?: string; openOnly?: boolean } = {}): Observable<Issue[]> {
    let params = new HttpParams();
    if (options.productId) params = params.set('productId', options.productId);
    if (options.openOnly) params = params.set('openOnly', 'true');
    return this.http.get<Issue[]>(this.base, { params });
  }

  mine(): Observable<Issue[]> {
    return this.http.get<Issue[]>(`${this.base}/mine`);
  }

  get(id: string): Observable<Issue> {
    return this.http.get<Issue>(`${this.base}/${id}`);
  }

  create(payload: Partial<Issue>): Observable<Issue> {
    return this.http.post<Issue>(this.base, payload);
  }

  update(id: string, payload: Partial<Issue>): Observable<Issue> {
    return this.http.put<Issue>(`${this.base}/${id}`, payload);
  }

  assign(id: string, assigneeId: string): Observable<Issue> {
    return this.http.patch<Issue>(`${this.base}/${id}/assign`, { assigneeId });
  }

  changeStatus(id: string, status: IssueStatus, comment?: string): Observable<Issue> {
    return this.http.patch<Issue>(`${this.base}/${id}/status`, { status, comment });
  }

  comment(id: string, message: string): Observable<Issue> {
    return this.http.post<Issue>(`${this.base}/${id}/comments`, { message });
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
