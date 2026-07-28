import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { adminGuard } from './core/guards/admin.guard';
import { mustChangePasswordGuard } from './core/guards/must-change-password.guard';

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
    canActivate: [mustChangePasswordGuard],
    loadComponent: () =>
      import('./pages/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'book',
    canActivate: [authGuard, roleGuard('PATIENT'), mustChangePasswordGuard],
    loadComponent: () =>
      import('./pages/book/book.component').then((m) => m.BookComponent),
  },
  {
    path: 'my-appointments',
    canActivate: [authGuard, roleGuard('PATIENT'), mustChangePasswordGuard],
    loadComponent: () =>
      import('./pages/my-appointments/my-appointments.component').then(
        (m) => m.MyAppointmentsComponent,
      ),
  },
  {
    path: 'agenda',
    canActivate: [authGuard, roleGuard('PROFESSIONAL', 'ADMIN'), mustChangePasswordGuard],
    loadComponent: () =>
      import('./pages/agenda/agenda.component').then((m) => m.AgendaComponent),
  },
  {
    path: 'admin',
    canActivate: [authGuard, adminGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./pages/admin/admin-layout.component').then(
        (m) => m.AdminLayoutComponent,
      ),
    children: [
      {
        path: '',
        redirectTo: 'services',
        pathMatch: 'full',
      },
      {
        path: 'services',
        loadComponent: () =>
          import('./pages/admin/services/admin-services.component').then(
            (m) => m.AdminServicesComponent,
          ),
      },
      {
        path: 'professionals',
        loadComponent: () =>
          import(
            './pages/admin/professionals/admin-professionals.component'
          ).then((m) => m.AdminProfessionalsComponent,
          ),
      },
    ],
  },
];
