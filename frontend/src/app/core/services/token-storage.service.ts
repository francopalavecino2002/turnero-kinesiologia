import { Injectable, signal, computed } from '@angular/core';
import { Role, JwtPayload } from '../models';

const TOKEN_KEY = 'auth_token';
const USER_KEY = 'auth_user';

interface StoredUser {
  id: number;
  email: string;
  role: Role;
  firstName: string;
  lastName: string;
  mustChangePassword: boolean;
}

@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  private readonly _user = signal<StoredUser | null>(this.loadUser());

  readonly user = this._user.asReadonly();
  readonly isLoggedIn = computed(() => !!this._user());
  readonly role = computed(() => this._user()?.role ?? null);
  readonly mustChangePassword = computed(() => this._user()?.mustChangePassword ?? false);

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  setToken(token: string, user: StoredUser): void {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this._user.set(user);
  }

  updateMustChangePassword(value: boolean): void {
    const current = this._user();
    if (!current) return;
    const updated = { ...current, mustChangePassword: value };
    localStorage.setItem(USER_KEY, JSON.stringify(updated));
    this._user.set(updated);
  }

  clear(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this._user.set(null);
  }

  hasRole(...roles: Role[]): boolean {
    const current = this._user();
    return current !== null && roles.includes(current.role);
  }

  isTokenExpired(): boolean {
    const token = this.getToken();
    if (!token) return true;

    try {
      const payload = this.decodeToken(token);
      const now = Math.floor(Date.now() / 1000);
      return payload.exp < now;
    } catch {
      return true;
    }
  }

  private decodeToken(token: string): JwtPayload {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join(''),
    );
    return JSON.parse(jsonPayload) as JwtPayload;
  }

  private loadUser(): StoredUser | null {
    const token = this.getToken();
    const userJson = localStorage.getItem(USER_KEY);
    if (!token || !userJson) return null;

    if (this.isTokenExpired()) {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
      return null;
    }

    try {
      return JSON.parse(userJson) as StoredUser;
    } catch {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
      return null;
    }
  }
}
