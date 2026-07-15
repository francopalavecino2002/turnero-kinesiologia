import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenStorageService } from '../services/token-storage.service';

export const authGuard: CanActivateFn = () => {
  const tokenStorage = inject(TokenStorageService);
  const router = inject(Router);

  if (tokenStorage.isLoggedIn() && !tokenStorage.isTokenExpired()) {
    return true;
  }

  return router.createUrlTree(['/login']);
};
