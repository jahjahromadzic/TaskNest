import { Component, ElementRef, ViewChild } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { BehaviorSubject, Observable, catchError, combineLatest, map, of, shareReplay, startWith, switchMap } from 'rxjs';
import { ArrowRight, Briefcase, CircleAlert, Inbox, RotateCcw, Search, Star, TriangleAlert, UserPen } from 'lucide';
import { Offer, TaskPage, TaskerProfile } from '../../api/models';
import { Icon } from '../../components/icon/icon';
import { Pagination } from '../../components/pagination/pagination';
import { TaskCard } from '../../components/task-card/task-card';
import { OfferService } from '../../services/offer.service';
import { TaskService } from '../../services/task.service';
import { TaskerProfileService } from '../../services/tasker-profile.service';
import { formatBudget, timeAgo } from '../../shared/format/format';
import { OFFER_STATUS, OfferStatus } from '../../shared/task-status/task-status';
import { TranslatePipe } from '../../i18n/translate.pipe';
import { t, translated } from '../../i18n/translate';

export type DashboardTab = 'matching' | 'offers' | 'jobs';

export const DASHBOARD_TABS: { key: DashboardTab; readonly label: string }[] = [
  translated({ key: 'matching' as DashboardTab }, { label: 'dashboard.tabMatching' }),
  translated({ key: 'offers' as DashboardTab }, { label: 'dashboard.tabOffers' }),
  translated({ key: 'jobs' as DashboardTab }, { label: 'dashboard.tabJobs' }),
];

interface DashboardQuery {
  tab: DashboardTab;
  page: number;
}

interface ListState {
  loading: boolean;
  failed: boolean;
  page: TaskPage | null;
  offers: Offer[] | null;
}

interface Counts {
  matching: number;
  offers: number;
  jobs: number;
}

const LOADING: ListState = { loading: true, failed: false, page: null, offers: null };
const FAILED: ListState = { loading: false, failed: true, page: null, offers: null };

export function readDashboardQuery(params: ParamMap): DashboardQuery {
  const tab = DASHBOARD_TABS.find((candidate) => candidate.key === params.get('tab'))?.key ?? 'matching';
  const page = Number(params.get('page'));
  return { tab, page: Number.isInteger(page) && page > 1 ? page - 1 : 0 };
}

export function sortOffersNewestFirst(offers: Offer[]): Offer[] {
  const rank: Record<string, number> = { ACCEPTED: 0, PENDING: 1, REJECTED: 2, WITHDRAWN: 2 };
  return [...offers].sort(
    (a, b) => rank[a.status ?? ''] - rank[b.status ?? ''] || (b.createdAt ?? '').localeCompare(a.createdAt ?? ''),
  );
}

@Component({
  selector: 'app-tasker-dashboard',
  imports: [AsyncPipe, RouterLink, Icon, Pagination, TaskCard, TranslatePipe],
  templateUrl: './tasker-dashboard.html',
})
export class TaskerDashboard {
  protected readonly icons = { ArrowRight, Briefcase, CircleAlert, Inbox, RotateCcw, Search, Star, TriangleAlert, UserPen };

  readonly tabs = DASHBOARD_TABS;
  readonly skeletonCards = [1, 2, 3];
  readonly formatBudget = formatBudget;
  readonly timeAgo = timeAgo;

  readonly query$: Observable<DashboardQuery>;
  readonly profile$: Observable<TaskerProfile | null>;
  readonly offers$: Observable<Offer[] | null>;
  readonly counts$: Observable<Counts | null>;
  readonly state$: Observable<ListState>;

  private readonly retry$ = new BehaviorSubject<void>(undefined);

  @ViewChild('list') private list?: ElementRef<HTMLElement>;

  constructor(
    private taskService: TaskService,
    private offerService: OfferService,
    private taskerProfileService: TaskerProfileService,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    this.query$ = this.route.queryParamMap.pipe(map(readDashboardQuery), shareReplay(1));
    this.profile$ = this.taskerProfileService.getMine().pipe(
      catchError(() => of(null)),
      shareReplay(1),
    );
    this.offers$ = this.retry$.pipe(
      switchMap(() =>
        this.offerService.getMine().pipe(
          map(sortOffersNewestFirst),
          catchError(() => of(null)),
        ),
      ),
      shareReplay(1),
    );
    this.counts$ = combineLatest([
      this.taskService.getMatching(0, 1).pipe(map((page) => page.totalElements ?? 0)),
      this.offers$.pipe(map((offers) => (offers ?? []).filter((offer) => offer.status === 'PENDING').length)),
      this.taskService.getAssigned(0, 1).pipe(map((page) => page.totalElements ?? 0)),
    ]).pipe(
      map(([matching, offers, jobs]) => ({ matching, offers, jobs })),
      catchError(() => of(null)),
      shareReplay(1),
    );
    this.state$ = combineLatest([this.query$, this.retry$]).pipe(
      switchMap(([query]) => {
        if (query.tab === 'offers') {
          return this.offers$.pipe(
            map((offers): ListState => (offers ? { loading: false, failed: false, page: null, offers } : FAILED)),
          );
        }
        const request$ = query.tab === 'jobs' ? this.taskService.getAssigned(query.page) : this.taskService.getMatching(query.page);
        return request$.pipe(
          map((page): ListState => ({ loading: false, failed: false, page, offers: null })),
          startWith(LOADING),
          catchError(() => of(FAILED)),
        );
      }),
    );
  }

  count(counts: Counts | null, tab: DashboardTab): number | null {
    return counts ? counts[tab] : null;
  }

  offerNote(taskId: string | undefined, offers: Offer[] | null): string | null {
    const offer = offers?.find((candidate) => candidate.taskId === taskId);
    return offer ? t('card.offerSent', { price: formatBudget(offer.price) }) : null;
  }

  offerStyle(status: Offer['status']) {
    return OFFER_STATUS[(status ?? 'PENDING') as OfferStatus];
  }

  profileIncomplete(profile: TaskerProfile | null): boolean {
    return profile !== null && ((profile.categories ?? []).length === 0 || (profile.municipalities ?? []).length === 0);
  }

  selectTab(tab: DashboardTab): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: { tab: tab === 'matching' ? null : tab } });
    this.scrollListToTop();
  }

  goToPage(page: number): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: page > 0 ? page + 1 : null },
      queryParamsHandling: 'merge',
    });
    this.scrollListToTop();
  }

  retry(): void {
    this.retry$.next();
  }

  private scrollListToTop(): void {
    this.list?.nativeElement.scrollTo?.({ top: 0, behavior: 'smooth' });
    if (window.scrollY > 0) {
      window.scrollTo?.({ top: 0, behavior: 'smooth' });
    }
  }
}
