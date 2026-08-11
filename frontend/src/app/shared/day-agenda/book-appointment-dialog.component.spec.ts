import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { BookAppointmentDialogComponent } from './book-appointment-dialog.component';
import { AppointmentService } from '../../core/services/appointment.service';
import { PatientService } from '../../core/services/patient.service';
import { Professional } from '../../core/models';

describe('BookAppointmentDialogComponent', () => {
  async function createComponent(professional: Professional, getAvailableSlots = () => of([])) {
    await TestBed.configureTestingModule({
      imports: [BookAppointmentDialogComponent],
      providers: [
        { provide: MatDialogRef, useValue: { close: () => {} } },
        { provide: MAT_DIALOG_DATA, useValue: { professional } },
        { provide: AppointmentService, useValue: { getAvailableSlots, staffBook: () => of({}) } },
        { provide: PatientService, useValue: { search: () => of([]) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(BookAppointmentDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  const twoServices: Professional = {
    id: 1,
    firstName: 'Ana',
    lastName: 'Gomez',
    services: [
      { id: 10, name: 'Kinesiología general', durationMinutes: 60 },
      { id: 11, name: 'EMSELLA', durationMinutes: 30 },
    ],
  };

  const oneService: Professional = {
    id: 2,
    firstName: 'Beto',
    lastName: 'Diaz',
    services: [{ id: 20, name: 'Kinesiología general', durationMinutes: 60 }],
  };

  const noServices: Professional = {
    id: 3,
    firstName: 'Carla',
    lastName: 'Ruiz',
    services: [],
  };

  it('shows the service selection step when the professional offers more than one service', async () => {
    const fixture = await createComponent(twoServices);
    const component = fixture.componentInstance;

    expect(component.skipServiceStep).toBe(false);
    expect(component.selectedService()).toBeNull();

    const buttons: NodeListOf<HTMLButtonElement> =
      fixture.nativeElement.querySelectorAll('.book-dialog__options button');
    expect(buttons.length).toBe(2);
  });

  it('auto-selects and hides the service step when the professional offers exactly one service', async () => {
    const fixture = await createComponent(oneService);
    const component = fixture.componentInstance;

    expect(component.skipServiceStep).toBe(true);
    expect(component.selectedService()).toEqual(oneService.services[0]);

    const serviceSection = fixture.nativeElement.querySelector('.book-dialog__options');
    expect(serviceSection).toBeNull();
  });

  it('fetches slots for the professional own id as soon as the single service is auto-selected', async () => {
    const getAvailableSlots = vi
      .fn()
      .mockReturnValue(of([{ startTime: '2026-08-12T10:00:00', endTime: '2026-08-12T11:00:00' }]));

    const fixture = await createComponent(oneService, getAvailableSlots);
    fixture.detectChanges();

    expect(getAvailableSlots).toHaveBeenCalledWith(
      oneService.id,
      oneService.services[0].id,
      expect.any(String),
    );
    expect(fixture.componentInstance.slots().length).toBe(1);
  });

  it('shows an inline message instead of a service picker when the professional has no services', async () => {
    const fixture = await createComponent(noServices);

    const message: HTMLParagraphElement | null =
      fixture.nativeElement.querySelector('.book-dialog__error[role="alert"]');
    expect(message?.textContent).toContain('No tenés servicios asignados');
  });

  it('does not allow submitting until a slot and a patient are selected', async () => {
    const fixture = await createComponent(oneService);
    expect(fixture.componentInstance.canSubmit()).toBe(false);
  });
});
