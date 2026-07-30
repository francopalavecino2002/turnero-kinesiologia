import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Availability,
  CreateAvailabilityRequest,
  UpdateAvailabilityRequest,
} from '../models';

@Injectable({ providedIn: 'root' })
export class AdminAvailabilityService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/admin/availability`;

  list(professionalId: number): Observable<Availability[]> {
    const params = new HttpParams().set('professionalId', String(professionalId));
    return this.http.get<Availability[]>(this.apiUrl, { params });
  }

  create(request: CreateAvailabilityRequest): Observable<Availability> {
    return this.http.post<Availability>(this.apiUrl, request);
  }

  update(id: number, request: UpdateAvailabilityRequest): Observable<Availability> {
    return this.http.put<Availability>(`${this.apiUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
