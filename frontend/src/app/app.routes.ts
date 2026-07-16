import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./pages/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./pages/register/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'change-password',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/change-password/change-password.component').then(
        (m) => m.ChangePasswordComponent,
      ),
  },
  {
    path: '',
    loadComponent: () =>
      import('./pages/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'book',
    canActivate: [authGuard, roleGuard('PATIENT')],
    loadComponent: () =>
      import('./pages/book/book.component').then((m) => m.BookComponent),
  },
  {
    path: 'my-appointments',
    canActivate: [authGuard, roleGuard('PATIENT')],
    loadComponent: () =>
      import('./pages/my-appointments/my-appointments.component').then(
        (m) => m.MyAppointmentsComponent,
      ),
  },
  {
    path: 'agenda',
    canActivate: [authGuard, roleGuard('PROFESSIONAL', 'ADMIN')],
    loadComponent: () =>
      import('./pages/agenda/agenda.component').then((m) => m.AgendaComponent),
  },
];
