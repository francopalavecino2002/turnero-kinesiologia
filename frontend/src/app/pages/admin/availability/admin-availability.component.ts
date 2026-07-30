import {
  ChangeDetectionStrategy,
  Component,
  computed,
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
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { AdminAvailabilityService } from '../../../core/services/admin-availability.service';
import { AdminProfessionalService } from '../../../core/services/admin-professional.service';
import {
  Availability,
  DayOfWeek,
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
  selector: 'app-admin-availability',
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
    MatProgressSpinnerModule,
    MatSnackBarModule,
  ],
  templateUrl: './admin-availability.component.html',
  styleUrl: './admin-availability.component.scss',
})
export class AdminAvailabilityComponent implements OnInit {
  private readonly adminAvailabilityService = inject(AdminAvailabilityService);
  private readonly adminProfessionalService = inject(AdminProfessionalService);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  readonly professionals = signal<ProfessionalAdmin[]>([]);
  readonly selectedProfessionalId = signal<number | null>(null);
  readonly availabilities = signal<Availability[]>([]);
  readonly loading = signal(false);
  readonly showForm = signal(false);
  readonly editingId = signal<number | null>(null);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly applyTo = signal<'all' | 'specific'>('all');

  readonly selectedProfessional = computed(() =>
    this.professionals().find(p => p.id === this.selectedProfessionalId())
  );

  readonly servicesForSelected = computed(() =>
    this.selectedProfessional()?.services ?? []
  );

  readonly dayLabels = DAY_LABELS;
  readonly dayOrder = DAY_ORDER;

  readonly availabilitiesByDay = computed(() => {
    const map = new Map<DayOfWeek, Availability[]>();
    for (const day of DAY_ORDER) {
      map.set(day, []);
    }
    for (const a of this.availabilities()) {
      const list = map.get(a.dayOfWeek);
      if (list) {
        list.push(a);
      }
    }
    return map;
  });

  readonly form = this.fb.nonNullable.group({
    dayOfWeek: ['' as DayOfWeek, Validators.required],
    startTime: ['', Validators.required],
    endTime: ['', Validators.required],
    serviceId: [null as number | null],
  });

  constructor() {
    this.form.controls.serviceId.disable();
    this.applyTo.set('all');
  }

  ngOnInit(): void {
    this.loadProfessionals();
  }

  private loadProfessionals(): void {
    this.adminProfessionalService.list(true).subscribe({
      next: (data) => this.professionals.set(data),
      error: () => this.snackBar.open('Error al cargar profesionales', 'Cerrar', { duration: 3000 }),
    });
  }

  onProfessionalChange(id: number): void {
    this.selectedProfessionalId.set(id);
    this.availabilities.set([]);
    this.cancelForm();
    if (id) {
      this.loadAvailabilities(id);
    }
  }

  private loadAvailabilities(professionalId: number): void {
    this.loading.set(true);
    this.adminAvailabilityService.list(professionalId).subscribe({
      next: (data) => {
        this.availabilities.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Error al cargar disponibilidades', 'Cerrar', { duration: 3000 });
      },
    });
  }

  get isEditing(): boolean {
    return this.editingId() !== null;
  }

  openCreateForm(): void {
    this.editingId.set(null);
    this.form.reset({ dayOfWeek: '' as DayOfWeek, startTime: '', endTime: '', serviceId: null });
    this.applyTo.set('all');
    this.form.controls.serviceId.disable();
    this.errorMessage.set(null);
    this.showForm.set(true);
  }

  openEditForm(availability: Availability): void {
    this.editingId.set(availability.id);
    const isSpecific = availability.serviceId != null;
    this.applyTo.set(isSpecific ? 'specific' : 'all');
    this.form.reset({
      dayOfWeek: availability.dayOfWeek,
      startTime: availability.startTime.substring(0, 5),
      endTime: availability.endTime.substring(0, 5),
      serviceId: availability.serviceId,
    });
    if (isSpecific) {
      this.form.controls.serviceId.enable();
    } else {
      this.form.controls.serviceId.disable();
    }
    this.errorMessage.set(null);
    this.showForm.set(true);
  }

  cancelForm(): void {
    this.showForm.set(false);
    this.editingId.set(null);
    this.errorMessage.set(null);
  }

  onApplyToChange(value: 'all' | 'specific'): void {
    this.applyTo.set(value);
    if (value === 'specific') {
      this.form.controls.serviceId.enable();
      if (this.servicesForSelected().length > 0) {
        this.form.patchValue({ serviceId: this.servicesForSelected()[0].id });
      }
    } else {
      this.form.controls.serviceId.disable();
      this.form.patchValue({ serviceId: null });
    }
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const professionalId = this.selectedProfessionalId();
    if (!professionalId) {
      return;
    }

    this.saving.set(true);
    this.errorMessage.set(null);

    const raw = this.form.getRawValue();
    const serviceId = this.applyTo() === 'specific' ? raw.serviceId : null;

    const payload = {
      professionalId,
      dayOfWeek: raw.dayOfWeek as DayOfWeek,
      startTime: raw.startTime + ':00',
      endTime: raw.endTime + ':00',
      serviceId,
    };

    const wasEditing = this.isEditing;

    const request$ = wasEditing
      ? this.adminAvailabilityService.update(this.editingId()!, {
          dayOfWeek: payload.dayOfWeek,
          startTime: payload.startTime,
          endTime: payload.endTime,
          serviceId: payload.serviceId,
        })
      : this.adminAvailabilityService.create(payload);

    request$.subscribe({
      next: () => {
        this.saving.set(false);
        this.showForm.set(false);
        this.editingId.set(null);
        this.loadAvailabilities(professionalId);
        this.snackBar.open(
          wasEditing ? 'Disponibilidad actualizada' : 'Disponibilidad creada',
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

  deleteAvailability(availability: Availability): void {
    if (!confirm(`¿Eliminar esta franja horaria?`)) {
      return;
    }

    this.adminAvailabilityService.delete(availability.id).subscribe({
      next: () => {
        const profId = this.selectedProfessionalId();
        if (profId) {
          this.loadAvailabilities(profId);
        }
        this.snackBar.open('Franja eliminada', 'Cerrar', { duration: 3000 });
      },
      error: (error: HttpErrorResponse) => {
        this.snackBar.open(
          error.error?.message ?? 'Error al eliminar',
          'Cerrar',
          { duration: 3000 },
        );
      },
    });
  }

  description(availability: Availability): string {
    if (availability.serviceId && availability.serviceName) {
      return `Solo ${availability.serviceName}`;
    }
    return 'General';
  }
}
