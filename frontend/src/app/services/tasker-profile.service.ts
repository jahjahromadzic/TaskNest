import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TaskerProfile, UpdateTaskerProfileRequest } from '../api/models';

@Injectable({ providedIn: 'root' })
export class TaskerProfileService {
  constructor(private http: HttpClient) {}

  getMine(): Observable<TaskerProfile> {
    return this.http.get<TaskerProfile>('/api/tasker-profiles/me');
  }

  updateAbout(request: UpdateTaskerProfileRequest): Observable<TaskerProfile> {
    return this.http.put<TaskerProfile>('/api/tasker-profiles/me', request);
  }

  updateCategories(ids: string[]): Observable<TaskerProfile> {
    return this.http.put<TaskerProfile>('/api/tasker-profiles/me/categories', { ids });
  }

  updateMunicipalities(ids: string[]): Observable<TaskerProfile> {
    return this.http.put<TaskerProfile>('/api/tasker-profiles/me/municipalities', { ids });
  }
}
