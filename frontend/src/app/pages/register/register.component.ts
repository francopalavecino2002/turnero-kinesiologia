import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/services/auth.service';
import { GoogleLoginButtonComponent } from '../../shared/google-login-button/google-login-button.component';

// Mirrors the backend rule (@Size(min = 8) on the register password).
const PASSWORD_MIN_LENGTH = 8;

@Component({
  selector: 'app-register',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatCheckboxModule,
    MatProgressSpinnerModule,
    GoogleLoginButtonComponent,
  ],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
})
export class RegisterComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly passwordMinLength = PASSWORD_MIN_LENGTH;
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly hidePassword = signal(true);

  protected readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required]],
    lastName: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', [Validators.required, Validators.pattern(/^[0-9+()\s-]{6,20}$/)]],
    password: ['', [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH)]],
    // Checked by default — reminders are opt-out.
    notificationsEnabled: [true],
  });

  protected togglePassword(): void {
    this.hidePassword.update((hidden) => !hidden);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const value = this.form.getRawValue();

    this.auth
      .register({
        firstName: value.firstName,
        lastName: value.lastName,
        email: value.email,
        phone: value.phone,
        password: value.password,
        notificationsEnabled: value.notificationsEnabled,
      })
      .subscribe({
        next: () => {
          this.loading.set(false);
          // Registration now requires email verification before the first login, so the
          // new patient is sent to a clear confirmation screen instead of being logged in.
          // `justRegistered` makes that screen start the resend cooldown countdown, matching
          // the server rule that the registration email itself counts as "recently sent".
          this.router.navigate(['/registro-exitoso'], {
            queryParams: { email: value.email, justRegistered: true },
          });
        },
        error: (error: HttpErrorResponse) => {
          this.loading.set(false);
          this.errorMessage.set(this.messageForRegisterError(error));
        },
      });
  }

  private messageForRegisterError(error: HttpErrorResponse): string {
    if (error.status === 409) {
      return 'Ese email ya está registrado';
    }
    if (error.status === 400) {
      return 'Revisá los datos ingresados e intentá de nuevo.';
    }
    return 'No pudimos completar el registro. Intentá de nuevo en unos minutos.';
  }
}
