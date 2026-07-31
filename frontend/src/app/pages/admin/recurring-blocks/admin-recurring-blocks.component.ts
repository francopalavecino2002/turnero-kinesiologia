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
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { HttpErrorResponse } from '@angular/common/http';
import { AdminRecurringBlockService } from '../../../core/services/admin-recurring-block.service';
import { AdminServiceService } from '../../../core/services/admin-service.service';
import { AdminProfessionalService } from '../../../core/services/admin-professional.service';
import {
  RecurringBlockAdmin,
  DayOfWeek,
  Service,
  ProfessionalAdmin,
} from '../../../core/models';

const DAY_LABELS: Record<DayOfWeek, string> = {
  MONDAY: 'Lunes',
  TUESDAY: 'Martes',
  WEDNESDAY: 'Miércoles',
  THURSDAY: 'Jueves',
  FRIDAY: 'Viernes',
  SATURDAY: 'Sábado',
  SUNDAY: 'Domingo',
};

const DAY_ORDER: DayOfWeek[] = [
  'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY',
];

@Component({
  selector: 'app-admin-recurring-blocks',
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
    MatSnackBarModule,
    MatTooltipModule,
  ],
  templateUrl: './admin-recurring-blocks.component.html',
  styleUrl: './admin-recurring-blocks.component.scss',
})
export class AdminRecurringBlocksComponent implements OnInit {
  private readonly adminRecurringBlockService = inject(AdminRecurringBlockService);
  private readonly adminServiceService = inject(AdminServiceService);
  private readonly adminProfessionalService = inject(AdminProfessionalService);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  readonly blocks = signal<RecurringBlockAdmin[]>([]);
  readonly availableServices = signal<Service[]>([]);
  readonly professionals = signal<ProfessionalAdmin[]>([]);
  readonly loading = signal(true);
  readonly includeInactive = signal(false);
  readonly showForm = signal(false);
  readonly editingId = signal<number | null>(null);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly dayLabels = DAY_LABELS;
  readonly dayOrder = DAY_ORDER;

  readonly form = this.fb.nonNullable.group({
    dayOfWeek: ['' as DayOfWeek, Validators.required],
    startTime: ['', Validators.required],
    endTime: ['', Validators.required],
    description: ['', [Validators.required, Validators.maxLength(255)]],
    serviceId: [null as number | null],
    professionalId: [null as number | null],
  });

  get isEditing(): boolean {
    return this.editingId() !== null;
  }

  ngOnInit(): void {
    this.loadBlocks();
    this.loadAvailableServices();
    this.loadProfessionals();
  }

  loadBlocks(): void {
    this.loading.set(true);
    this.adminRecurringBlockService.list(this.includeInactive()).subscribe({
      next: (data) => {
        this.blocks.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Error al cargar bloques recurrentes', 'Cerrar', {
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

  loadProfessionals(): void {
    this.adminProfessionalService.list(true).subscribe({
      next: (data) => this.professionals.set(data),
    });
  }

  toggleIncludeInactive(): void {
    this.includeInactive.update((v) => !v);
    this.loadBlocks();
  }

  openCreateForm(): void {
    this.editingId.set(null);
    this.form.reset({
      dayOfWeek: '' as DayOfWeek,
      startTime: '',
      endTime: '',
      description: '',
      serviceId: null,
      professionalId: null,
    });
    this.errorMessage.set(null);
    this.showForm.set(true);
  }

  openEditForm(block: RecurringBlockAdmin): void {
    this.editingId.set(block.id);
    this.form.reset({
      dayOfWeek: block.dayOfWeek,
      startTime: block.startTime.substring(0, 5),
      endTime: block.endTime.substring(0, 5),
      description: block.description,
      serviceId: block.serviceId,
      professionalId: block.professionalId,
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

    const raw = this.form.getRawValue();
    const payload = {
      dayOfWeek: raw.dayOfWeek as DayOfWeek,
      startTime: raw.startTime + ':00',
      endTime: raw.endTime + ':00',
      description: raw.description,
      serviceId: raw.serviceId,
      professionalId: raw.professionalId,
    };

    const wasEditing = this.isEditing;

    const request$ = wasEditing
      ? this.adminRecurringBlockService.update(this.editingId()!, payload)
      : this.adminRecurringBlockService.create(payload);

    request$.subscribe({
      next: () => {
        this.saving.set(false);
        this.showForm.set(false);
        this.editingId.set(null);
        this.loadBlocks();
        this.snackBar.open(
          wasEditing ? 'Bloque recurrente actualizado' : 'Bloque recurrente creado',
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

  deactivate(block: RecurringBlockAdmin): void {
    if (
      !confirm(
        '¿Desactivar bloque recurrente? Las franjas horarias bloqueadas volverán a estar disponibles para reservas.',
      )
    ) {
      return;
    }

    this.adminRecurringBlockService.deactivate(block.id).subscribe({
      next: () => {
        this.loadBlocks();
        this.snackBar.open('Bloque recurrente desactivado', 'Cerrar', { duration: 3000 });
      },
      error: () => {
        this.snackBar.open('Error al desactivar', 'Cerrar', { duration: 3000 });
      },
    });
  }

  reactivate(block: RecurringBlockAdmin): void {
    this.adminRecurringBlockService.reactivate(block.id).subscribe({
      next: (updated) => {
        this.loadBlocks();
        const msg = 'Bloque recurrente reactivado';
        if (updated.affectedAppointmentsCount > 0) {
          this.snackBar.open(
            msg + '. Afecta a ' + updated.affectedAppointmentsCount + ' turno(s) ya reservado(s).',
            'Cerrar',
            { duration: 5000 },
          );
        } else {
          this.snackBar.open(msg, 'Cerrar', { duration: 3000 });
        }
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

  serviceName(block: RecurringBlockAdmin): string | null {
    if (block.serviceId && block.serviceName) {
      return block.serviceName;
    }
    return null;
  }
}
