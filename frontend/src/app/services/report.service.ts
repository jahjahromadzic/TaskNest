import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateReportRequest } from '../api/models';

export type ReportTarget = 'TASK' | 'USER';

@Injectable({ providedIn: 'root' })
export class ReportService {
  constructor(private http: HttpClient) {}

  report(target: ReportTarget, id: string, request: CreateReportRequest): Observable<void> {
    const path = target === 'TASK' ? 'tasks' : 'users';
    return this.http.post<void>(`/api/${path}/${encodeURIComponent(id)}/reports`, request);
  }
}
