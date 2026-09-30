import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { ClientHire, ClientProfile, Review, TaskerProfile } from '../../api/models';
import { routes } from '../../app.routes';
import { AuthService } from '../../auth/auth.service';
import { currentLang } from '../../i18n/lang';
import { UserProfile } from './user-profile';

type Page<T> = { total: number; content: T[] };

const none = { total: 0, content: [] };

describe('User profile', () => {
  let http: HttpTestingController;
  let query: Record<string, string>;

  const client: ClientProfile = {
    userId: 'u7',
    fullName: 'Adnan Delić',
    memberSince: '2026-09-01T10:00:00',
    averageRating: 4.5,
    reviewCount: 1,
    postedTasksCount: 1,
    hiresCount: 0,
    completedJobsCount: 0,
    cancelledTasksCount: 0,
  };

  const tasker: TaskerProfile = {
    userId: 'u7',
    fullName: 'Adnan Delić',
    headline: 'Moving and furniture assembly',
    averageRating: 4.7,
    completedJobsCount: 3,
    withdrawnJobsCount: 0,
    verified: true,
    categories: [{ id: 'c1', slug: 'moving', name: 'Moving' }],
    municipalities: [{ id: 'm1', name: 'Ilidža' }],
  };

  function review(id: number, text: string): Review {
    return { id: `r${id}`, reviewerId: `p${id}`, reviewerName: `Person ${id}`, rating: 5, comment: `${text} ${id}`, taskTitle: 'A job' };
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
    query = {};
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useFactory: () => ({
            snapshot: { paramMap: convertToParamMap({ userId: 'u7' }), queryParamMap: convertToParamMap(query) },
          }),
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    currentLang.set('en');
  });

  async function open(data: {
    tasker?: TaskerProfile | null;
    client?: ClientProfile;
    taskerReviews?: Page<Review>;
    clientReviews?: Page<Review>;
    hires?: Page<ClientHire>;
  }) {
    const fixture = TestBed.createComponent(UserProfile);
    await fixture.whenStable();
    http.expectOne('/api/users/u7/client-profile').flush(data.client ?? client);
    const taskerRequest = http.expectOne('/api/tasker-profiles/users/u7');
    if (data.tasker === null) {
      taskerRequest.flush(null, { status: 404, statusText: 'Not Found' });
    } else {
      taskerRequest.flush(data.tasker ?? tasker);
    }
    for (const [as, page] of [['TASKER', data.taskerReviews ?? none], ['CLIENT', data.clientReviews ?? none]] as const) {
      http
        .expectOne((request) => request.url === '/api/users/u7/reviews' && request.params.get('as') === as && request.params.get('page') === '0')
        .flush({ content: page.content, totalElements: page.total });
    }
    const hires = data.hires ?? none;
    http
      .expectOne((request) => request.url === '/api/users/u7/hires' && request.params.get('page') === '0')
      .flush({ content: hires.content, totalElements: hires.total });
    await fixture.whenStable();
    return fixture;
  }

  function text(fixture: { nativeElement: HTMLElement }): string {
    return (fixture.nativeElement.textContent ?? '').replace(/\s+/g, ' ');
  }

  function button(fixture: { nativeElement: HTMLElement }, label: string): HTMLButtonElement {
    return Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button')).find((b) => b.textContent?.includes(label))!;
  }

  function hrefs(fixture: { nativeElement: HTMLElement }): (string | null)[] {
    return Array.from<HTMLAnchorElement>(fixture.nativeElement.querySelectorAll('article a')).map((link) => link.getAttribute('href'));
  }

  it('opens a busy tasker on their tasker side, with what clients say about their work', async () => {
    const fixture = await open({ taskerReviews: { total: 1, content: [review(1, 'Great work')] } });
    const page = text(fixture);

    expect(page).toContain('Adnan Delić');
    expect(page).toContain('Verified');
    expect(page).toContain('Moving and furniture assembly');
    expect(page).toContain('Ilidža');
    expect(page).toContain('Reviews from clients');
    expect(page).toContain('Great work 1');
    expect(button(fixture, 'As tasker').getAttribute('aria-selected')).toBe('true');
    expect(hrefs(fixture)).toEqual(['/users/p1?as=client']);
    expect(TestBed.inject(Title).getTitle()).toBe('Adnan Delić · TaskNest');
  });

  it('switches to the client side in place and keeps the choice in the address', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = await open({
      clientReviews: { total: 1, content: [review(2, 'Pays on time')] },
      hires: { total: 1, content: [hire(1)] },
    });

    button(fixture, 'As client').click();
    await fixture.whenStable();

    expect(text(fixture)).toContain('1 task posted');
    expect(text(fixture)).toContain('Pays on time 2');
    expect(text(fixture)).not.toContain('Moving and furniture assembly Ilidža');
    expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { as: 'client' }, replaceUrl: true }));

    button(fixture, 'Past hires').click();
    await fixture.whenStable();
    expect(hrefs(fixture)).toEqual(['/tasks/task1', '/users/u9?as=tasker']);
  });

  it('opens on the side named in the link', async () => {
    query = { as: 'client' };
    const fixture = await open({ taskerReviews: { total: 3, content: [review(1, 'Great work')] } });

    expect(button(fixture, 'As client').getAttribute('aria-selected')).toBe('true');
    expect(text(fixture)).toContain('Reviews from taskers');
  });

  it('shows only the client side, without a role switch, for someone who is not a tasker', async () => {
    query = { as: 'tasker' };
    const fixture = await open({ tasker: null, hires: { total: 1, content: [hire(1)] } });

    expect(fixture.nativeElement.querySelector('[role=tablist][aria-label]')).toBeNull();
    expect(text(fixture)).toContain('Reviews from taskers');
    expect(text(fixture)).toContain('Past hires');
    expect(text(fixture)).not.toContain('Verified');
  });

  it('explains an empty client side instead of showing bare zeros', async () => {
    query = { as: 'client' };
    currentLang.set('bs');
    const fixture = await open({ client: { ...client, postedTasksCount: 0, averageRating: undefined } });

    expect(text(fixture)).toContain('0 objavljenih oglasa');
    expect(text(fixture)).toContain('Još nema objavljenih oglasa, pa taskeri nemaju šta ocijeniti.');
  });

  it('loads more on demand', async () => {
    query = { as: 'client' };
    const fixture = await open({ hires: { total: 12, content: Array.from({ length: 10 }, (_, i) => hire(i)) } });
    button(fixture, 'Past hires').click();
    await fixture.whenStable();

    button(fixture, 'Show more').click();
    http
      .expectOne((request) => request.url === '/api/users/u7/hires' && request.params.get('page') === '1')
      .flush({ content: [hire(10), hire(11)], totalElements: 12 });
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelectorAll('article')).toHaveLength(12);
    expect(button(fixture, 'Show more')).toBeUndefined();
  });

  it('tells the owner this is how others see them, with a link to edit the tasker profile', async () => {
    TestBed.inject(AuthService).login({ email: 'me@test.ba', password: 'password123' }).subscribe();
    http.expectOne('/api/auth/login').flush({ token: 't', userId: 'u7', email: 'me@test.ba', fullName: 'Me', roles: ['CLIENT', 'TASKER'] });
    const fixture = await open({});

    expect(text(fixture)).toContain('This is how others see your profile.');
    expect(fixture.nativeElement.querySelector('a[href="/tasker/profile"]')).not.toBeNull();
  });

  it('shows no owner hints to anyone else', async () => {
    const fixture = await open({});

    expect(text(fixture)).not.toContain('This is how others see your profile.');
  });

  it('says so when the person does not exist', async () => {
    const fixture = TestBed.createComponent(UserProfile);
    await fixture.whenStable();
    http.expectOne('/api/tasker-profiles/users/u7').flush(null, { status: 404, statusText: 'Not Found' });
    http.match((request) => request.url === '/api/users/u7/reviews').forEach((request) => request.flush({ content: [], totalElements: 0 }));
    http.expectOne((request) => request.url === '/api/users/u7/hires').flush({ content: [], totalElements: 0 });
    http.expectOne('/api/users/u7/client-profile').flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(text(fixture)).toContain('This person could not be found');
  });
});

describe('Old profile links', () => {
  it('lead to the unified profile on the matching side', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()] });
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/taskers/u7');
    expect(router.url).toBe('/login?returnUrl=%2Fusers%2Fu7%3Fas%3Dtasker');

    await router.navigateByUrl('/clients/u3');
    expect(router.url).toBe('/login?returnUrl=%2Fusers%2Fu3%3Fas%3Dclient');
  });
});
