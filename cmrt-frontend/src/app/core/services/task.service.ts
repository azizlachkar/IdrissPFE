import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { Task, TaskStatus } from '../models';

@Injectable({ providedIn: 'root' })
export class TaskService {
  private http = inject(HttpClient);
  private base = `${API_URL}/tasks`;

  list(options: { productId?: string; assigneeId?: string } = {}): Observable<Task[]> {
    let params = new HttpParams();
    if (options.productId) params = params.set('productId', options.productId);
    if (options.assigneeId) params = params.set('assigneeId', options.assigneeId);
    return this.http.get<Task[]>(this.base, { params });
  }

  mine(): Observable<Task[]> {
    return this.http.get<Task[]>(`${this.base}/mine`);
  }

  create(payload: Partial<Task>): Observable<Task> {
    return this.http.post<Task>(this.base, payload);
  }

  update(id: string, payload: Partial<Task>): Observable<Task> {
    return this.http.put<Task>(`${this.base}/${id}`, payload);
  }

  changeStatus(id: string, status: TaskStatus): Observable<Task> {
    return this.http.patch<Task>(`${this.base}/${id}/status`, { status });
  }

  toggleChecklist(id: string, index: number): Observable<Task> {
    return this.http.patch<Task>(`${this.base}/${id}/checklist/${index}`, {});
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
