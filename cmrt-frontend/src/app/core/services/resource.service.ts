import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { Reservation, ResourceStatus, TestResource } from '../models';

/** Technical-control assets and their bookings. */
@Injectable({ providedIn: 'root' })
export class ResourceService {
  private http = inject(HttpClient);
  private resources = `${API_URL}/resources`;
  private reservations = `${API_URL}/reservations`;

  list(availableOnly = false): Observable<TestResource[]> {
    let params = new HttpParams();
    if (availableOnly) params = params.set('availableOnly', 'true');
    return this.http.get<TestResource[]>(this.resources, { params });
  }

  create(payload: Partial<TestResource>): Observable<TestResource> {
    return this.http.post<TestResource>(this.resources, payload);
  }

  update(id: string, payload: Partial<TestResource>): Observable<TestResource> {
    return this.http.put<TestResource>(`${this.resources}/${id}`, payload);
  }

  changeStatus(id: string, status: ResourceStatus): Observable<TestResource> {
    return this.http.patch<TestResource>(`${this.resources}/${id}/status`, { status });
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.resources}/${id}`);
  }

  // -------------------------------------------------------- reservations

  listReservations(options: { resourceId?: string; pendingOnly?: boolean } = {}): Observable<Reservation[]> {
    let params = new HttpParams();
    if (options.resourceId) params = params.set('resourceId', options.resourceId);
    if (options.pendingOnly) params = params.set('pendingOnly', 'true');
    return this.http.get<Reservation[]>(this.reservations, { params });
  }

  myReservations(): Observable<Reservation[]> {
    return this.http.get<Reservation[]>(`${this.reservations}/mine`);
  }

  reserve(payload: Partial<Reservation>): Observable<Reservation> {
    return this.http.post<Reservation>(this.reservations, payload);
  }

  approveReservation(id: string, comment?: string): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.reservations}/${id}/approve`, { comment });
  }

  rejectReservation(id: string, comment: string): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.reservations}/${id}/reject`, { comment });
  }

  startReservation(id: string): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.reservations}/${id}/start`, {});
  }

  completeReservation(id: string): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.reservations}/${id}/complete`, {});
  }

  cancelReservation(id: string): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.reservations}/${id}/cancel`, {});
  }
}
