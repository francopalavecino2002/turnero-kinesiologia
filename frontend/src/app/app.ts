import { Component, inject, computed, signal, HostBinding } from '@angular/core';
import { RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs';
import { HeaderComponent } from './shared/header/header.component';
import { WhatsappButtonComponent } from './shared/whatsapp-button/whatsapp-button.component';
import { AuthService } from './core/services/auth.service';

// Auth-flow screens where neither the header nor the floating WhatsApp button adds value: the
// user is mid-authentication (no useful chat yet, and the button would sit on top of a centered
// form). Every other route — public landing and all authenticated screens — shows the button.
const AUTH_FLOW_ROUTES = [
  '/login',
  '/register',
  '/registro-exitoso',
  '/oauth2/callback',
  '/verificar-email',
  '/recuperar-contrasena',
  '/restablecer-password',
];

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, HeaderComponent, WhatsappButtonComponent],
  template: `
    @if (showHeader()) {
      <app-header />
    }
    <router-outlet />
    @if (showWhatsappButton()) {
      <app-whatsapp-button />
    }
  `,
})
export class App {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  private readonly currentUrl = signal(this.router.url);

  @HostBinding('style.--eqi-shell-top')
  get shellTopVar(): string {
    return this.showHeader() ? '64px' : '0px';
  }

  constructor() {
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe((event) => {
        this.currentUrl.set((event as NavigationEnd).urlAfterRedirects);
      });
  }

  readonly showHeader = computed(() => {
    if (!this.authService.isLoggedIn()) return false;
    return !AUTH_FLOW_ROUTES.includes(this.currentUrl());
  });

  readonly showWhatsappButton = computed(() => {
    return !AUTH_FLOW_ROUTES.includes(this.currentUrl());
  });
}
