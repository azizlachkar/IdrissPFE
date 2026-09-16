import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { AuditLog, DashboardResponse, WorkloadItem } from '../models';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private http = inject(HttpClient);
  private base = `${API_URL}/dashboard`;

  overview(): Observable<DashboardResponse> {
    return this.http.get<DashboardResponse>(this.base);
  }

  workload(): Observable<WorkloadItem[]> {
    return this.http.get<WorkloadItem[]>(`${this.base}/workload`);
  }

  activity(limit = 50): Observable<AuditLog[]> {
    return this.http.get<AuditLog[]>(`${this.base}/activity?limit=${limit}`);
  }
}
