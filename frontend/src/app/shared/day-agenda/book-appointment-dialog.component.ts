import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SlicePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/services/auth.service';
import { CatalogService } from '../../core/services/catalog.service';
import { AppointmentService } from '../../core/services/appointment.service';
import { PatientService } from '../../core/services/patient.service';
import {
  AvailableSlot,
  PatientSearchResult,
  Professional,
  Service,
  StaffBookAppointmentRequest,
} from '../../core/models';

type PatientMode = 'registered' | 'guest';

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
  private readonly auth = inject(AuthService);
  private readonly catalogService = inject(CatalogService);
  private readonly appointmentService = inject(AppointmentService);
  private readonly patientService = inject(PatientService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly isAdmin = computed(() => this.auth.role() === 'ADMIN');

  readonly today = new Date();
  readonly minDate = new Date(this.today.getFullYear(), this.today.getMonth(), this.today.getDate());
  readonly dateControl = this.fb.nonNullable.control<Date>(this.minDate);

  readonly services = signal<Service[]>([]);
  readonly loadingServices = signal(true);
  readonly selectedService = signal<Service | null>(null);

  // Only populated/relevant for ADMIN. For a PROFESSIONAL caller, myProfessional() is the only
  // option and is never shown as a picker.
  readonly professionals = signal<Professional[]>([]);
  readonly loadingProfessionals = signal(false);
  readonly myProfessional = signal<Professional | null>(null);
  readonly selectedProfessional = signal<Professional | null>(null);

  readonly professionalOffersSelectedService = computed(() => {
    const service = this.selectedService();
    const professional = this.selectedProfessional();
    if (!service || !professional) {
      return true;
    }
    return professional.services.some((s) => s.id === service.id);
  });

  readonly slots = signal<AvailableSlot[]>([]);
  readonly loadingSlots = signal(false);
  readonly selectedSlot = signal<AvailableSlot | null>(null);

  readonly patientMode = signal<PatientMode>('registered');

  readonly patientSearchControl = this.fb.nonNullable.control('');
  readonly patientResults = signal<PatientSearchResult[]>([]);
  readonly searchingPatients = signal(false);
  readonly selectedPatient = signal<PatientSearchResult | null>(null);

  readonly guestForm = this.fb.nonNullable.group({
    name: ['', Validators.required],
    phone: ['', Validators.required],
    email: [''],
  });

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  // Set when a step's data fails to load in a way that leaves the form unusable
  // (e.g. the caller's own professional profile). Gates the rest of the form.
  readonly fatalError = signal<string | null>(null);

  readonly canSubmit = computed(() => {
    if (!this.selectedService() || !this.selectedProfessional() || !this.selectedSlot()) {
      return false;
    }
    if (!this.professionalOffersSelectedService()) {
      return false;
    }
    if (this.patientMode() === 'registered') {
      return this.selectedPatient() !== null;
    }
    return this.guestForm.valid;
  });

  constructor() {
    this.loadServices();

    if (!this.isAdmin()) {
      this.catalogService.getMyProfessionalProfile().subscribe({
        next: (professional) => {
          this.myProfessional.set(professional);
          this.selectedProfessional.set(professional);
        },
        error: (err) => {
          console.error('No se pudo cargar el perfil profesional del usuario actual', err);
          this.fatalError.set(
            'No pudimos cargar tu perfil profesional. Si sos administrador sin perfil vinculado, contactá al desarrollador.',
          );
        },
      });
    }

    this.patientSearchControl.valueChanges
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((term) => {
          const trimmed = term.trim();
          if (trimmed.length < 2) {
            this.searchingPatients.set(false);
            return of([] as PatientSearchResult[]);
          }
          this.searchingPatients.set(true);
          return this.patientService.search(trimmed);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (results) => {
          this.patientResults.set(results);
          this.searchingPatients.set(false);
        },
        error: (err) => {
          console.error('No se pudieron buscar pacientes', err);
          this.patientResults.set([]);
          this.searchingPatients.set(false);
          this.errorMessage.set('No pudimos buscar pacientes. Probá de nuevo.');
        },
      });
  }

  private loadServices(): void {
    this.loadingServices.set(true);
    this.catalogService.getServices().subscribe({
      next: (services) => {
        this.services.set(services);
        this.loadingServices.set(false);
      },
      error: (err) => {
        console.error('No se pudieron cargar los servicios', err);
        this.loadingServices.set(false);
        this.fatalError.set('No pudimos cargar los servicios disponibles. Intentá nuevamente más tarde.');
      },
    });
  }

  selectService(service: Service): void {
    this.selectedService.set(service);
    this.selectedSlot.set(null);
    this.slots.set([]);

    if (this.isAdmin()) {
      this.selectedProfessional.set(null);
      this.loadingProfessionals.set(true);
      this.catalogService.getProfessionalsForService(service.id).subscribe({
        next: (matches) => {
          this.professionals.set(matches);
          this.loadingProfessionals.set(false);
        },
        error: (err) => {
          console.error('No se pudieron cargar los profesionales para el servicio seleccionado', err);
          this.loadingProfessionals.set(false);
          this.errorMessage.set('No pudimos cargar los profesionales para este servicio. Probá de nuevo.');
        },
      });
    } else if (this.professionalOffersSelectedService()) {
      this.fetchSlots();
    }
  }

  selectProfessional(professional: Professional): void {
    this.selectedProfessional.set(professional);
    this.selectedSlot.set(null);
    this.slots.set([]);
    if (this.professionalOffersSelectedService()) {
      this.fetchSlots();
    }
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
    const professional = this.selectedProfessional();
    const date = this.dateControl.value;
    if (!service || !professional || !date || !this.professionalOffersSelectedService()) {
      return;
    }

    this.selectedSlot.set(null);
    this.slots.set([]);
    this.loadingSlots.set(true);

    this.appointmentService.getAvailableSlots(professional.id, service.id, toIsoDate(date)).subscribe({
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

  displayPatient(patient: PatientSearchResult | null): string {
    return patient ? patient.fullName : '';
  }

  submit(): void {
    const service = this.selectedService();
    const professional = this.selectedProfessional();
    const slot = this.selectedSlot();
    if (!service || !professional || !slot || !this.canSubmit()) {
      return;
    }

    const request: StaffBookAppointmentRequest = {
      serviceId: service.id,
      professionalId: professional.id,
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
