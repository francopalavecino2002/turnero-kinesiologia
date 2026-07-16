import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AppointmentService } from '../../core/services/appointment.service';
import { AppointmentResponse, AppointmentStatus } from '../../core/models';

const CANCELLATION_WINDOW_MS = 24 * 60 * 60 * 1000;

const DAY_NAMES = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
const MONTH_NAMES = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
];

function formatAppointmentDate(isoDateTime: string): string {
  const d = new Date(isoDateTime);
  const day = DAY_NAMES[d.getDay()];
  const date = d.getDate();
  const month = MONTH_NAMES[d.getMonth()];
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${day} ${date} de ${month}, ${hh}:${mm} hs`;
}

const STATUS_LABELS: Record<AppointmentStatus, string> = {
  BOOKED: 'Reservado',
  CONFIRMED: 'Confirmado',
  CANCELLED: 'Cancelado',
  COMPLETED: 'Completado',
  NO_SHOW: 'Ausente',
};

@Component({
  selector: 'app-cancel-confirm-dialog',
  template: `
    <h2 mat-dialog-title>Cancelar turno</h2>
    <mat-dialog-content>
      ¿Estás seguro de que querés cancelar este turno? Se enviará al historial.
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>No, volver</button>
      <button mat-flat-button color="warn" [mat-dialog-close]="true">Sí, cancelar</button>
    </mat-dialog-actions>
  `,
  imports: [MatButtonModule, MatDialogModule],
})
export class CancelConfirmDialogComponent {}

@Component({
  selector: 'app-my-appointments',
  templateUrl: './my-appointments.component.html',
  styleUrl: './my-appointments.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatButtonModule,
    MatDialogModule,
    MatExpansionModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
})
export class MyAppointmentsComponent {
  private readonly appointmentService = inject(AppointmentService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly allAppointments = signal<AppointmentResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);

  readonly upcoming = computed(() => {
    const now = new Date();
    return this.allAppointments()
      .filter(
        (a) =>
          new Date(a.dateTime) > now &&
          (a.status === 'BOOKED' || a.status === 'CONFIRMED'),
      )
      .sort(
        (a, b) =>
          new Date(a.dateTime).getTime() - new Date(b.dateTime).getTime(),
      );
  });

  readonly history = computed(() => {
    const upcomingIds = new Set(this.upcoming().map((a) => a.id));
    return this.allAppointments()
      .filter((a) => !upcomingIds.has(a.id))
      .sort(
        (a, b) =>
          new Date(b.dateTime).getTime() - new Date(a.dateTime).getTime(),
      );
  });

  constructor() {
    this.loadAppointments();
  }

  loadAppointments(): void {
    this.loading.set(true);
    this.error.set(false);

    this.appointmentService.getMyAppointments().subscribe({
      next: (appointments) => {
        this.allAppointments.set(appointments);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });
  }

  canCancel(appointment: AppointmentResponse): boolean {
    return (
      new Date(appointment.dateTime).getTime() - Date.now() >=
      CANCELLATION_WINDOW_MS
    );
  }

  cancelAppointment(appointment: AppointmentResponse): void {
    const dialogRef = this.dialog.open(CancelConfirmDialogComponent, {
      width: '340px',
    });

    dialogRef.afterClosed().subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }

      this.appointmentService.cancel(appointment.id).subscribe({
        next: () => {
          this.snackBar.open('Turno cancelado', undefined, { duration: 3000 });
          this.loadAppointments();
        },
        error: (err) => {
          // TODO: map backend error codes to i18n messages once a config endpoint exists.
          const message =
            err?.status === 409
              ? (err.error?.message ?? 'El turno ya fue modificado. Refrescá la página.')
              : 'No se pudo cancelar el turno. Intentá nuevamente.';
          this.snackBar.open(message, undefined, { duration: 5000 });
        },
      });
    });
  }

  formatDateTime(isoDateTime: string): string {
    return formatAppointmentDate(isoDateTime);
  }

  statusLabel(status: AppointmentStatus): string {
    return STATUS_LABELS[status];
  }

  goToBook(): void {
    this.router.navigate(['/book']);
  }
}
