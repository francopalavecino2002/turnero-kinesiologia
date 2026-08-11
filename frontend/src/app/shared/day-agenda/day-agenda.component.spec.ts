import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { DayAgendaComponent } from './day-agenda.component';
import { AppointmentService } from '../../core/services/appointment.service';
import { CatalogService } from '../../core/services/catalog.service';
import { Professional } from '../../core/models';

describe('DayAgendaComponent', () => {
  async function createComponent(
    getMyProfessionalProfile: () => ReturnType<CatalogService['getMyProfessionalProfile']>,
  ) {
    await TestBed.configureTestingModule({
      imports: [DayAgendaComponent],
      providers: [
        {
          provide: AppointmentService,
          useValue: { getAgenda: () => of([]) },
        },
        { provide: CatalogService, useValue: { getMyProfessionalProfile } },
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(false) }) } },
        { provide: MatSnackBar, useValue: { open: () => {} } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(DayAgendaComponent);
    fixture.componentRef.setInput('date', new Date());
    fixture.detectChanges();
    return fixture;
  }

  const professional: Professional = {
    id: 1,
    firstName: 'Ana',
    lastName: 'Gomez',
    services: [{ id: 10, name: 'Kinesiología general', durationMinutes: 60 }],
  };

  it('shows the "Agendar turno" button once the current user resolves to a professional', async () => {
    const fixture = await createComponent(() => of(professional));

    const button: HTMLButtonElement | null = fixture.nativeElement.querySelector(
      '.day-agenda__header button',
    );
    expect(button).not.toBeNull();
    expect(fixture.componentInstance.myProfessionalUnavailableReason()).toBeNull();
  });

  it('hides the button and explains why for an admin with no linked professional profile', async () => {
    const fixture = await createComponent(() => throwError(() => ({ status: 404 })));

    const button: HTMLButtonElement | null = fixture.nativeElement.querySelector(
      '.day-agenda__header button',
    );
    expect(button).toBeNull();
    expect(fixture.componentInstance.myProfessionalUnavailableReason()).toContain(
      'no tiene un perfil profesional vinculado',
    );
  });

  it('hides the button and shows a generic message on an unexpected error', async () => {
    const fixture = await createComponent(() => throwError(() => ({ status: 500 })));

    const button: HTMLButtonElement | null = fixture.nativeElement.querySelector(
      '.day-agenda__header button',
    );
    expect(button).toBeNull();
    expect(fixture.componentInstance.myProfessionalUnavailableReason()).toContain(
      'No pudimos verificar tu perfil profesional',
    );
  });
});
