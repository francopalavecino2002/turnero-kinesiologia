import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  RecurringBlockAdmin,
  RecurringBlockCreateRequest,
  RecurringBlockUpdateRequest,
} from '../models';

@Injectable({ providedIn: 'root' })
export class AdminRecurringBlockService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/admin/recurring-blocks`;

  list(includeInactive = false): Observable<RecurringBlockAdmin[]> {
    const params = new HttpParams().set(
      'includeInactive',
      String(includeInactive),
    );
    return this.http.get<RecurringBlockAdmin[]>(this.apiUrl, { params });
  }

  getById(id: number): Observable<RecurringBlockAdmin> {
    return this.http.get<RecurringBlockAdmin>(`${this.apiUrl}/${id}`);
  }

  create(
    request: RecurringBlockCreateRequest,
  ): Observable<RecurringBlockAdmin> {
    return this.http.post<RecurringBlockAdmin>(this.apiUrl, request);
  }

  update(
    id: number,
    request: RecurringBlockUpdateRequest,
  ): Observable<RecurringBlockAdmin> {
    return this.http.put<RecurringBlockAdmin>(
      `${this.apiUrl}/${id}`,
      request,
    );
  }

  deactivate(id: number): Observable<RecurringBlockAdmin> {
    return this.http.post<RecurringBlockAdmin>(
      `${this.apiUrl}/${id}/deactivate`,
      {},
    );
  }

  reactivate(id: number): Observable<RecurringBlockAdmin> {
    return this.http.post<RecurringBlockAdmin>(
      `${this.apiUrl}/${id}/reactivate`,
      {},
    );
  }
}
