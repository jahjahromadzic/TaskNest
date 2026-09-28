import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { ClientHire, Review } from '../../api/models';
import { AuthService } from '../../auth/auth.service';
import { currentLang } from '../../i18n/lang';
import { ClientPublic } from './client-public';

describe('Public client profile', () => {
  let http: HttpTestingController;

  const profile = {
    userId: 'u3',
    fullName: 'Amra Hodžić',
    memberSince: '2026-09-01T10:00:00',
    averageRating: 4.5,
    reviewCount: 2,
    postedTasksCount: 5,
    hiresCount: 3,
    completedJobsCount: 2,
    cancelledTasksCount: 1,
  };

  function review(id: number): Review {
    return { id: `r${id}`, reviewerId: `t${id}`, reviewerName: `Tasker ${id}`, rating: 5, comment: `Pays on time ${id}`, taskTitle: 'Paint a kitchen' };
  }

  function hire(id: number): ClientHire {
    return {
      taskId: `task${id}`,
      title: `Job ${id}`,
      status: 'CLOSED',
      categorySlug: 'painting',
      categoryName: 'Painting',
      taskerId: 'u9',
      taskerName: 'Selma Karić',
      assignedAt: '2026-09-10T10:00:00',
    };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ userId: 'u3' }) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    currentLang.set('en');
  });

  async function open(reviews: { total: number; content: Review[] }, hires: { total: number; content: ClientHire[] }) {
    const fixture = TestBed.createComponent(ClientPublic);
    await fixture.whenStable();
    http.expectOne('/api/users/u3/client-profile').flush(profile);
    http
      .expectOne((request) => request.url === '/api/users/u3/reviews' && request.params.get('page') === '0')
      .flush({ content: reviews.content, totalElements: reviews.total });
    http
      .expectOne((request) => request.url === '/api/users/u3/hires' && request.params.get('page') === '0')
      .flush({ content: hires.content, totalElements: hires.total });
    await fixture.whenStable();
    return fixture;
  }

  function text(fixture: { nativeElement: HTMLElement }): string {
    return (fixture.nativeElement.textContent ?? '').replace(/\s+/g, ' ');
  }

  function tab(fixture: { nativeElement: HTMLElement }, index: number): HTMLButtonElement {
    return fixture.nativeElement.querySelectorAll<HTMLButtonElement>('[role=tab]')[index];
  }

  it('asks only for the reviews the person received as a client', async () => {
    const fixture = TestBed.createComponent(ClientPublic);
    await fixture.whenStable();

    const reviews = http.expectOne((request) => request.url === '/api/users/u3/reviews');
    expect(reviews.request.params.get('as')).toBe('CLIENT');
    reviews.flush({ content: [], totalElements: 0 });
    http.expectOne('/api/users/u3/client-profile').flush(profile);
    http.expectOne((request) => request.url === '/api/users/u3/hires').flush({ content: [], totalElements: 0 });
  });

  it('shows who the client is, their track record and what taskers say', async () => {
    const fixture = await open({ total: 2, content: [review(1), review(2)] }, { total: 3, content: [hire(1)] });
    const page = text(fixture);

    expect(page).toContain('Amra Hodžić');
    expect(page).toContain('Member since 1 Sept 2026');
    expect(page).toContain('5 tasks posted');
    expect(page).toContain('Hires3');
    expect(page).toContain('Completed2');
    expect(page).toContain('Cancelled1');
    expect(page).toContain('Pays on time 1');
    expect(fixture.nativeElement.querySelector('article a').getAttribute('href')).toBe('/taskers/t1');
    expect(tab(fixture, 0).getAttribute('aria-selected')).toBe('true');
    expect(TestBed.inject(Title).getTitle()).toBe('Amra Hodžić · TaskNest');
  });

  it('lists past hires with the tasker who did the job', async () => {
    const fixture = await open({ total: 0, content: [] }, { total: 1, content: [hire(1)] });

    tab(fixture, 1).click();
    await fixture.whenStable();

    const links = Array.from<HTMLAnchorElement>(fixture.nativeElement.querySelectorAll('article a')).map((link) => link.getAttribute('href'));
    expect(text(fixture)).toContain('Job 1');
    expect(text(fixture)).toContain('Selma Karić');
    expect(text(fixture)).toContain('Closed');
    expect(links).toEqual(['/tasks/task1', '/taskers/u9']);
  });

  it('loads more hires on demand', async () => {
    const fixture = await open({ total: 0, content: [] }, { total: 12, content: Array.from({ length: 10 }, (_, i) => hire(i)) });
    tab(fixture, 1).click();
    await fixture.whenStable();

    const more = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button')).find((button) => button.textContent?.includes('Show more hires'));
    more?.click();
    http
      .expectOne((request) => request.url === '/api/users/u3/hires' && request.params.get('page') === '1')
      .flush({ content: [hire(10), hire(11)], totalElements: 12 });
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelectorAll('article')).toHaveLength(12);
    expect(text(fixture)).not.toContain('Show more hires');
  });

  it('explains an empty history in Bosnian with the right plural', async () => {
    currentLang.set('bs');
    const fixture = await open({ total: 0, content: [] }, { total: 0, content: [] });

    expect(text(fixture)).toContain('5 objavljenih oglasa');
    expect(text(fixture)).toContain('Još nema recenzija');

    tab(fixture, 1).click();
    await fixture.whenStable();
    expect(text(fixture)).toContain('Ovaj klijent još nije zaposlio taskera.');
  });

  function signIn(id: string, roles: string[]): void {
    TestBed.inject(AuthService).login({ email: 'me@test.ba', password: 'password123' }).subscribe();
    http.expectOne('/api/auth/login').flush({ token: 't', userId: id, email: 'me@test.ba', fullName: 'Me', roles });
  }

  it('tells the owner this is how taskers see them and links to their tasker profile', async () => {
    signIn('u3', ['CLIENT', 'TASKER']);
    const fixture = await open({ total: 0, content: [] }, { total: 0, content: [] });

    expect(text(fixture)).toContain('This is how taskers see your profile.');
    const link = Array.from<HTMLAnchorElement>(fixture.nativeElement.querySelectorAll('a'))
      .find((anchor) => anchor.textContent?.includes('Your tasker profile'));
    expect(link?.getAttribute('href')).toBe('/taskers/u3');
  });

  it('shows no owner hints to anyone else, and no tasker link to a plain client', async () => {
    signIn('someone-else', ['CLIENT']);
    const fixture = await open({ total: 0, content: [] }, { total: 0, content: [] });

    expect(text(fixture)).not.toContain('This is how taskers see your profile.');
    expect(text(fixture)).not.toContain('Your tasker profile');
  });

  it('says so when the person does not exist', async () => {
    const fixture = TestBed.createComponent(ClientPublic);
    await fixture.whenStable();
    http.expectOne((request) => request.url === '/api/users/u3/reviews').flush({ content: [], totalElements: 0 });
    http.expectOne((request) => request.url === '/api/users/u3/hires').flush({ content: [], totalElements: 0 });
    http.expectOne('/api/users/u3/client-profile').flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(text(fixture)).toContain('This person could not be found');
  });
});
