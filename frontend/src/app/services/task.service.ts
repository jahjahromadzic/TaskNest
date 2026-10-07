import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateTaskRequest, TaskDetail, TaskPage, TaskPhoto } from '../api/models';
import { TaskStatus } from '../shared/task-status/task-status';
import { translated } from '../i18n/translate';

export const TASK_SORTS = {
  newest: translated({ value: 'publishedAt,desc' }, { label: 'browse.sortNewest' }),
  ending: translated({ value: 'expiresAt,asc' }, { label: 'browse.sortEnding' }),
  budgetHigh: translated({ value: 'budget,desc' }, { label: 'browse.sortBudgetHigh' }),
  budgetLow: translated({ value: 'budget,asc' }, { label: 'browse.sortBudgetLow' }),
};

export type TaskSort = keyof typeof TASK_SORTS;

export interface TaskFilters {
  categoryId: string | null;
  municipalityId: string | null;
  sort: TaskSort;
  page: number;
}

export const TASKS_PER_PAGE = 10;

@Injectable({ providedIn: 'root' })
export class TaskService {
  constructor(private http: HttpClient) {}

  browse(filters: TaskFilters): Observable<TaskPage> {
    let params = new HttpParams()
      .set('page', filters.page)
      .set('size', TASKS_PER_PAGE)
      .set('sort', TASK_SORTS[filters.sort].value);
    if (filters.categoryId) {
      params = params.set('categoryId', filters.categoryId);
    }
    if (filters.municipalityId) {
      params = params.set('municipalityId', filters.municipalityId);
    }
    return this.http.get<TaskPage>('/api/tasks', { params });
  }

  getTask(id: string): Observable<TaskDetail> {
    return this.http.get<TaskDetail>(`/api/tasks/${encodeURIComponent(id)}`);
  }

  createTask(request: CreateTaskRequest): Observable<TaskDetail> {
    return this.http.post<TaskDetail>('/api/tasks', request);
  }

  uploadPhoto(taskId: string, photo: Blob): Observable<TaskPhoto> {
    const form = new FormData();
    form.append('file', photo, 'photo.jpg');
    return this.http.post<TaskPhoto>(`/api/tasks/${encodeURIComponent(taskId)}/photos`, form);
  }

  deletePhoto(taskId: string, photoId: string): Observable<void> {
    return this.http.delete<void>(`/api/tasks/${encodeURIComponent(taskId)}/photos/${encodeURIComponent(photoId)}`);
  }

  publishTask(id: string): Observable<TaskDetail> {
    return this.http.post<TaskDetail>(`/api/tasks/${encodeURIComponent(id)}/publish`, null);
  }

  getMyTasks(statuses: TaskStatus[], page: number): Observable<TaskPage> {
    let params = new HttpParams().set('page', page).set('size', TASKS_PER_PAGE).set('sort', 'createdAt,desc');
    for (const status of statuses) {
      params = params.append('status', status);
    }
    return this.http.get<TaskPage>('/api/tasks/mine', { params });
  }

  getMyTaskCounts(): Observable<Partial<Record<TaskStatus, number>>> {
    return this.http.get<Partial<Record<TaskStatus, number>>>('/api/tasks/mine/counts');
  }

  cancelTask(id: string): Observable<TaskDetail> {
    return this.http.post<TaskDetail>(`/api/tasks/${encodeURIComponent(id)}/cancel`, null);
  }

  reopenTask(id: string): Observable<TaskDetail> {
    return this.http.post<TaskDetail>(`/api/tasks/${encodeURIComponent(id)}/reopen`, null);
  }

  closeTask(id: string): Observable<TaskDetail> {
    return this.http.post<TaskDetail>(`/api/tasks/${encodeURIComponent(id)}/close`, null);
  }

  startTask(id: string): Observable<TaskDetail> {
    return this.http.post<TaskDetail>(`/api/tasks/${encodeURIComponent(id)}/start`, null);
  }

  completeTask(id: string): Observable<TaskDetail> {
    return this.http.post<TaskDetail>(`/api/tasks/${encodeURIComponent(id)}/complete`, null);
  }

  getMatching(page: number, size = TASKS_PER_PAGE): Observable<TaskPage> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', 'publishedAt,desc');
    return this.http.get<TaskPage>('/api/tasks/matching', { params });
  }

  getAssigned(page: number, size = TASKS_PER_PAGE): Observable<TaskPage> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', 'publishedAt,desc');
    return this.http.get<TaskPage>('/api/tasks/assigned', { params });
  }
}
