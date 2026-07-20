import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/services/auth.service';
import { Role } from '../../core/models';

const PASSWORD_MIN_LENGTH = 8;

@Component({
  selector: 'app-change-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  styleUrl: './change-password.component.scss',
  template: `
    <main class="eqi-shell">
      <section class="eqi-card">
        <img
          class="eqi-logo"
          src="assets/EQI-02.png"
          alt="eQi – Especialidades Kinésicas"
          width="120"
          height="120"
        />

        <h1 class="eqi-heading">Actualizá tu contraseña</h1>

        @if (forcedMode()) {
          <p class="eqi-subheading">
            Por seguridad, necesitás cambiar tu contraseña temporal antes de continuar.
          </p>
        } @else {
          <p class="eqi-subheading">Ingresá tu contraseña actual y la nueva contraseña.</p>
        }

        @if (errorMessage(); as message) {
          <div class="eqi-error" role="alert">
            <mat-icon aria-hidden="true">error_outline</mat-icon>
            <span>{{ message }}</span>
          </div>
        }

        <form class="eqi-form" [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <mat-form-field appearance="outline">
            <mat-label>Contraseña actual</mat-label>
            <input
              matInput
              [type]="hideCurrent() ? 'password' : 'text'"
              autocomplete="current-password"
              formControlName="currentPassword"
              required
            />
            <button
              matSuffix
              mat-icon-button
              type="button"
              (click)="hideCurrent.update(v => !v)"
              [attr.aria-label]="hideCurrent() ? 'Mostrar contraseña' : 'Ocultar contraseña'"
              [attr.aria-pressed]="!hideCurrent()"
            >
              <mat-icon aria-hidden="true">{{ hideCurrent() ? 'visibility_off' : 'visibility' }}</mat-icon>
            </button>
            @if (form.controls.currentPassword.hasError('required')) {
              <mat-error>Ingresá tu contraseña actual</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Nueva contraseña</mat-label>
            <input
              matInput
              [type]="hideNew() ? 'password' : 'text'"
              autocomplete="new-password"
              formControlName="newPassword"
              required
            />
            <button
              matSuffix
              mat-icon-button
              type="button"
              (click)="hideNew.update(v => !v)"
              [attr.aria-label]="hideNew() ? 'Mostrar contraseña' : 'Ocultar contraseña'"
              [attr.aria-pressed]="!hideNew()"
            >
              <mat-icon aria-hidden="true">{{ hideNew() ? 'visibility_off' : 'visibility' }}</mat-icon>
            </button>
            @if (form.controls.newPassword.hasError('required')) {
              <mat-error>Ingresá la nueva contraseña</mat-error>
            } @else if (form.controls.newPassword.hasError('minlength')) {
              <mat-error>La contraseña debe tener al menos {{ passwordMinLength }} caracteres</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Confirmar nueva contraseña</mat-label>
            <input
              matInput
              [type]="hideConfirm() ? 'password' : 'text'"
              autocomplete="new-password"
              formControlName="confirmPassword"
              required
            />
            <button
              matSuffix
              mat-icon-button
              type="button"
              (click)="hideConfirm.update(v => !v)"
              [attr.aria-label]="hideConfirm() ? 'Mostrar contraseña' : 'Ocultar contraseña'"
              [attr.aria-pressed]="!hideConfirm()"
            >
              <mat-icon aria-hidden="true">{{ hideConfirm() ? 'visibility_off' : 'visibility' }}</mat-icon>
            </button>
            @if (form.controls.confirmPassword.hasError('required')) {
              <mat-error>Confirmá la nueva contraseña</mat-error>
            } @else if (form.hasError('passwordMismatch')) {
              <mat-error>Las contraseñas no coinciden</mat-error>
            }
          </mat-form-field>

          @if (sameAsCurrent()) {
            <div class="eqi-warning" role="status">
              <mat-icon aria-hidden="true">info_outline</mat-icon>
              <span>La nueva contraseña es igual a la actual.</span>
            </div>
          }

          <button
            class="eqi-btn-block"
            mat-raised-button
            color="primary"
            type="submit"
            [disabled]="loading()"
          >
            @if (loading()) {
              <mat-progress-spinner diameter="22" mode="indeterminate" aria-label="Guardando" />
            } @else {
              Guardar contraseña
            }
          </button>
        </form>

        @if (!forcedMode()) {
          <p class="eqi-alt">
            <a href="javascript:void(0)" (click)="goBack()">Cancelar</a>
          </p>
        }
      </section>
    </main>
  `,
})
export class ChangePasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly passwordMinLength = PASSWORD_MIN_LENGTH;
  protected readonly forcedMode = this.auth.mustChangePassword;
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly hideCurrent = signal(true);
  protected readonly hideNew = signal(true);
  protected readonly hideConfirm = signal(true);

  protected readonly form = this.fb.nonNullable.group(
    {
      currentPassword: ['', [Validators.required]],
      newPassword: ['', [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: this.passwordMatchValidator },
  );

  protected sameAsCurrent(): boolean {
    const current = this.form.controls.currentPassword.value;
    const next = this.form.controls.newPassword.value;
    return current.length > 0 && next.length > 0 && current === next;
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const { currentPassword, newPassword } = this.form.getRawValue();

    this.auth.changePassword({ currentPassword, newPassword }).subscribe({
      next: () => {
        this.loading.set(false);
        if (!this.forcedMode()) {
          this.snackBar.open('Contraseña actualizada', undefined, { duration: 3000 });
        }
        this.navigateToHome();
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        if (error.status === 401) {
          this.errorMessage.set('La contraseña actual es incorrecta');
        } else {
          this.errorMessage.set('No pudimos cambiar la contraseña. Intentá de nuevo en unos minutos.');
        }
      },
    });
  }

  protected goBack(): void {
    this.location.back();
  }

  private navigateToHome(): void {
    const target = this.auth.role() === 'PATIENT' ? '/book' : '/agenda';
    this.router.navigate([target]);
  }

  private passwordMatchValidator(control: AbstractControl): ValidationErrors | null {
    const newPass = control.get('newPassword');
    const confirm = control.get('confirmPassword');
    if (!newPass || !confirm) return null;

    if (newPass.value !== confirm.value) {
      confirm.setErrors({ ...confirm.errors, passwordMismatch: true });
      return { passwordMismatch: true };
    }

    if (confirm.hasError('passwordMismatch')) {
      const { passwordMismatch, ...rest } = confirm.errors!;
      confirm.setErrors(Object.keys(rest).length ? rest : null);
    }

    return null;
  }
}
