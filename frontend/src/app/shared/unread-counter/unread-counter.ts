import { signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { NavigationEnd, Router } from '@angular/router';
import {
  EMPTY,
  Observable,
  Subject,
  catchError,
  combineLatest,
  distinctUntilChanged,
  filter,
  fromEvent,
  map,
  merge,
  of,
  startWith,
  switchMap,
} from 'rxjs';
import { CurrentUser } from '../../auth/current-user';

export class UnreadCounter {
  readonly count = signal(0);

  private readonly recount$ = new Subject<void>();

  constructor(http: HttpClient, router: Router, user$: Observable<CurrentUser | null>, url: string) {
    const userId$ = user$.pipe(
      map((user) => user?.id ?? null),
      distinctUntilChanged(),
    );
    const checkAgain$ = merge(
      router.events.pipe(filter((event) => event instanceof NavigationEnd)),
      fromEvent(document, 'visibilitychange').pipe(filter(() => document.visibilityState === 'visible')),
      this.recount$,
    ).pipe(startWith(null));

    combineLatest([userId$, checkAgain$])
      .pipe(
        switchMap(([userId]) =>
          userId
            ? http.get<{ count: number }>(url).pipe(
                map((response) => response.count),
                catchError(() => EMPTY),
              )
            : of(0),
        ),
      )
      .subscribe((count) => this.count.set(count));
  }

  recount(): void {
    this.recount$.next();
  }

  lower(by = 1): void {
    this.count.update((count) => Math.max(0, count - by));
  }
}
