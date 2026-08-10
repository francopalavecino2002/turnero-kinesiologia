import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PatientSearchResult } from '../models';

@Injectable({ providedIn: 'root' })
export class PatientService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/patients`;

  search(term: string): Observable<PatientSearchResult[]> {
    const params = new HttpParams().set('search', term);
    return this.http.get<PatientSearchResult[]>(this.apiUrl, { params });
  }
}
