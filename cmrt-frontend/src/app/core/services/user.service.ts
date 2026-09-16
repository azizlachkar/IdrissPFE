import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { API_URL } from '../api.config';
import { PipelineStageTemplate, Role, UserProfile } from '../models';

@Injectable({ providedIn: 'root' })
export class UserService {
  private http = inject(HttpClient);
  private base = `${API_URL}/users`;

  /** Cached: the directory feeds every assignment dropdown in the app. */
  private directory$?: Observable<Record<Role, UserProfile[]>>;

  list(role?: Role): Observable<UserProfile[]> {
    const url = role ? `${this.base}?role=${role}` : this.base;
    return this.http.get<UserProfile[]>(url);
  }

  directory(force = false): Observable<Record<Role, UserProfile[]>> {
    if (!this.directory$ || force) {
      this.directory$ = this.http
        .get<Record<Role, UserProfile[]>>(`${this.base}/directory`)
        .pipe(shareReplay(1));
    }
    return this.directory$;
  }

  update(id: string, payload: Partial<UserProfile>): Observable<UserProfile> {
    return this.http.put<UserProfile>(`${this.base}/${id}`, payload);
  }

  changeRole(id: string, role: Role): Observable<UserProfile> {
    this.directory$ = undefined;
    return this.http.patch<UserProfile>(`${this.base}/${id}/role`, { role });
  }

  setActive(id: string, active: boolean): Observable<UserProfile> {
    this.directory$ = undefined;
    return this.http.patch<UserProfile>(`${this.base}/${id}/active`, { active });
  }

  confirm(id: string): Observable<UserProfile> {
    return this.http.patch<UserProfile>(`${this.base}/${id}/confirm`, {});
  }

  /** The pipeline template published by the API, for the reference screen. */
  pipelineTemplate(): Observable<PipelineStageTemplate[]> {
    return this.http.get<PipelineStageTemplate[]>(`${API_URL}/metadata/pipeline`);
  }
}
