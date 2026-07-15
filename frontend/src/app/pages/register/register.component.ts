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
        next: () => this.autoLogin(value.email, value.password),
        error: (error: HttpErrorResponse) => {
          this.loading.set(false);
          this.errorMessage.set(this.messageForRegisterError(error));
        },
      });
  }

  // Registration returns no token, so log in with the just-created credentials
  // to establish the session, then land the new patient on the booking screen.
  private autoLogin(email: string, password: string): void {
    this.auth.login({ email, password }).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/book']);
      },
      error: () => {
        // Account exists but the session couldn't be established; send them to
        // login rather than leaving them stuck on the form.
        this.loading.set(false);
        this.router.navigate(['/login']);
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
