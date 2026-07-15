import { Injectable, inject, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  RegisterResponse,
  UserInfoResponse,
  Role,
} from '../models';
import { TokenStorageService } from './token-storage.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly tokenStorage = inject(TokenStorageService);

  private readonly apiUrl = `${environment.apiUrl}/auth`;

  readonly user = this.tokenStorage.user;
  readonly isLoggedIn = this.tokenStorage.isLoggedIn;
  readonly role = this.tokenStorage.role;

  register(request: RegisterRequest) {
    return this.http.post<RegisterResponse>(`${this.apiUrl}/register`, request);
  }

  login(request: LoginRequest) {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, request).pipe(
      tap((response) => {
        this.tokenStorage.setToken(response.token, {
          id: response.id,
          email: response.email,
          role: response.role,
          firstName: response.firstName,
          lastName: response.lastName,
        });
      }),
    );
  }

  logout(): void {
    this.tokenStorage.clear();
    this.router.navigate(['/login']);
  }

  getCurrentUser() {
    return this.http.get<UserInfoResponse>(`${this.apiUrl}/me`);
  }

  isAuthenticated(): boolean {
    return this.isLoggedIn() && !this.tokenStorage.isTokenExpired();
  }

  hasRole(...roles: Role[]): boolean {
    return this.tokenStorage.hasRole(...roles);
  }
}
