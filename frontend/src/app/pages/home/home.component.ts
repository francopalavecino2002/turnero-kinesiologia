import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/services/auth.service';
import { CatalogService } from '../../core/services/catalog.service';
import { Service } from '../../core/models';

@Component({
  selector: 'app-home',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly catalogService = inject(CatalogService);

  readonly isLoggedIn = this.authService.isLoggedIn;

  readonly services = signal<Service[]>([]);
  readonly loadingServices = signal(true);
  readonly servicesError = signal(false);

  readonly currentYear = new Date().getFullYear();

  ngOnInit(): void {
    this.catalogService.getServices().subscribe({
      next: (data) => {
        this.services.set(data.filter((s) => s.active));
        this.loadingServices.set(false);
      },
      error: () => {
        this.servicesError.set(true);
        this.loadingServices.set(false);
      },
    });
  }
}
