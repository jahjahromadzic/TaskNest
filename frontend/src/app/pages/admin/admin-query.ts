import { ParamMap } from '@angular/router';
import { AdminTaskQuery, AdminUserQuery } from '../../services/admin.service';
import { translated } from '../../i18n/translate';

export type AdminTab = 'users' | 'tasks';
export type UserFilter = 'all' | 'taskers' | 'suspended';
export type TaskFilter = 'all' | 'open' | 'assigned' | 'removed';

export const USER_FILTERS: { key: UserFilter; readonly label: string }[] = [
  translated({ key: 'all' as UserFilter }, { label: 'admin.filterAllUsers' }),
  translated({ key: 'taskers' as UserFilter }, { label: 'admin.filterTaskers' }),
  translated({ key: 'suspended' as UserFilter }, { label: 'admin.filterSuspended' }),
];

export const TASK_FILTERS: { key: TaskFilter; readonly label: string }[] = [
  translated({ key: 'all' as TaskFilter }, { label: 'admin.filterAllTasks' }),
  translated({ key: 'open' as TaskFilter }, { label: 'admin.filterOpen' }),
  translated({ key: 'assigned' as TaskFilter }, { label: 'admin.filterAssigned' }),
  translated({ key: 'removed' as TaskFilter }, { label: 'admin.filterRemoved' }),
];

const TASK_STATUS_BY_FILTER: Record<TaskFilter, AdminTaskQuery['status']> = {
  all: null,
  open: 'PUBLISHED',
  assigned: 'ASSIGNED',
  removed: 'REMOVED',
};

export function readTab(params: ParamMap): AdminTab {
  return params.get('tab') === 'tasks' ? 'tasks' : 'users';
}

function readPage(params: ParamMap): number {
  const page = Number(params.get('page'));
  return Number.isInteger(page) && page > 1 ? page - 1 : 0;
}

export function readUserFilter(params: ParamMap): UserFilter {
  return USER_FILTERS.find((filter) => filter.key === params.get('filter'))?.key ?? 'all';
}

export function readTaskFilter(params: ParamMap): TaskFilter {
  return TASK_FILTERS.find((filter) => filter.key === params.get('filter'))?.key ?? 'all';
}

export function readUserQuery(params: ParamMap): AdminUserQuery {
  const filter = readUserFilter(params);
  return {
    status: filter === 'suspended' ? 'SUSPENDED' : null,
    role: filter === 'taskers' ? 'TASKER' : null,
    search: params.get('q') ?? '',
    page: readPage(params),
  };
}

export function readTaskQuery(params: ParamMap): AdminTaskQuery {
  return {
    status: TASK_STATUS_BY_FILTER[readTaskFilter(params)],
    search: params.get('q') ?? '',
    page: readPage(params),
  };
}

export function sameQuery<T>(a: T, b: T): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}
