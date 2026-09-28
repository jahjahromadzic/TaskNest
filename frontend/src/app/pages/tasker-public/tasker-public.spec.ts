import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Review } from '../../api/models';
import { AuthService } from '../../auth/auth.service';
import { TaskerPublic } from './tasker-public';

describe('Public tasker profile', () => {
  let http: HttpTestingController;

  const profile = {
    userId: 'u7',
    fullName: 'Adnan Delić',
    headline: 'Moving and furniture assembly',
    averageRating: 4.5,
    completedJobsCount: 2,
    withdrawnJobsCount: 0,
    verified: true,
    categories: [{ id: 'c1', slug: 'moving', name: 'Moving' }],
    municipalities: [{ id: 'm1', name: 'Ilidža' }],
  };

  function review(id: number): Review {
    return { id: `r${id}`, reviewerName: `Client ${id}`, rating: 5, comment: `Great job ${id}`, taskTitle: 'Move a flat' };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ userId: 'u7' }) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(totalReviews: number, firstPage: Review[]) {
    const fixture = TestBed.createComponent(TaskerPublic);
    await fixture.whenStable();
    http.expectOne('/api/tasker-profiles/users/u7').flush(profile);
    http
      .expectOne((request) => request.url === '/api/users/u7/reviews' && request.params.get('page') === '0')
      .flush({ content: firstPage, totalElements: totalReviews });
    await fixture.whenStable();
    return fixture;
  }

  it('shows who the tasker is, what they do and what clients say', async () => {
    const fixture = await open(1, [review(1)]);
    const text = fixture.nativeElement.textContent.replace(/\s+/g, ' ');

    expect(text).toContain('Adnan Delić');
    expect(text).toContain('Verified');
    expect(text).toContain('Moving');
    expect(text).toContain('Ilidža');
    expect(text).toContain('Great job 1');
    expect(TestBed.inject(Title).getTitle()).toBe('Adnan Delić · TaskNest');
  });

  it('asks only for the reviews the person received as a tasker', async () => {
    const fixture = TestBed.createComponent(TaskerPublic);
    await fixture.whenStable();

    const reviews = http.expectOne((request) => request.url === '/api/users/u7/reviews');
    expect(reviews.request.params.get('as')).toBe('TASKER');
    reviews.flush({ content: [], totalElements: 0 });
    http.expectOne('/api/tasker-profiles/users/u7').flush(profile);
  });

  it('tells the owner this is how clients see them, with links to edit and to their client profile', async () => {
    TestBed.inject(AuthService).login({ email: 'me@test.ba', password: 'password123' }).subscribe();
    http.expectOne('/api/auth/login').flush({ token: 't', userId: 'u7', email: 'me@test.ba', fullName: 'Me', roles: ['CLIENT', 'TASKER'] });
    const fixture = await open(0, []);

    expect(fixture.nativeElement.textContent).toContain('This is how clients see your tasker profile.');
    const links = Array.from<HTMLAnchorElement>(fixture.nativeElement.querySelectorAll('a')).map((link) => link.getAttribute('href'));
    expect(links).toContain('/tasker/profile');
    expect(links).toContain('/clients/u7');
  });

  it('loads the next page of reviews on demand', async () => {
    const fixture = await open(12, Array.from({ length: 10 }, (_, i) => review(i)));

    fixture.nativeElement.querySelector('section button').click();
    http
      .expectOne((request) => request.url === '/api/users/u7/reviews' && request.params.get('page') === '1')
      .flush({ content: [review(10), review(11)], totalElements: 12 });
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelectorAll('article')).toHaveLength(12);
    expect(fixture.nativeElement.textContent).not.toContain('Show more reviews');
  });

  it('says so when the person is not a tasker', async () => {
    const fixture = TestBed.createComponent(TaskerPublic);
    await fixture.whenStable();
    const reviews = http.expectOne((request) => request.url === '/api/users/u7/reviews');
    http.expectOne('/api/tasker-profiles/users/u7').flush({ detail: 'Not found' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('This tasker could not be found');
    expect(reviews.cancelled).toBe(true);
  });
});
