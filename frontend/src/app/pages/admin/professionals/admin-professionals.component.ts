import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { HttpErrorResponse } from '@angular/common/http';
import { AdminProfessionalService } from '../../../core/services/admin-professional.service';
import { AdminServiceService } from '../../../core/services/admin-service.service';
import {
  ProfessionalAdmin,
  Service,
  ProfessionalCreatedResponse,
} from '../../../core/models';

@Component({
  selector: 'app-admin-professionals',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatProgressSpinnerModule,
    MatDialogModule,
    MatSnackBarModule,
    MatTooltipModule,
  ],
  templateUrl: './admin-professionals.component.html',
  styleUrl: './admin-professionals.component.scss',
})
export class AdminProfessionalsComponent implements OnInit {
  private readonly adminProfessionalService = inject(AdminProfessionalService);
  private readonly adminServiceService = inject(AdminServiceService);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  readonly professionals = signal<ProfessionalAdmin[]>([]);
  readonly availableServices = signal<Service[]>([]);
  readonly loading = signal(true);
  readonly includeInactive = signal(false);
  readonly showForm = signal(false);
  readonly editingId = signal<number | null>(null);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly showPasswordModal = signal(false);
  readonly createdProfessional = signal<ProfessionalCreatedResponse | null>(
    null,
  );
  readonly passwordCopied = signal(false);

  readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    serviceIds: this.fb.nonNullable.control<number[]>([]),
  });

  get isEditing(): boolean {
    return this.editingId() !== null;
  }

  ngOnInit(): void {
    this.loadProfessionals();
    this.loadAvailableServices();
  }

  loadProfessionals(): void {
    this.loading.set(true);
    this.adminProfessionalService.list(this.includeInactive()).subscribe({
      next: (data) => {
        this.professionals.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Error al cargar profesionales', 'Cerrar', {
          duration: 3000,
        });
      },
    });
  }

  loadAvailableServices(): void {
    this.adminServiceService.list(false).subscribe({
      next: (data) => this.availableServices.set(data),
    });
  }

  toggleIncludeInactive(): void {
    this.includeInactive.update((v) => !v);
    this.loadProfessionals();
  }

  openCreateForm(): void {
    this.editingId.set(null);
    this.form.reset({
      firstName: '',
      lastName: '',
      email: '',
      serviceIds: [],
    });
    this.errorMessage.set(null);
    this.showForm.set(true);
  }

  openEditForm(professional: ProfessionalAdmin): void {
    this.editingId.set(professional.id);
    this.form.reset({
      firstName: professional.firstName,
      lastName: professional.lastName,
      email: professional.email,
      serviceIds: professional.services.map((s) => s.id),
    });
    this.errorMessage.set(null);
    this.showForm.set(true);
  }

  cancelForm(): void {
    this.showForm.set(false);
    this.editingId.set(null);
    this.errorMessage.set(null);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.errorMessage.set(null);

    const { firstName, lastName, email, serviceIds } =
      this.form.getRawValue();
    const wasEditing = this.isEditing;

    if (wasEditing) {
      this.adminProfessionalService
        .update(this.editingId()!, { firstName, lastName, serviceIds })
        .subscribe({
          next: () => {
            this.saving.set(false);
            this.showForm.set(false);
            this.editingId.set(null);
            this.loadProfessionals();
            this.snackBar.open('Profesional actualizado', 'Cerrar', {
              duration: 3000,
            });
          },
          error: (error: HttpErrorResponse) => {
            this.saving.set(false);
            this.errorMessage.set(
              error.error?.message ?? 'Ocurrió un error al guardar',
            );
          },
        });
    } else {
      this.adminProfessionalService
        .create({ firstName, lastName, email, serviceIds })
        .subscribe({
          next: (response) => {
            this.saving.set(false);
            this.showForm.set(false);
            this.createdProfessional.set(response);
            this.passwordCopied.set(false);
            this.showPasswordModal.set(true);
            this.loadProfessionals();
          },
          error: (error: HttpErrorResponse) => {
            this.saving.set(false);
            this.errorMessage.set(
              error.error?.message ?? 'Ocurrió un error al guardar',
            );
          },
        });
    }
  }

  copyPassword(): void {
    const password = this.createdProfessional()?.temporaryPassword;
    if (password) {
      navigator.clipboard.writeText(password).then(() => {
        this.passwordCopied.set(true);
      });
    }
  }

  closePasswordModal(): void {
    this.showPasswordModal.set(false);
    this.createdProfessional.set(null);
    this.passwordCopied.set(false);
  }

  deactivate(professional: ProfessionalAdmin): void {
    if (
      !confirm(
        '¿Desactivar a ' +
          professional.firstName +
          ' ' +
          professional.lastName +
          '? Los turnos ya reservados no se ven afectados, pero no podrá recibir turnos nuevos.',
      )
    ) {
      return;
    }

    this.adminProfessionalService.deactivate(professional.id).subscribe({
      next: () => {
        this.loadProfessionals();
        this.snackBar.open('Profesional desactivado', 'Cerrar', {
          duration: 3000,
        });
      },
      error: () => {
        this.snackBar.open('Error al desactivar', 'Cerrar', { duration: 3000 });
      },
    });
  }

  reactivate(professional: ProfessionalAdmin): void {
    this.adminProfessionalService.reactivate(professional.id).subscribe({
      next: () => {
        this.loadProfessionals();
        this.snackBar.open('Profesional reactivado', 'Cerrar', {
          duration: 3000,
        });
      },
      error: (error: HttpErrorResponse) => {
        this.snackBar.open(
          error.error?.message ?? 'Error al reactivar',
          'Cerrar',
          { duration: 3000 },
        );
      },
    });
  }
}
