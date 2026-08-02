import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/services/auth.service';
import { TokenStorageService } from '../../core/services/token-storage.service';
import { ErrorResponse, Role } from '../../core/models';

@Component({
  selector: 'app-oauth2-callback',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './oauth2-callback.component.html',
  styleUrl: './oauth2-callback.component.scss',
})
export class OAuth2CallbackComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly tokenStorage = inject(TokenStorageService);

  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    const error = this.route.snapshot.queryParamMap.get('error');
    if (error) {
      this.fail(this.messageForError(error));
      return;
    }

    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.fail('El inicio de sesión con Google no se completó. Volvé a intentarlo.');
      return;
    }

    this.auth.handleOAuth2Token(token).subscribe({
      next: (user) => {
        this.loading.set(false);
        // First-login flow for bootstrapped/invited accounts (e.g. the admin).
        if (user.mustChangePassword) {
          this.router.navigate(['/change-password']);
          return;
        }
        this.redirectByRole(user.role);
      },
      error: (httpError: HttpErrorResponse) => {
        // A 401 clears the token in the auth interceptor and already bounces to /login; any
        // other failure leaves stale state behind, so wipe it explicitly.
        this.tokenStorage.clear();
        const code = (httpError.error as ErrorResponse | undefined)?.code;
        this.fail(
          httpError.status === 401 && code === 'EMAIL_NOT_VERIFIED'
            ? 'Tu email todavía no está verificado. Ingresá con email y contraseña para verificar tu cuenta.'
            : 'No pudimos completar el inicio de sesión con Google. Volvé a intentarlo.',
        );
      },
    });
  }

  private fail(message: string): void {
    this.loading.set(false);
    this.errorMessage.set(message);
  }

  private redirectByRole(role: Role): void {
    const target = role === 'PATIENT' ? '/book' : '/agenda';
    this.router.navigate([target]);
  }

  private messageForError(code: string): string {
    switch (code) {
      case 'access_denied':
        return 'Cancelaste el inicio de sesión con Google.';
      case 'email_not_verified':
        return 'Google no pudo confirmar tu email. Probá con otra cuenta o registrate con email y contraseña.';
      case 'invalid_request':
        return 'La solicitud de ingreso con Google no fue válida. Volvé a intentarlo.';
      default:
        return 'No pudimos completar el inicio de sesión con Google. Volvé a intentarlo.';
    }
  }
}
