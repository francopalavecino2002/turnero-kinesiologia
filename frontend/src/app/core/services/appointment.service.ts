import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AgendaEntry,
  AppointmentResponse,
  CreateAppointmentRequest,
  AvailableSlot,
} from '../models';

@Injectable({ providedIn: 'root' })
export class AppointmentService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/appointments`;

  create(request: CreateAppointmentRequest): Observable<AppointmentResponse> {
    return this.http.post<AppointmentResponse>(this.apiUrl, request);
  }

  getById(id: number): Observable<AppointmentResponse> {
    return this.http.get<AppointmentResponse>(`${this.apiUrl}/${id}`);
  }

  getMyAppointments(): Observable<AppointmentResponse[]> {
    return this.http.get<AppointmentResponse[]>(`${this.apiUrl}/my`);
  }

  getMyAgenda(date: string): Observable<AppointmentResponse[]> {
    const params = new HttpParams().set('date', date);
    return this.http.get<AppointmentResponse[]>(`${this.apiUrl}/my-agenda`, { params });
  }

  getAgenda(date: string): Observable<AgendaEntry[]> {
    const params = new HttpParams().set('date', date);
    return this.http.get<AgendaEntry[]>(`${this.apiUrl}/agenda`, { params });
  }

  getByProfessionalAndDate(professionalId: number, date: string): Observable<AppointmentResponse[]> {
    const params = new HttpParams().set('professionalId', professionalId).set('date', date);
    return this.http.get<AppointmentResponse[]>(this.apiUrl, { params });
  }

  getAllByDate(date: string): Observable<AppointmentResponse[]> {
    const params = new HttpParams().set('date', date);
    return this.http.get<AppointmentResponse[]>(this.apiUrl, { params });
  }

  getAvailableSlots(
    professionalId: number,
    serviceId: number,
    date: string,
  ): Observable<AvailableSlot[]> {
    const params = new HttpParams()
      .set('professionalId', professionalId)
      .set('serviceId', serviceId)
      .set('date', date);
    return this.http.get<AvailableSlot[]>(`${this.apiUrl}/available-slots`, { params });
  }

  cancel(id: number): Observable<AppointmentResponse> {
    return this.http.post<AppointmentResponse>(`${this.apiUrl}/${id}/cancel`, {});
  }

  confirm(id: number): Observable<AppointmentResponse> {
    return this.http.post<AppointmentResponse>(`${this.apiUrl}/${id}/confirm`, {});
  }

  complete(id: number): Observable<AppointmentResponse> {
    return this.http.post<AppointmentResponse>(`${this.apiUrl}/${id}/complete`, {});
  }

  noShow(id: number): Observable<AppointmentResponse> {
    return this.http.post<AppointmentResponse>(`${this.apiUrl}/${id}/no-show`, {});
  }
}
