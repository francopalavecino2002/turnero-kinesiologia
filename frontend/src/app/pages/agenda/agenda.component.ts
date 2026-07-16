import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AppointmentService } from '../../core/services/appointment.service';
import { AgendaEntry, AppointmentStatus } from '../../core/models';

const DAY_NAMES = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
const MONTH_NAMES = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
];

function toIsoDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function formatDayLabel(date: Date): string {
  const day = DAY_NAMES[date.getDay()];
  const d = date.getDate();
  const month = MONTH_NAMES[date.getMonth()];
  return `${day} ${d} de ${month}`;
}

function formatTimeRange(start: string, end: string): string {
  const s = new Date(start);
  const e = new Date(end);
  const fmt = (d: Date) => `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  return `${fmt(s)} – ${fmt(e)}`;
}

const STATUS_LABELS: Record<AppointmentStatus, string> = {
  BOOKED: 'Reservado',
  CONFIRMED: 'Confirmado',
  CANCELLED: 'Cancelado',
  COMPLETED: 'Completado',
  NO_SHOW: 'Ausente',
};

interface ActionButton {
  label: string;
  action: 'confirm' | 'complete' | 'noShow' | 'cancel';
  destructive: boolean;
}

const ACTIONS_BY_STATUS: Record<string, ActionButton[]> = {
  BOOKED: [
    { label: 'Confirmar', action: 'confirm', destructive: false },
    { label: 'Completar', action: 'complete', destructive: false },
    { label: 'Ausente', action: 'noShow', destructive: true },
    { label: 'Cancelar', action: 'cancel', destructive: true },
  ],
  CONFIRMED: [
    { label: 'Completar', action: 'complete', destructive: false },
    { label: 'Ausente', action: 'noShow', destructive: true },
    { label: 'Cancelar', action: 'cancel', destructive: true },
  ],
};

export interface ActionDialogData {
  title: string;
  message: string;
  confirmLabel: string;
  confirmColor: 'warn' | 'primary';
}

@Component({
  selector: 'app-action-confirm-dialog',
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>{{ data.message }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>No, volver</button>
      <button mat-flat-button [color]="data.confirmColor" [mat-dialog-close]="true">
        {{ data.confirmLabel }}
      </button>
    </mat-dialog-actions>
  `,
  imports: [MatButtonModule, MatDialogModule],
})
export class ActionConfirmDialogComponent {
  readonly data = inject<ActionDialogData>(MAT_DIALOG_DATA);
}

@Component({
  selector: 'app-agenda',
  templateUrl: './agenda.component.html',
  styleUrl: './agenda.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
})
export class AgendaComponent {
  private readonly appointmentService = inject(AppointmentService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly selectedDate = signal(new Date());
  readonly entries = signal<AgendaEntry[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);

  readonly isToday = computed(() => {
    const now = new Date();
    const sel = this.selectedDate();
    return toIsoDate(now) === toIsoDate(sel);
  });

  readonly dayLabel = computed(() => formatDayLabel(this.selectedDate()));

  readonly sortedEntries = computed(() =>
    [...this.entries()].sort(
      (a, b) => new Date(a.dateTime).getTime() - new Date(b.dateTime).getTime(),
    ),
  );

  readonly ownCount = computed(() => this.entries().filter(e => e.ownedByCurrentUser).length);
  readonly otherCount = computed(() => this.entries().filter(e => !e.ownedByCurrentUser).length);

  constructor() {
    this.loadAgenda();
  }

  loadAgenda(): void {
    this.loading.set(true);
    this.error.set(false);

    const dateStr = toIsoDate(this.selectedDate());
    this.appointmentService.getAgenda(dateStr).subscribe({
      next: (entries) => {
        this.entries.set(entries);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });
  }

  prevDay(): void {
    const d = new Date(this.selectedDate());
    d.setDate(d.getDate() - 1);
    this.selectedDate.set(d);
    this.loadAgenda();
  }

  nextDay(): void {
    const d = new Date(this.selectedDate());
    d.setDate(d.getDate() + 1);
    this.selectedDate.set(d);
    this.loadAgenda();
  }

  goToToday(): void {
    this.selectedDate.set(new Date());
    this.loadAgenda();
  }

  formatTimeRange(start: string, end: string): string {
    return formatTimeRange(start, end);
  }

  statusLabel(status: AppointmentStatus): string {
    return STATUS_LABELS[status];
  }

  actionsFor(entry: AgendaEntry): ActionButton[] {
    if (!entry.ownedByCurrentUser) {
      return [];
    }
    return ACTIONS_BY_STATUS[entry.status] ?? [];
  }

  performAction(entry: AgendaEntry, action: ActionButton): void {
    if (action.destructive) {
      this.openDestructiveDialog(entry, action);
    } else {
      this.executeAction(entry, action.action);
    }
  }

  private openDestructiveDialog(entry: AgendaEntry, action: ActionButton): void {
    const titles: Record<string, string> = {
      cancel: 'Cancelar turno',
      noShow: 'Marcar como ausente',
    };
    const messages: Record<string, string> = {
      cancel: '¿Estás seguro de que querés cancelar este turno?',
      noShow: '¿Marcar este turno como ausente?',
    };
    const confirmLabels: Record<string, string> = {
      cancel: 'Sí, cancelar',
      noShow: 'Sí, marcar ausente',
    };

    const dialogRef = this.dialog.open<ActionConfirmDialogComponent, ActionDialogData>(
      ActionConfirmDialogComponent,
      {
        width: '340px',
        data: {
          title: titles[action.action],
          message: messages[action.action],
          confirmLabel: confirmLabels[action.action],
          confirmColor: 'warn',
        },
      },
    );

    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.executeAction(entry, action.action);
      }
    });
  }

  private executeAction(
    entry: AgendaEntry,
    actionType: 'confirm' | 'complete' | 'noShow' | 'cancel',
  ): void {
    if (entry.id == null) {
      return;
    }

    const actionMap: Record<string, () => import('rxjs').Observable<unknown>> = {
      confirm: () => this.appointmentService.confirm(entry.id!),
      complete: () => this.appointmentService.complete(entry.id!),
      noShow: () => this.appointmentService.noShow(entry.id!),
      cancel: () => this.appointmentService.cancel(entry.id!),
    };

    const labels: Record<string, string> = {
      confirm: 'Turno confirmado',
      complete: 'Turno completado',
      noShow: 'Turno marcado como ausente',
      cancel: 'Turno cancelado',
    };

    actionMap[actionType]().subscribe({
      next: () => {
        this.snackBar.open(labels[actionType], undefined, { duration: 3000 });
        this.loadAgenda();
      },
      error: (err: { status?: number; error?: { message?: string } }) => {
        const message =
          err?.status === 409
            ? (err.error?.message ?? 'El turno ya fue modificado. Refrescá la página.')
            : 'No se pudo realizar la acción. Intentá nuevamente.';
        this.snackBar.open(message, undefined, { duration: 5000 });
      },
    });
  }
}
