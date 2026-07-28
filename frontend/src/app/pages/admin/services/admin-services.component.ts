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
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { AdminServiceService } from '../../../core/services/admin-service.service';
import { Service } from '../../../core/models';

@Component({
  selector: 'app-admin-services',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSlideToggleModule,
    MatProgressSpinnerModule,
    MatDialogModule,
    MatSnackBarModule,
  ],
  templateUrl: './admin-services.component.html',
  styleUrl: './admin-services.component.scss',
})
export class AdminServicesComponent implements OnInit {
  private readonly adminService = inject(AdminServiceService);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  readonly services = signal<Service[]>([]);
  readonly loading = signal(true);
  readonly includeInactive = signal(false);
  readonly showForm = signal(false);
  readonly editingId = signal<number | null>(null);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    durationMinutes: [60, [Validators.required, Validators.min(5), Validators.max(480)]],
  });

  get isEditing(): boolean {
    return this.editingId() !== null;
  }

  ngOnInit(): void {
    this.loadServices();
  }

  loadServices(): void {
    this.loading.set(true);
    this.adminService.list(this.includeInactive()).subscribe({
      next: (data) => {
        this.services.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Error al cargar servicios', 'Cerrar', {
          duration: 3000,
        });
      },
    });
  }

  toggleIncludeInactive(): void {
    this.includeInactive.update((v) => !v);
    this.loadServices();
  }

  openCreateForm(): void {
    this.editingId.set(null);
    this.form.reset({ name: '', durationMinutes: 60 });
    this.errorMessage.set(null);
    this.showForm.set(true);
  }

  openEditForm(service: Service): void {
    this.editingId.set(service.id);
    this.form.reset({
      name: service.name,
      durationMinutes: service.durationMinutes,
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

    const { name, durationMinutes } = this.form.getRawValue();
    const wasEditing = this.isEditing;

    const request$ = wasEditing
      ? this.adminService.update(this.editingId()!, { name, durationMinutes })
      : this.adminService.create({ name, durationMinutes });

    request$.subscribe({
      next: () => {
        this.saving.set(false);
        this.showForm.set(false);
        this.editingId.set(null);
        this.loadServices();
        this.snackBar.open(
          wasEditing ? 'Servicio actualizado' : 'Servicio creado',
          'Cerrar',
          { duration: 3000 },
        );
      },
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.errorMessage.set(
          error.error?.message ?? 'Ocurrió un error al guardar',
        );
      },
    });
  }

  deactivate(service: Service): void {
    if (
      !confirm(
        '¿Seguro? Los turnos ya reservados no se ven afectados, solo dejará de ofrecerse para nuevas reservas.',
      )
    ) {
      return;
    }

    this.adminService.deactivate(service.id).subscribe({
      next: () => {
        this.loadServices();
        this.snackBar.open('Servicio desactivado', 'Cerrar', { duration: 3000 });
      },
      error: () => {
        this.snackBar.open('Error al desactivar', 'Cerrar', { duration: 3000 });
      },
    });
  }

  reactivate(service: Service): void {
    this.adminService.reactivate(service.id).subscribe({
      next: () => {
        this.loadServices();
        this.snackBar.open('Servicio reactivado', 'Cerrar', { duration: 3000 });
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
