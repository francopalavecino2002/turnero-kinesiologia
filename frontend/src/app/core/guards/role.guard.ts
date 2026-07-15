import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenStorageService } from '../services/token-storage.service';
import { Role } from '../models';

export function roleGuard(...allowedRoles: Role[]): CanActivateFn {
  return () => {
    const tokenStorage = inject(TokenStorageService);
    const router = inject(Router);

    if (!tokenStorage.isLoggedIn() || tokenStorage.isTokenExpired()) {
      return router.createUrlTree(['/login']);
    }

    if (tokenStorage.hasRole(...allowedRoles)) {
      return true;
    }

    return router.createUrlTree(['/']);
  };
}
