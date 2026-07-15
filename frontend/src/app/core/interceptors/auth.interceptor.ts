import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { TokenStorageService } from '../services/token-storage.service';
import { environment } from '../../../environments/environment';

const PUBLIC_PATHS = ['/api/auth/login', '/api/auth/register'];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenStorage = inject(TokenStorageService);
  const router = inject(Router);

  const isApiRequest = req.url.startsWith(environment.apiUrl);
  const isPublicPath = PUBLIC_PATHS.some((path) => req.url.includes(path));

  if (isApiRequest && !isPublicPath) {
    const token = tokenStorage.getToken();
    if (token) {
      req = req.clone({
        setHeaders: { Authorization: `Bearer ${token}` },
      });
    }
  }

  return next(req).pipe(
    catchError((error) => {
      if (error.status === 401) {
        tokenStorage.clear();
        router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};
