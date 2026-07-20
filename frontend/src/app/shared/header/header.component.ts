import {
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { BreakpointObserver } from '@angular/cdk/layout';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { AuthService } from '../../core/services/auth.service';
import { filter } from 'rxjs';

interface NavLink {
  label: string;
  route: string;
  icon: string;
}

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatDividerModule,
  ],
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss',
})
export class HeaderComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly breakpointObserver = inject(BreakpointObserver);

  readonly user = this.authService.user;
  readonly isLoggedIn = this.authService.isLoggedIn;
  readonly role = this.authService.role;
  readonly mustChangePassword = this.authService.mustChangePassword;

  readonly isMobile = signal(false);

  readonly navLinks = computed<NavLink[]>(() => {
    if (this.mustChangePassword()) return [];
    const r = this.role();
    if (r === 'PATIENT') {
      return [
        { label: 'Reservar turno', route: '/book', icon: 'event_available' },
        {
          label: 'Mis turnos',
          route: '/my-appointments',
          icon: 'calendar_month',
        },
      ];
    }
    if (r === 'PROFESSIONAL' || r === 'ADMIN') {
      return [{ label: 'Agenda', route: '/agenda', icon: 'view_day' }];
    }
    return [];
  });

  readonly homeRoute = computed(() => {
    const r = this.role();
    if (r === 'PATIENT') return '/book';
    return '/agenda';
  });

  readonly displayName = computed(() => {
    const u = this.user();
    return u ? `${u.firstName} ${u.lastName}` : '';
  });

  readonly userInitial = computed(() => {
    const u = this.user();
    if (!u) return '';
    return (u.firstName?.[0] ?? '') + (u.lastName?.[0] ?? '');
  });

  constructor() {
    this.breakpointObserver
      .observe('(max-width: 639px)')
      .subscribe((result) => {
        this.isMobile.set(result.matches);
      });
  }

  logout(): void {
    this.authService.logout();
  }
}
