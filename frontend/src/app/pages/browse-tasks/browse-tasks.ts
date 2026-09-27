import { Component, ElementRef, ViewChild } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { BehaviorSubject, Observable, catchError, combineLatest, map, of, shareReplay, startWith, switchMap } from 'rxjs';
import {
  ArrowUpDown,
  CircleAlert,
  LayoutGrid,
  MapPin,
  Plus,
  RotateCcw,
  SearchX,
  SlidersHorizontal,
} from 'lucide';
import { Icon } from '../../components/icon/icon';
import { Category, Municipality, TaskPage } from '../../api/models';
import { CategoryIcon } from '../../components/category-icon/category-icon';
import { Pagination } from '../../components/pagination/pagination';
import { Select, SelectOption } from '../../components/select/select';
import { TaskCard } from '../../components/task-card/task-card';
import { ReferenceService } from '../../services/reference.service';
import { TASK_SORTS, TaskFilters, TaskService, TaskSort } from '../../services/task.service';

interface ListState {
  loading: boolean;
  failed: boolean;
  result: TaskPage | null;
}

const LOADING: ListState = { loading: true, failed: false, result: null };
const FAILED: ListState = { loading: false, failed: true, result: null };

export function readFilters(params: ParamMap): TaskFilters {
  const sort = params.get('sort');
  const page = Number(params.get('page'));
  return {
    categoryId: params.get('category'),
    municipalityId: params.get('municipality'),
    sort: sort && sort in TASK_SORTS ? (sort as TaskSort) : 'newest',
    page: Number.isInteger(page) && page > 1 ? page - 1 : 0,
  };
}

@Component({
  selector: 'app-browse-tasks',
  imports: [
    Icon,
    AsyncPipe,
    FormsModule,
    RouterLink,
    CategoryIcon,
    Pagination,
    Select,
    TaskCard,
  ],
  templateUrl: './browse-tasks.html',
})
export class BrowseTasks {
  protected readonly icons = {
    ArrowUpDown,
    CircleAlert,
    LayoutGrid,
    MapPin,
    Plus,
    RotateCcw,
    SearchX,
    SlidersHorizontal,
  };

  readonly sortOptions: SelectOption[] = Object.entries(TASK_SORTS).map(([value, sort]) => ({ value, label: sort.label }));
  readonly skeletonCards = [1, 2, 3];

  readonly categories$: Observable<Category[]>;
  readonly municipalities$: Observable<Municipality[]>;
  readonly municipalityOptions$: Observable<SelectOption[]>;
  readonly filters$: Observable<TaskFilters>;
  readonly state$: Observable<ListState>;

  private readonly retry$ = new BehaviorSubject<void>(undefined);

  @ViewChild('list') private list?: ElementRef<HTMLElement>;

  constructor(
    private taskService: TaskService,
    private referenceService: ReferenceService,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    this.categories$ = this.referenceService.getCategories().pipe(
      catchError(() => of([])),
      shareReplay(1),
    );
    this.municipalities$ = this.referenceService.getMunicipalities().pipe(
      catchError(() => of([])),
      shareReplay(1),
    );
    this.municipalityOptions$ = this.municipalities$.pipe(
      map((municipalities) => [
        { value: '', label: 'All municipalities' },
        ...municipalities.map((municipality) => ({ value: municipality.id ?? '', label: municipality.name ?? '' })),
      ]),
    );
    this.filters$ = this.route.queryParamMap.pipe(map(readFilters), shareReplay(1));
    this.state$ = combineLatest([this.filters$, this.retry$]).pipe(
      switchMap(([filters]) =>
        this.taskService.browse(filters).pipe(
          map((result): ListState => ({ loading: false, failed: false, result })),
          startWith(LOADING),
          catchError(() => of(FAILED)),
        ),
      ),
    );
  }

  nameOf(items: { id?: string; name?: string }[] | null, id: string | null): string | undefined {
    return items?.find((item) => item.id === id)?.name;
  }

  setCategory(categoryId: string | null): void {
    this.updateQuery({ category: categoryId });
  }

  setMunicipality(municipalityId: string): void {
    this.updateQuery({ municipality: municipalityId || null });
  }

  setSort(sort: string): void {
    this.updateQuery({ sort: sort === 'newest' ? null : sort });
  }

  goToPage(page: number): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: page > 0 ? page + 1 : null },
      queryParamsHandling: 'merge',
    });
    this.scrollListToTop();
  }

  resetFilters(): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: {} });
    this.scrollListToTop();
  }

  retry(): void {
    this.retry$.next();
  }

  private updateQuery(changes: Record<string, string | null>): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { ...changes, page: null },
      queryParamsHandling: 'merge',
    });
    this.scrollListToTop();
  }

  private scrollListToTop(): void {
    this.list?.nativeElement.scrollTo?.({ top: 0, behavior: 'smooth' });
    if (window.scrollY > 0) {
      window.scrollTo?.({ top: 0, behavior: 'smooth' });
    }
  }
}
