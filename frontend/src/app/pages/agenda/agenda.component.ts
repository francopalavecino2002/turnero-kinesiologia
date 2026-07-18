import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import { MatCalendar } from '@angular/material/datepicker';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subscription } from 'rxjs';
import { map, distinctUntilChanged } from 'rxjs/operators';
import { AppointmentService } from '../../core/services/appointment.service';
import { MonthDaySummary } from '../../core/models';
import { DayAgendaComponent } from '../../shared/day-agenda/day-agenda.component';

@Component({
  selector: 'app-agenda',
  templateUrl: './agenda.component.html',
  styleUrl: './agenda.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [provideNativeDateAdapter()],
  imports: [
    MatCalendar,
    MatIconModule,
    MatProgressSpinnerModule,
    DayAgendaComponent,
  ],
})
export class AgendaComponent implements AfterViewInit, OnDestroy {
  @ViewChild(MatCalendar) calendar!: MatCalendar<Date>;

  private readonly appointmentService = inject(AppointmentService);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly selectedDate = signal(new Date());
  readonly monthSummary = signal<MonthDaySummary[]>([]);
  readonly monthLoading = signal(true);
  readonly monthError = signal(false);

  private displayedMonthKey = '';
  private stateChangesSub?: Subscription;

  readonly monthLabel = computed(() => {
    const d = this.selectedDate();
    const months = [
      'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
      'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
    ];
    return `${months[d.getMonth()]} ${d.getFullYear()}`;
  });

  dateClassFn = (_date: Date): string => '';

  constructor() {
    const now = new Date();
    this.displayedMonthKey = `${now.getFullYear()}-${now.getMonth()}`;
    this.loadMonthSummary(now.getFullYear(), now.getMonth() + 1);
  }

  ngAfterViewInit(): void {
    this.stateChangesSub = this.calendar.stateChanges.pipe(
      map(() => {
        const d = this.calendar.activeDate;
        return `${d.getFullYear()}-${d.getMonth()}`;
      }),
      distinctUntilChanged(),
    ).subscribe((key) => {
      if (key !== this.displayedMonthKey) {
        this.displayedMonthKey = key;
        this.loadMonthSummary(
          this.calendar.activeDate.getFullYear(),
          this.calendar.activeDate.getMonth() + 1,
        );
      }
    });

    if (!this.monthLoading()) {
      this.calendar.updateTodaysDate();
    }
  }

  ngOnDestroy(): void {
    this.stateChangesSub?.unsubscribe();
  }

  onDateSelected(date: Date | null): void {
    if (date) {
      this.selectedDate.set(date);
    }
  }

  onActionPerformed(): void {
    const d = this.selectedDate();
    this.loadMonthSummary(d.getFullYear(), d.getMonth() + 1);
  }

  loadMonthSummary(year: number, month: number): void {
    this.monthLoading.set(true);
    this.monthError.set(false);

    this.appointmentService.getMonthSummary(year, month).subscribe({
      next: (response) => {
        const daysWithAppointments = new Set(response.days.map(e => e.day));
        this.monthSummary.set(response.days);
        this.monthLoading.set(false);

        this.dateClassFn = (date: Date): string =>
          (date.getMonth() + 1 === month &&
            date.getFullYear() === year &&
            daysWithAppointments.has(date.getDate()))
            ? 'agenda-calendar__has-appointments'
            : '';

        this.cdr.detectChanges();
        this.calendar?.updateTodaysDate();
      },
      error: () => {
        this.monthSummary.set([]);
        this.monthLoading.set(false);
        this.monthError.set(true);

        this.dateClassFn = (_date: Date): string => '';

        this.cdr.detectChanges();
        this.calendar?.updateTodaysDate();
      },
    });
  }
}
