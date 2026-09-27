import { TestBed } from '@angular/core/testing';
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
  }, 60_000);

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        { provide: ReferenceService, useValue: { getCategories: () => of([]), getMunicipalities: () => of([]) } },
        { provide: TaskService, useValue: { browse: () => of({ content: [], page: 0, totalElements: 0, totalPages: 0 }) } },
      ],
    });
    harness = await RouterTestingHarness.create();
  });

  it('opens the task list at the root address', async () => {
    await harness.navigateByUrl('/');
    expect(harness.routeNativeElement?.textContent).toContain('Browse Local Tasks');
  });

  it('shows the placeholder with its heading for a page not built yet', async () => {
    await harness.navigateByUrl('/tasks/123');
    expect(harness.routeNativeElement?.textContent).toContain('Task details');
    expect(harness.routeNativeElement?.textContent).toContain('phase 2');
  });

  it('shows the not found page for an unknown address', async () => {
    await harness.navigateByUrl('/nema-ovoga');
    expect(harness.routeNativeElement?.textContent).toContain('Page not found');
  });
});
