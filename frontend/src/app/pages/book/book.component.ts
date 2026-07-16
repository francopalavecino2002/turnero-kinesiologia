import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { BreakpointObserver } from '@angular/cdk/layout';
import { MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatStepperModule } from '@angular/material/stepper';
import { CatalogService } from '../../core/services/catalog.service';
import { AppointmentService } from '../../core/services/appointment.service';
import {
  AppointmentResponse,
  AvailableSlot,
  CreateAppointmentRequest,
  Professional,
  Service,
} from '../../core/models';

type StepId = 'service' | 'professional' | 'date' | 'slot';
type WizardView = 'wizard' | 'confirm' | 'success';

function toIsoDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

@Component({
  selector: 'app-book',
  templateUrl: './book.component.html',
  styleUrl: './book.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [provideNativeDateAdapter(), { provide: MAT_DATE_LOCALE, useValue: 'es-AR' }],
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatStepperModule,
  ],
})
export class BookComponent {
  private readonly catalogService = inject(CatalogService);
  private readonly appointmentService = inject(AppointmentService);
  private readonly router = inject(Router);
  private readonly breakpointObserver = inject(BreakpointObserver);

  readonly isMobile = signal(this.breakpointObserver.isMatched('(max-width: 639.98px)'));
  readonly stepperOrientation = computed<'horizontal' | 'vertical'>(() =>
    this.isMobile() ? 'vertical' : 'horizontal',
  );

  readonly today = new Date();
  readonly minDate = new Date(this.today.getFullYear(), this.today.getMonth(), this.today.getDate());
  readonly dateControl = new FormControl<Date>(this.minDate, { nonNullable: true });

  readonly view = signal<WizardView>('wizard');

  readonly services = signal<Service[]>([]);
  readonly loadingServices = signal(false);
  readonly servicesError = signal(false);

  readonly selectedService = signal<Service | null>(null);

  readonly professionals = signal<Professional[]>([]);
  readonly loadingProfessionals = signal(false);
  // True once we've fetched professionals for the current service and there were none available.
  readonly noProfessionalsForService = signal(false);
  // Only the professional step is shown when there is more than one candidate to choose from.
  readonly needsProfessionalStep = computed(() => this.professionals().length > 1);

  readonly selectedProfessional = signal<Professional | null>(null);

  // Tracks the date step separately from selectedSlot: it must read `true` as soon as a date is
  // chosen (not only once a slot is picked), otherwise the linear stepper's forward-navigation
  // guard - which requires every earlier step's `completed` to be true - blocks the jump to Horario.
  readonly dateChosen = signal(false);

  readonly slots = signal<AvailableSlot[]>([]);
  readonly loadingSlots = signal(false);
  readonly slotsError = signal(false);

  readonly selectedSlot = signal<AvailableSlot | null>(null);

  readonly booking = signal(false);
  readonly bookingError = signal<string | null>(null);
  readonly bookingResult = signal<AppointmentResponse | null>(null);

  // Steps that are actually rendered right now; drives which index to jump to after each choice.
  readonly stepIds = computed<StepId[]>(() => [
    'service',
    ...(this.needsProfessionalStep() ? (['professional'] as const) : []),
    'date',
    'slot',
  ]);

  readonly selectedIndex = signal(0);

  private goToStep(step: StepId): void {
    const index = this.stepIds().indexOf(step);
    // Deferred: the linear stepper reads each mat-step's `completed` input to guard
    // forward navigation, and that input (bound to the same selection signal we just
    // set) hasn't been refreshed by change detection yet in this tick - jumping straight
    // there would read the stale `false` and silently refuse to advance.
    setTimeout(() => this.selectedIndex.set(index === -1 ? 0 : index));
  }

  /** Syncs the signal when the user clicks a step header directly. */
  onStepChange(index: number): void {
    this.selectedIndex.set(index);
  }

  private resetDateAndSlots(): void {
    this.dateChosen.set(false);
    this.dateControl.setValue(this.minDate);
    this.selectedSlot.set(null);
    this.slots.set([]);
    this.slotsError.set(false);
  }

