import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, convertToParamMap, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { TaskSummary } from '../../api/models';
import { readFilters } from './browse-tasks';

describe('Browse tasks page', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  let router: Router;

  const task: TaskSummary = {
    id: 't1',
    title: 'Fix the kitchen sink',
    budget: 80,
    status: 'PUBLISHED',
    categoryName: 'Plumbing',
    municipalityName: 'Centar Sarajevo',
    publishedAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 5 * 86_400_000).toISOString(),
  };

  beforeAll(async () => {
    await import('./browse-tasks');
  }, 60_000);

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
  });

  async function open(url: string): Promise<void> {
    await harness.navigateByUrl(url);
    http.expectOne('/api/categories').flush([{ id: 'c1', name: 'Plumbing' }]);
    http.expectOne('/api/municipalities').flush([{ id: 'm1', name: 'Centar Sarajevo' }]);
    await harness.fixture.whenStable();
  }

  function taskRequest(): TestRequest {
    return http.expectOne((req) => req.url === '/api/tasks');
  }

  function page(content: TaskSummary[], totalPages = 1) {
    return { content, page: 0, size: 10, totalElements: content.length, totalPages, first: true, last: true };
  }

  function text(): string {
    return harness.routeNativeElement?.textContent ?? '';
  }

  it('shows the tasks from the server', async () => {
    await open('/tasks');

    taskRequest().flush(page([task]));
    await harness.fixture.whenStable();

    expect(text()).toContain('1 task');
    expect(text()).toContain('Fix the kitchen sink');
    expect(text()).toContain('80 KM');
  });

  it('reads the filters from the address so a link opens the same list', async () => {
    await open('/tasks?category=c1&municipality=m1&sort=ending&page=3');

    const params = taskRequest().request.params;
    expect(params.get('categoryId')).toBe('c1');
    expect(params.get('municipalityId')).toBe('m1');
    expect(params.get('sort')).toBe('expiresAt,asc');
    expect(params.get('page')).toBe('2');
  });

  it('puts the chosen category in the address and starts again from the first page', async () => {
    await open('/tasks?page=2');
    taskRequest().flush(page([task], 3));
    await harness.fixture.whenStable();

    const button = Array.from<HTMLButtonElement>(harness.routeNativeElement!.querySelectorAll('.category-option'))
      .find((option) => option.textContent?.includes('Plumbing'))!;
    button.click();
    await harness.fixture.whenStable();

    expect(router.url).toBe('/tasks?category=c1');
    taskRequest().flush(page([]));
  });

  it('says so when nothing matches the filters', async () => {
    await open('/tasks?category=c1');

    taskRequest().flush(page([]));
    await harness.fixture.whenStable();

    expect(text()).toContain('No tasks here yet');
    expect(text()).toContain('Show all tasks');
  });

  it('offers to try again when the list cannot be loaded', async () => {
    await open('/tasks');

    taskRequest().flush(null, { status: 500, statusText: 'Server Error' });
    await harness.fixture.whenStable();
    expect(text()).toContain('Something went wrong');

    harness.routeNativeElement!.querySelector<HTMLButtonElement>('[role=alert] button')!.click();
    taskRequest().flush(page([task]));
    await harness.fixture.whenStable();

    expect(text()).toContain('Fix the kitchen sink');
  });

  it('searches a moment after the user stops typing and keeps the words in the address', async () => {
    await open('/tasks?category=c1');
    taskRequest().flush(page([task]));
    await harness.fixture.whenStable();

    const input = harness.routeNativeElement!.querySelector<HTMLInputElement>('#task-search')!;
    for (const typed of ['ves', 'ves ma', 'ves masina ']) {
      input.value = typed;
      input.dispatchEvent(new Event('input'));
    }
    http.expectNone((req) => req.url === '/api/tasks');
    await new Promise((resolve) => setTimeout(resolve, 350));
    await harness.fixture.whenStable();

    const params = taskRequest().request.params;
    expect(params.get('q')).toBe('ves masina');
    expect(params.get('categoryId')).toBe('c1');
    expect(router.url).toBe('/tasks?category=c1&q=ves%20masina');
  });

  it('says what was searched for when nothing matches and clears the search in one click', async () => {
    await open('/tasks?q=perilica');
    const request = taskRequest();
    expect(request.request.params.get('q')).toBe('perilica');
    request.flush(page([]));
    await harness.fixture.whenStable();

    expect(harness.routeNativeElement!.querySelector<HTMLInputElement>('#task-search')!.value).toBe('perilica');
    expect(text()).toContain('No open task matches “perilica”');

    harness.routeNativeElement!.querySelector<HTMLButtonElement>('[aria-label="Clear search"]')!.click();
    await harness.fixture.whenStable();

    expect(router.url).toBe('/tasks');
    expect(taskRequest().request.params.has('q')).toBe(false);
  });
});

describe('readFilters', () => {
  it('trims the search from the address and cuts it at 100 characters', () => {
    expect(readFilters(convertToParamMap({ q: '  perilica ' })).search).toBe('perilica');
    expect(readFilters(convertToParamMap({ q: 'a'.repeat(150) })).search).toHaveLength(100);
  });

  it('falls back to safe values when the address holds nonsense', () => {
    expect(readFilters(convertToParamMap({ sort: 'hack', page: '-4' }))).toEqual({
      search: '',
      categoryId: null,
      municipalityId: null,
      sort: 'newest',
      page: 0,
    });
  });
});
