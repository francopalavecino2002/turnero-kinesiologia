import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators, ValidatorFn, AbstractControl } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/services/auth.service';

// Mirrors the backend rule (@Size(min = 8) on the reset password).
const PASSWORD_MIN_LENGTH = 8;

const passwordsMustMatch: ValidatorFn = (control: AbstractControl) =>
  control.get('password')?.value === control.get('confirmPassword')?.value
    ? null
    : { passwordsMismatch: true };

@Component({
  selector: 'app-reset-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss',
})
export class ResetPasswordComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);

  protected readonly passwordMinLength = PASSWORD_MIN_LENGTH;
  protected readonly token = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly submitted = signal(false);
  protected readonly successMessage = signal<string | null>(null);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly hidePassword = signal(true);

  protected readonly form = this.fb.nonNullable.group(
    {
      password: ['', [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordsMustMatch },
  );

  ngOnInit(): void {
    this.token.set(this.route.snapshot.queryParamMap.get('token'));
  }

  protected togglePassword(): void {
    this.hidePassword.update((hidden) => !hidden);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const token = this.token();
    if (!token) {
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.auth.resetPassword({ token, newPassword: this.form.getRawValue().password }).subscribe({
      next: (response) => {
        this.loading.set(false);
        this.submitted.set(true);
        this.successMessage.set(response.message);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.errorMessage.set(
          error.status === 400
            ? 'El link es inválido, venció o ya fue usado. Pedí uno nuevo.'
            : 'No pudimos restablecer tu contraseña. Intentá de nuevo en unos minutos.',
        );
      },
    });
  }
}
