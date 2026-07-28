import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenStorageService } from '../services/token-storage.service';

export const adminGuard: CanActivateFn = () => {
  const tokenStorage = inject(TokenStorageService);
  const router = inject(Router);

  if (
    tokenStorage.isLoggedIn() &&
    !tokenStorage.isTokenExpired() &&
    tokenStorage.hasRole('ADMIN')
  ) {
    return true;
  }

  return router.createUrlTree(['/']);
};
