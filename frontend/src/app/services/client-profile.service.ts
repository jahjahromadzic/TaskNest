import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ClientHirePage, ClientProfile } from '../api/models';

@Injectable({ providedIn: 'root' })
export class ClientProfileService {
  constructor(private http: HttpClient) {}

  get(userId: string): Observable<ClientProfile> {
    return this.http.get<ClientProfile>(`/api/users/${encodeURIComponent(userId)}/client-profile`);
  }

  hires(userId: string, page: number, size = 10): Observable<ClientHirePage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ClientHirePage>(`/api/users/${encodeURIComponent(userId)}/hires`, { params });
  }
}
