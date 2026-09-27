import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TaskDetail, TaskPage } from '../api/models';

export const TASK_SORTS = {
  newest: { label: 'Newest first', value: 'publishedAt,desc' },
  ending: { label: 'Ending soon', value: 'expiresAt,asc' },
  budgetHigh: { label: 'Budget: high to low', value: 'budget,desc' },
  budgetLow: { label: 'Budget: low to high', value: 'budget,asc' },
} as const;

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
}