  selectService(service: Service): void {
    this.selectedService.set(service);
    this.selectedProfessional.set(null);
    this.noProfessionalsForService.set(false);
    this.professionals.set([]);
    this.resetDateAndSlots();
    this.loadingProfessionals.set(true);

    this.catalogService.getProfessionalsForService(service.id).subscribe({
      next: (matches) => {
        this.professionals.set(matches);
        this.loadingProfessionals.set(false);

        if (matches.length === 0) {
          this.noProfessionalsForService.set(true);
          return;
        }

        if (matches.length === 1) {
          this.selectedProfessional.set(matches[0]);
          this.goToStep('date');
          return;
        }

        this.goToStep('professional');
      },
      error: () => {
        this.loadingProfessionals.set(false);
        this.noProfessionalsForService.set(true);
      },
    });
  }

  selectProfessional(professional: Professional): void {
    this.selectedProfessional.set(professional);
    this.resetDateAndSlots();
    this.goToStep('date');
  }

  onDateSelected(date: Date | null): void {
    if (!date) {
      return;
    }
    this.dateControl.setValue(date);
    this.dateChosen.set(true);
    this.fetchSlots();
  }

  confirmDate(): void {
    if (!this.dateChosen()) {
      return;
    }
    this.goToStep('slot');
  }

  private fetchSlots(): void {
    const service = this.selectedService();
    const professional = this.selectedProfessional();
    const date = this.dateControl.value;
    if (!service || !professional || !date) {
      return;
    }

    this.selectedSlot.set(null);
    this.slots.set([]);
    this.slotsError.set(false);
    this.loadingSlots.set(true);

    this.appointmentService.getAvailableSlots(professional.id, service.id, toIsoDate(date)).subscribe({
      next: (slots) => {
        this.slots.set(slots);
        this.loadingSlots.set(false);
      },
      error: () => {
        this.loadingSlots.set(false);
        this.slotsError.set(true);
      },
    });
  }

  selectSlot(slot: AvailableSlot): void {
    this.selectedSlot.set(slot);
    this.bookingError.set(null);
    this.view.set('confirm');
  }

  backToSlots(): void {
    this.view.set('wizard');
    this.goToStep('slot');
  }

  confirmBooking(): void {
    const service = this.selectedService();
    const professional = this.selectedProfessional();
    const slot = this.selectedSlot();
    if (!service || !professional || !slot) {
      return;
    }

    const request: CreateAppointmentRequest = {
      serviceId: service.id,
      professionalId: professional.id,
      dateTime: slot.startTime,
    };

    this.booking.set(true);
    this.bookingError.set(null);

    this.appointmentService.create(request).subscribe({
      next: (response) => {
        this.booking.set(false);
        this.bookingResult.set(response);
        this.view.set('success');
      },
      error: (err) => {
        this.booking.set(false);
        if (err?.status === 409) {
          this.bookingError.set('Ese horario ya no está disponible, elegí otro.');
          this.view.set('wizard');
          this.fetchSlots();
          this.goToStep('slot');
        } else {
          this.bookingError.set('Ocurrió un error al reservar el turno. Intentá nuevamente.');
        }
      },
    });
  }

  bookAnother(): void {
    this.selectedService.set(null);
    this.selectedProfessional.set(null);
    this.professionals.set([]);
    this.noProfessionalsForService.set(false);
    this.resetDateAndSlots();
    this.bookingError.set(null);
    this.bookingResult.set(null);
    this.dateControl.setValue(this.minDate);
    this.selectedIndex.set(0);
    this.view.set('wizard');
  }

  goToMyAppointments(): void {
    this.router.navigate(['/my-appointments']);
  }

  loadServices(): void {
    this.loadingServices.set(true);
    this.servicesError.set(false);
    this.catalogService.getServices().subscribe({
      next: (services) => {
        // Backend already returns only active services.
        this.services.set(services);
        this.loadingServices.set(false);
      },
      error: () => {
        this.loadingServices.set(false);
        this.servicesError.set(true);
      },
    });
  }

  constructor() {
    this.breakpointObserver
      .observe('(max-width: 639.98px)')
      .subscribe((result) => this.isMobile.set(result.matches));
    this.loadServices();
  }
}
