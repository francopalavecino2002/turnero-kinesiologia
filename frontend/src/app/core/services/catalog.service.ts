import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Service, Professional } from '../models';

@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly http = inject(HttpClient);

  getServices(): Observable<Service[]> {
    return this.http.get<Service[]>(`${environment.apiUrl}/services`);
  }

  getProfessionals(): Observable<Professional[]> {
    return this.http.get<Professional[]>(`${environment.apiUrl}/professionals`);
  }

  getProfessionalsForService(serviceId: number): Observable<Professional[]> {
    return this.http.get<Professional[]>(`${environment.apiUrl}/services/${serviceId}/professionals`);
  }

  // Resolves the logged-in professional's own record (id, offered services) - used by the manual
  // booking dialog so a PROFESSIONAL implicitly books on their own agenda.
  getMyProfessionalProfile(): Observable<Professional> {
    return this.http.get<Professional>(`${environment.apiUrl}/professionals/me`);
  }
}
