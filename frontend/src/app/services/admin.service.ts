import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AdminReportPage, AdminStats, AdminTaskPage, AdminUser, AdminUserPage, TaskDetail, TaskerProfile } from '../api/models';

export interface AdminUserQuery {
  status: 'ACTIVE' | 'SUSPENDED' | null;
  role: 'TASKER' | null;
  search: string;
  page: number;
}

export interface AdminTaskQuery {
  status: 'PUBLISHED' | 'ASSIGNED' | 'REMOVED' | null;
  search: string;
  page: number;
}

export interface AdminReportQuery {
  status: 'OPEN' | 'RESOLVED' | 'DISMISSED' | null;
  page: number;
}

export const ADMIN_PAGE_SIZE = 15;

@Injectable({ providedIn: 'root' })
export class AdminService {
  constructor(private http: HttpClient) {}

  stats(): Observable<AdminStats> {
    return this.http.get<AdminStats>('/api/admin/stats');
  }

  users(query: AdminUserQuery): Observable<AdminUserPage> {
    let params = new HttpParams().set('page', query.page).set('size', ADMIN_PAGE_SIZE);
    if (query.status) params = params.set('status', query.status);
    if (query.role) params = params.set('role', query.role);
    if (query.search.trim()) params = params.set('search', query.search.trim());
    return this.http.get<AdminUserPage>('/api/admin/users', { params });
  }

  tasks(query: AdminTaskQuery): Observable<AdminTaskPage> {
    let params = new HttpParams().set('page', query.page).set('size', ADMIN_PAGE_SIZE);
    if (query.status) params = params.set('status', query.status);
    if (query.search.trim()) params = params.set('search', query.search.trim());
    return this.http.get<AdminTaskPage>('/api/admin/tasks', { params });
  }

  reports(query: AdminReportQuery): Observable<AdminReportPage> {
    let params = new HttpParams().set('page', query.page).set('size', ADMIN_PAGE_SIZE);
    if (query.status) params = params.set('status', query.status);
    return this.http.get<AdminReportPage>('/api/admin/reports', { params });
  }

  dismissReport(reportId: string): Observable<void> {
    return this.http.post<void>(`/api/admin/reports/${encodeURIComponent(reportId)}/dismiss`, null);
  }

  suspend(userId: string): Observable<AdminUser> {
    return this.http.post<AdminUser>(`/api/admin/users/${encodeURIComponent(userId)}/suspend`, null);
  }

  reactivate(userId: string): Observable<AdminUser> {
    return this.http.post<AdminUser>(`/api/admin/users/${encodeURIComponent(userId)}/reactivate`, null);
  }

  setVerified(profileId: string, verified: boolean): Observable<TaskerProfile> {
    const action = verified ? 'verify' : 'unverify';
    return this.http.post<TaskerProfile>(`/api/admin/tasker-profiles/${encodeURIComponent(profileId)}/${action}`, null);
  }

  removeTask(taskId: string, reason: string): Observable<TaskDetail> {
    return this.http.post<TaskDetail>(`/api/admin/tasks/${encodeURIComponent(taskId)}/remove`, { reason });
  }
}
