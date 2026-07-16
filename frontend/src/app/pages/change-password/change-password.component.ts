import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../core/services/auth.service';

/**
 * Placeholder for the first-login "must change password" flow (e.g. the
 * bootstrapped admin / invited professionals). Follow-up: build the real form
 * calling AuthService change-password, then clear mustChangePassword.
 */
@Component({
  selector: 'app-change-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatIconModule],
  styleUrl: './change-password.component.scss',
  template: `
    <main class="eqi-shell">
      <section class="eqi-card">
        <img class="eqi-logo" src="assets/EQI-02.png" alt="eQi – Especialidades Kinésicas" width="120" height="120" />
        <h1 class="eqi-heading">Actualizá tu contraseña</h1>
        <p class="eqi-subheading">
          Por seguridad, necesitás cambiar tu contraseña temporal antes de continuar.
        </p>
        <button class="continuar-btn eqi-btn-block" mat-raised-button color="primary" type="button" (click)="continue()">
          Continuar
        </button>
      </section>
    </main>
  `,
})
export class ChangePasswordComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected continue(): void {
    // Placeholder: no password change yet, just route onward by role.
    const target = this.auth.role() === 'PATIENT' ? '/book' : '/agenda';
    this.router.navigate([target]);
  }
}
