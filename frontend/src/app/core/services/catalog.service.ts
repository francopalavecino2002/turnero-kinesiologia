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
}
