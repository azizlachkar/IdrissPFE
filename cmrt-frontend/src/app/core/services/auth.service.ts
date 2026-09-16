import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { API_URL } from '../api.config';
import { AuthResponse, Role, UserProfile } from '../models';

const TOKEN_KEY = 'cmrt.token';
const USER_KEY = 'cmrt.user';

/**
 * Session state. The JWT and the profile are kept in localStorage so a reload
 * restores the session; every screen reads the current user from the signal.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  readonly currentUser = signal<UserProfile | null>(this.readUser());
  readonly isLoggedIn = computed(() => this.currentUser() !== null);
  readonly role = computed<Role | null>(() => this.currentUser()?.role ?? null);

  get token(): string | null {
    return typeof localStorage === 'undefined' ? null : localStorage.getItem(TOKEN_KEY);
  }

  login(credentials: { email: string; password: string }): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${API_URL}/auth/login`, credentials).pipe(
      tap(res => this.persist(res))
    );
  }

  signup(payload: Record<string, unknown>): Observable<UserProfile> {
    return this.http.post<UserProfile>(`${API_URL}/auth/signup`, payload);
  }

  verifyEmail(token: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${API_URL}/auth/verify`, { token });
  }

  forgotPassword(email: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${API_URL}/auth/forgot-password`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${API_URL}/auth/reset-password`, { token, newPassword });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${API_URL}/auth/change-password`, { currentPassword, newPassword });
  }

  /** Re-reads the profile from the API, so role changes take effect without a re-login. */
  refreshProfile(): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${API_URL}/auth/me`).pipe(
      tap(user => {
        this.currentUser.set(user);
        localStorage.setItem(USER_KEY, JSON.stringify(user));
      })
    );
  }

  logout(redirect = true) {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.currentUser.set(null);
    if (redirect) {
      this.router.navigate(['/login']);
    }
  }

  /** True when the signed-in user holds any of the given roles. ADMIN always passes. */
  hasRole(...roles: Role[]): boolean {
    const current = this.role();
    if (!current) return false;
    if (current === 'ADMIN') return true;
    return roles.includes(current);
  }

  private persist(res: AuthResponse) {
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(USER_KEY, JSON.stringify(res.user));
    this.currentUser.set(res.user);
  }

  private readUser(): UserProfile | null {
    if (typeof localStorage === 'undefined') return null;
    const raw = localStorage.getItem(USER_KEY);
    if (!raw || !localStorage.getItem(TOKEN_KEY)) return null;
    try {
      return JSON.parse(raw) as UserProfile;
    } catch {
      return null;
    }
  }
}
