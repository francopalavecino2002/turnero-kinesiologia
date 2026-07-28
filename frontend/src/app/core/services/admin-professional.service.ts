import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ProfessionalAdmin,
  ProfessionalCreateRequest,
  ProfessionalCreatedResponse,
  ProfessionalUpdateRequest,
} from '../models';

@Injectable({ providedIn: 'root' })
export class AdminProfessionalService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/admin/professionals`;

  list(includeInactive = false): Observable<ProfessionalAdmin[]> {
    const params = new HttpParams().set(
      'includeInactive',
      String(includeInactive),
    );
    return this.http.get<ProfessionalAdmin[]>(this.apiUrl, { params });
  }

  getById(id: number): Observable<ProfessionalAdmin> {
    return this.http.get<ProfessionalAdmin>(`${this.apiUrl}/${id}`);
  }

  create(
    request: ProfessionalCreateRequest,
  ): Observable<ProfessionalCreatedResponse> {
    return this.http.post<ProfessionalCreatedResponse>(this.apiUrl, request);
  }

  update(
    id: number,
    request: ProfessionalUpdateRequest,
  ): Observable<ProfessionalAdmin> {
    return this.http.put<ProfessionalAdmin>(
      `${this.apiUrl}/${id}`,
      request,
    );
  }

  deactivate(id: number): Observable<ProfessionalAdmin> {
    return this.http.post<ProfessionalAdmin>(
      `${this.apiUrl}/${id}/deactivate`,
      {},
    );
  }

  reactivate(id: number): Observable<ProfessionalAdmin> {
    return this.http.post<ProfessionalAdmin>(
      `${this.apiUrl}/${id}/reactivate`,
      {},
    );
  }
}
