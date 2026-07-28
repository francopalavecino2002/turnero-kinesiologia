import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Service,
  ServiceCreateRequest,
  ServiceUpdateRequest,
} from '../models';

@Injectable({ providedIn: 'root' })
export class AdminServiceService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/admin/services`;

  list(includeInactive = false): Observable<Service[]> {
    const params = new HttpParams().set(
      'includeInactive',
      String(includeInactive),
    );
    return this.http.get<Service[]>(this.apiUrl, { params });
  }

  getById(id: number): Observable<Service> {
    return this.http.get<Service>(`${this.apiUrl}/${id}`);
  }

  create(request: ServiceCreateRequest): Observable<Service> {
    return this.http.post<Service>(this.apiUrl, request);
  }

  update(id: number, request: ServiceUpdateRequest): Observable<Service> {
    return this.http.put<Service>(`${this.apiUrl}/${id}`, request);
  }

  deactivate(id: number): Observable<Service> {
    return this.http.post<Service>(`${this.apiUrl}/${id}/deactivate`, {});
  }

  reactivate(id: number): Observable<Service> {
    return this.http.post<Service>(`${this.apiUrl}/${id}/reactivate`, {});
  }
}
