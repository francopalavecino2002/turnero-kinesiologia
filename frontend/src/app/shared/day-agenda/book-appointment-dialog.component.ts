import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { SlicePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AppointmentService } from '../../core/services/appointment.service';
import { PatientService } from '../../core/services/patient.service';
import {
  AvailableSlot,
  PatientSearchResult,
  Professional,
  ServiceSummary,
  StaffBookAppointmentRequest,
} from '../../core/models';

type PatientMode = 'registered' | 'guest';

export interface BookAppointmentDialogData {
  professional: Professional;
}

function toIsoDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

@Component({
  selector: 'app-book-appointment-dialog',
  templateUrl: './book-appointment-dialog.component.html',
  styleUrl: './book-appointment-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [provideNativeDateAdapter(), { provide: MAT_DATE_LOCALE, useValue: 'es-AR' }],
  imports: [
    ReactiveFormsModule,
    SlicePipe,
    MatAutocompleteModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatChipsModule,
    MatDatepickerModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
})
export class BookAppointmentDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<BookAppointmentDialogComponent>);
  private readonly data = inject<BookAppointmentDialogData>(MAT_DIALOG_DATA);
  private readonly appointmentService = inject(AppointmentService);
  private readonly patientService = inject(PatientService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  // The dialog always books on the current user's own agenda - DayAgendaComponent resolves this
  // professional (and gates opening the dialog at all) before creating it.
  readonly professional = this.data.professional;
  readonly offeredServices = this.professional.services;
  readonly skipServiceStep = this.offeredServices.length === 1;

  readonly today = new Date();
  readonly minDate = new Date(this.today.getFullYear(), this.today.getMonth(), this.today.getDate());
  readonly dateControl = this.fb.nonNullable.control<Date>(this.minDate);

  readonly selectedService = signal<ServiceSummary | null>(
    this.skipServiceStep ? this.offeredServices[0] : null,
  );

  readonly slots = signal<AvailableSlot[]>([]);
  readonly loadingSlots = signal(false);
  readonly selectedSlot = signal<AvailableSlot | null>(null);

  readonly patientMode = signal<PatientMode>('registered');

  readonly patientSearchControl = this.fb.nonNullable.control<string | PatientSearchResult>('');
  readonly patientResults = signal<PatientSearchResult[]>([]);
  readonly searchingPatients = signal(false);
  readonly selectedPatient = signal<PatientSearchResult | null>(null);

  readonly guestForm = this.fb.nonNullable.group({
    name: ['', Validators.required],
    phone: ['', Validators.required],
    email: [''],
  });

  private readonly guestFormValid = toSignal(
    this.guestForm.statusChanges.pipe(map(() => this.guestForm.valid)),
    { initialValue: this.guestForm.valid },
  );

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly canSubmit = computed(() => {
    if (!this.selectedService() || !this.selectedSlot()) {
      return false;
    }
    if (this.patientMode() === 'registered') {
      return this.selectedPatient() !== null;
    }
    return this.guestFormValid();
  });

  constructor() {
    if (this.skipServiceStep) {
      this.fetchSlots();
    }

    this.patientSearchControl.valueChanges
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((term) => {
          if (typeof term !== 'string') {
            // A patient was just selected: the control now holds the selected
            // PatientSearchResult (see [displayWith]), not user-typed text.
            this.searchingPatients.set(false);
            return of([] as PatientSearchResult[]);
          }
          const trimmed = term.trim();
          if (trimmed.length < 2) {
            this.searchingPatients.set(false);
            return of([] as PatientSearchResult[]);
          }
          this.searchingPatients.set(true);
          return this.patientService.search(trimmed).pipe(
            catchError((err) => {
              console.error('No se pudieron buscar pacientes', err);
              this.errorMessage.set('No pudimos buscar pacientes. Probá de nuevo.');
              return of([] as PatientSearchResult[]);
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((results) => {
        this.patientResults.set(results);
        this.searchingPatients.set(false);
      });
  }

  selectService(service: ServiceSummary): void {
    this.selectedService.set(service);
    this.selectedSlot.set(null);
    this.slots.set([]);
    this.fetchSlots();
  }

  onDateSelected(date: Date | null): void {
    if (!date) {
      return;
    }
    this.dateControl.setValue(date);
    this.fetchSlots();
  }

  private fetchSlots(): void {
    const service = this.selectedService();
    const date = this.dateControl.value;
    if (!service || !date) {
      return;
    }

    this.selectedSlot.set(null);
    this.slots.set([]);
    this.loadingSlots.set(true);

    this.appointmentService.getAvailableSlots(this.professional.id, service.id, toIsoDate(date)).subscribe({
      next: (slots) => {
        this.slots.set(slots);
        this.loadingSlots.set(false);
      },
      error: (err) => {
        console.error('No se pudieron cargar los horarios disponibles', err);
        this.loadingSlots.set(false);
        this.errorMessage.set('No pudimos cargar los horarios disponibles. Probá de nuevo.');
      },
    });
  }

  selectSlot(slot: AvailableSlot): void {
    this.selectedSlot.set(slot);
  }

  setPatientMode(mode: PatientMode): void {
    this.patientMode.set(mode);
    this.selectedPatient.set(null);
    this.patientSearchControl.setValue('');
    this.patientResults.set([]);
    this.guestForm.reset({ name: '', phone: '', email: '' });
  }

  selectPatient(patient: PatientSearchResult): void {
    this.selectedPatient.set(patient);
    this.patientResults.set([]);
  }

  clearSelectedPatient(): void {
    this.selectedPatient.set(null);
    this.patientSearchControl.setValue('');
  }

  displayPatient(patient: PatientSearchResult | string | null): string {
    if (!patient) {
      return '';
    }
    return typeof patient === 'string' ? patient : patient.fullName;
  }

  submit(): void {
    const service = this.selectedService();
    const slot = this.selectedSlot();
    if (!service || !slot || !this.canSubmit()) {
      return;
    }

    const request: StaffBookAppointmentRequest = {
      serviceId: service.id,
      professionalId: this.professional.id,
      dateTime: slot.startTime,
      ...(this.patientMode() === 'registered'
        ? { patientId: this.selectedPatient()!.id }
        : {
            guestPatient: {
              name: this.guestForm.value.name!.trim(),
              phone: this.guestForm.value.phone!.trim(),
              email: this.guestForm.value.email?.trim() || undefined,
            },
          }),
    };

    this.submitting.set(true);
    this.errorMessage.set(null);

    this.appointmentService.staffBook(request).subscribe({
      next: () => {
        this.submitting.set(false);
        this.dialogRef.close(true);
      },
      error: (err: { status?: number; error?: { message?: string } }) => {
        this.submitting.set(false);
        this.errorMessage.set(
          err?.error?.message ?? 'No se pudo agendar el turno. Intentá nuevamente.',
        );
      },
    });
  }

  close(): void {
    this.dialogRef.close(false);
  }
}
