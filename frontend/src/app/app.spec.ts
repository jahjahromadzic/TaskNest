import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { ReferenceService } from './services/reference.service';
import { TaskService } from './services/task.service';

describe('App routes', () => {
  let harness: RouterTestingHarness;

  beforeAll(async () => {
    await import('./pages/browse-tasks/browse-tasks');
    await import('./pages/task-detail/task-detail');
  }, 60_000);

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ReferenceService, useValue: { getCategories: () => of([]), getMunicipalities: () => of([]) } },
        {
          provide: TaskService,
          useValue: {
            browse: () => of({ content: [], page: 0, totalElements: 0, totalPages: 0 }),
            getTask: () => of({ id: '123', title: 'Fix the kitchen sink', status: 'PUBLISHED' }),
          },
        },
      ],
    });
    harness = await RouterTestingHarness.create();
  });

  it('opens the task list at the root address', async () => {
    await harness.navigateByUrl('/');
    expect(harness.routeNativeElement?.textContent).toContain('Browse Local Tasks');
  });

  it('opens the details of a task from its address', async () => {
    await harness.navigateByUrl('/tasks/123');
    expect(harness.routeNativeElement?.textContent).toContain('Fix the kitchen sink');
  });

  it('shows the not found page for an unknown address', async () => {
    await harness.navigateByUrl('/nema-ovoga');
    expect(harness.routeNativeElement?.textContent).toContain('Page not found');
  });
});
