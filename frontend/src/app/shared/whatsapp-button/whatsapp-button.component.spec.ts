import { TestBed } from '@angular/core/testing';
import { WhatsappButtonComponent } from './whatsapp-button.component';

describe('WhatsappButtonComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WhatsappButtonComponent],
    }).compileComponents();
  });

  it('opens a wa.me chat with the URL-encoded prefilled message', () => {
    const fixture = TestBed.createComponent(WhatsappButtonComponent);
    fixture.detectChanges();
    const anchor: HTMLAnchorElement = fixture.nativeElement.querySelector('a');

    expect(anchor.href).toBe(
      'https://wa.me/5492995227041?text=Hola!%20Quer%C3%ADa%20consultar%20por%20un%20turno%20en%20eQi.',
    );
    expect(anchor.target).toBe('_blank');
    expect(anchor.getAttribute('rel')).toBe('noopener noreferrer');
    expect(anchor.getAttribute('aria-label')).toBe('Escribinos por WhatsApp');
  });
});
