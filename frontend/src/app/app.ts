import { Component, inject, computed, signal, HostBinding } from '@angular/core';
import { RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs';
import { HeaderComponent } from './shared/header/header.component';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, HeaderComponent],
  template: `
    @if (showHeader()) {
      <app-header />
    }
    <router-outlet />
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
    const url = this.currentUrl();
    const hideOn = ['/login', '/register', '/'];
    return !hideOn.includes(url);
  });
}
