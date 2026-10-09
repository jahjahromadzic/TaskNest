import { Component, ElementRef, ViewChild, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AsyncPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import {
  BehaviorSubject,
  Observable,
  Subject,
  catchError,
  combineLatest,
  debounceTime,
  distinctUntilChanged,
  map,
  of,
  shareReplay,
  startWith,
  switchMap,
} from 'rxjs';
import {
  ArrowUpDown,
  CircleAlert,
  LayoutGrid,
  MapPin,
  Plus,
  RotateCcw,
  Search,
  SearchX,
  SlidersHorizontal,
  X,
} from 'lucide';
import { Icon } from '../../components/icon/icon';
import { Category, Municipality, TaskPage } from '../../api/models';
import { CategoryIcon } from '../../components/category-icon/category-icon';
import { Pagination } from '../../components/pagination/pagination';
import { Select, SelectOption } from '../../components/select/select';
import {
  SEARCHABLE_FROM,
  municipalityOptions,
  regionLabel,
  regionOf,
  regionOptions,
} from '../../shared/municipalities/municipalities';
import { TaskCard } from '../../components/task-card/task-card';
import { ReferenceService } from '../../services/reference.service';
import { TASK_SORTS, TaskFilters, TaskService, TaskSort } from '../../services/task.service';
import { CategoryPipe, TranslatePipe } from '../../i18n/translate.pipe';
import { t, tOptional } from '../../i18n/translate';

interface ListState {
  loading: boolean;
  failed: boolean;
  result: TaskPage | null;
}

export const SEARCH_MAX = 100;
const SEARCH_DELAY_MS = 300;

const LOADING: ListState = { loading: true, failed: false, result: null };
const FAILED: ListState = { loading: false, failed: true, result: null };

export function readFilters(params: ParamMap): TaskFilters {
  const sort = params.get('sort');
  const page = Number(params.get('page'));
  return {
    search: (params.get('q') ?? '').trim().slice(0, SEARCH_MAX),
    categoryId: params.get('category'),
    region: params.get('region'),
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
    TranslatePipe,
    CategoryPipe,
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
    Search,
    SearchX,
    SlidersHorizontal,
    X,
  };
  readonly searchMax = SEARCH_MAX;
  readonly searchText = signal('');

  readonly sortOptions: SelectOption[] = Object.entries(TASK_SORTS).map(([value, sort]) => ({
    value,
    get label() {
      return sort.label;
    },
  }));
  readonly skeletonCards = [1, 2, 3];
  readonly searchableFrom = SEARCHABLE_FROM;
  protected readonly regionLabel = regionLabel;

  readonly categories$: Observable<Category[]>;
  readonly municipalities$: Observable<Municipality[]>;
  readonly regionOptions$: Observable<SelectOption[]>;
  readonly region$: Observable<string | null>;
  readonly municipalityOptions$: Observable<SelectOption[]>;
  readonly filters$: Observable<TaskFilters>;
  readonly state$: Observable<ListState>;

  private readonly retry$ = new BehaviorSubject<void>(undefined);
  private readonly typed$ = new Subject<string>();

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
    this.filters$ = this.route.queryParamMap.pipe(map(readFilters), shareReplay(1));
    this.regionOptions$ = this.municipalities$.pipe(
      map((municipalities) => [
        {
          value: '',
          get label() {
            return t('browse.allRegions');
          },
        },
        ...regionOptions(municipalities),
      ]),
    );
    this.region$ = combineLatest([this.municipalities$, this.filters$]).pipe(
      map(([municipalities, filters]) => filters.region ?? regionOf(municipalities, filters.municipalityId)),
      distinctUntilChanged(),
      shareReplay(1),
    );
    this.municipalityOptions$ = combineLatest([this.municipalities$, this.region$]).pipe(
      map(([municipalities, region]) =>
        region
          ? [
              {
                value: '',
                get label() {
                  return t('browse.allMunicipalities');
                },
              },
              ...municipalityOptions(municipalities, region),
            ]
          : [],
      ),
    );
    this.filters$.pipe(takeUntilDestroyed()).subscribe((filters) => {
      if (filters.search !== this.searchText().trim()) {
        this.searchText.set(filters.search);
      }
    });
    this.typed$
      .pipe(debounceTime(SEARCH_DELAY_MS), takeUntilDestroyed())
      .subscribe((text) => this.applySearch(text.trim()));
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

  categoryLabel(categories: Category[] | null, id: string | null): string | undefined {
    const category = categories?.find((item) => item.id === id);
    return category ? (tOptional(`categories.${category.slug}`) ?? category.name) : undefined;
  }

  type(text: string): void {
    this.searchText.set(text);
    this.typed$.next(text);
  }

  searchNow(): void {
    this.applySearch(this.searchText().trim());
  }

  clearSearch(): void {
    this.searchText.set('');
    this.typed$.next('');
    this.applySearch('');
  }

  private applySearch(text: string): void {
    if (text === (this.route.snapshot.queryParamMap.get('q') ?? '')) {
      return;
    }
    this.updateQuery({ q: text || null });
  }

  setCategory(categoryId: string | null): void {
    this.updateQuery({ category: categoryId });
  }

  setRegion(region: string): void {
    this.updateQuery({ region: region || null, municipality: null });
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
