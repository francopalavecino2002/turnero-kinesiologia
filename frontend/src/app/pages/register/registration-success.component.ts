import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/services/auth.service';

// Mirrors the server-side resend cooldown (app.tokens.resend-cooldown) so the button can't be
// hammered. It also starts disabled right after registration because the registration email
// itself counts against the cooldown.
const RESEND_COOLDOWN_SECONDS = 60;

@Component({
  selector: 'app-registration-success',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './registration-success.component.html',
  styleUrl: './registration-success.component.scss',
})
export class RegistrationSuccessComponent implements OnInit, OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  protected readonly email = signal<string | null>(null);
  protected readonly justRegistered = signal(false);
  protected readonly resending = signal(false);
  protected readonly resendMessage = signal<string | null>(null);
  protected readonly cooldownRemaining = signal(0);

  private cooldownTimer?: ReturnType<typeof setInterval>;

  ngOnInit(): void {
    this.email.set(this.route.snapshot.queryParamMap.get('email'));
    // After registration the server just issued a verification token, so the resend cooldown is
    // active right away: start the countdown immediately. When the user arrived from a failed
    // login the token is older, so the button is enabled from the start.
    this.justRegistered.set(this.route.snapshot.queryParamMap.get('justRegistered') === 'true');
    if (this.justRegistered()) {
      this.startCooldown(RESEND_COOLDOWN_SECONDS);
    }
  }

  ngOnDestroy(): void {
    this.stopCooldown();
  }

  protected resend(): void {
    const email = this.email();
    if (!email || this.cooldownRemaining() > 0 || this.resending()) {
      return;
    }

    this.resending.set(true);
    this.resendMessage.set(null);

    this.auth.resendVerification(email).subscribe({
      next: () => {
        this.resending.set(false);
        this.resendMessage.set(
          'Te reenviamos el link de verificación. Revisá tu casilla y la carpeta de spam.',
        );
        this.startCooldown(RESEND_COOLDOWN_SECONDS);
      },
      error: () => {
        this.resending.set(false);
        this.resendMessage.set('No pudimos reenviar el link. Intentá de nuevo en unos minutos.');
      },
    });
  }

  private startCooldown(seconds: number): void {
    this.stopCooldown();
    this.cooldownRemaining.set(seconds);
    this.cooldownTimer = setInterval(() => {
      const next = this.cooldownRemaining() - 1;
      if (next <= 0) {
        this.stopCooldown();
        this.cooldownRemaining.set(0);
      } else {
        this.cooldownRemaining.set(next);
      }
    }, 1000);
  }

  private stopCooldown(): void {
    if (this.cooldownTimer) {
      clearInterval(this.cooldownTimer);
      this.cooldownTimer = undefined;
    }
  }
}
