import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { throwError, of } from 'rxjs';
import { BookAppointmentDialogComponent } from './book-appointment-dialog.component';
import { AuthService } from '../../core/services/auth.service';
import { CatalogService } from '../../core/services/catalog.service';
import { AppointmentService } from '../../core/services/appointment.service';
import { PatientService } from '../../core/services/patient.service';

describe('BookAppointmentDialogComponent', () => {
  async function createComponent(role: 'ADMIN' | 'PROFESSIONAL') {
    await TestBed.configureTestingModule({
      imports: [BookAppointmentDialogComponent],
      providers: [
        { provide: MatDialogRef, useValue: { close: () => {} } },
        { provide: AuthService, useValue: { role: signal(role) } },
        {
          provide: CatalogService,
          useValue: {
            getServices: () => of([]),
            getProfessionalsForService: () => of([]),
            getMyProfessionalProfile: () => throwError(() => new Error('boom')),
          },
        },
        { provide: AppointmentService, useValue: { getAvailableSlots: () => of([]), staffBook: () => of({}) } },
        { provide: PatientService, useValue: { search: () => of([]) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(BookAppointmentDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('exposes a visible fatal error, instead of hanging silently, when the professional profile fails to load', async () => {
    const fixture = await createComponent('PROFESSIONAL');
    const component = fixture.componentInstance;

    expect(component.fatalError()).not.toBeNull();
    expect(component.selectedProfessional()).toBeNull();

    const message: HTMLParagraphElement | null = fixture.nativeElement.querySelector(
      '.book-dialog__error[role="alert"]',
    );
    expect(message?.textContent).toContain('No pudimos cargar tu perfil profesional');
  });

  it('does not set a fatal error for an ADMIN, who does not depend on the own-profile lookup', async () => {
    const fixture = await createComponent('ADMIN');
    const component = fixture.componentInstance;

    expect(component.fatalError()).toBeNull();
  });
});
