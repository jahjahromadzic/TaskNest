import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TaskerProfile } from '../../api/models';
import { ToastService } from '../../shared/toast/toast.service';
import { TaskerProfilePage } from './tasker-profile';

describe('Tasker profile page', () => {
  let fixture: ComponentFixture<TaskerProfilePage>;
  let page: TaskerProfilePage;
  let http: HttpTestingController;

  const plumbing = { id: 'c1', slug: 'plumbing', name: 'Plumbing' };
  const tiling = { id: 'c2', slug: 'tiling', name: 'Tiling' };
  const centar = { id: 'm1', name: 'Centar Sarajevo' };

  const fresh: TaskerProfile = {
    id: 'p1',
    fullName: 'Haris Mehić',
    headline: '',
    bio: '',
    completedJobsCount: 0,
    withdrawnJobsCount: 0,
    categories: [],
    municipalities: [],
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TaskerProfilePage);
    page = fixture.componentInstance;
  });

  afterEach(() => http.verify());

  async function open(profile: TaskerProfile): Promise<void> {
    await fixture.whenStable();
    http.expectOne('/api/tasker-profiles/me').flush(profile);
    http.expectOne('/api/categories').flush([plumbing, tiling]);
    http.expectOne('/api/municipalities').flush([centar]);
    await fixture.whenStable();
  }

  async function tapCategory(name: string): Promise<void> {
    Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('.category-tile'))
      .find((tile) => tile.textContent!.includes(name))!
      .click();
    await fixture.whenStable();
  }

  it('shows how complete the profile is and what is missing', async () => {
    await open({ ...fresh, headline: 'Handyman', categories: [plumbing] });

    expect(page.completeness()).toBe(50);
    expect(page.checklist().filter((item) => !item.done).map((item) => item.label)).toEqual([
      'A few words about you',
      'At least one municipality',
    ]);
  });

  it('can only be saved after something has changed', async () => {
    await open(fresh);
    expect(page.dirty()).toBe(false);

    await tapCategory('Plumbing');
    expect(page.dirty()).toBe(true);

    await tapCategory('Plumbing');
    expect(page.dirty()).toBe(false);
  });

  it('sends only the parts that changed', async () => {
    await open({ ...fresh, headline: 'Handyman', categories: [plumbing], municipalities: [centar] });

    await tapCategory('Tiling');
    page.save();

    const request = http.expectOne({ method: 'PUT', url: '/api/tasker-profiles/me/categories' });
    expect(request.request.body).toEqual({ ids: ['c1', 'c2'] });
    request.flush({ ...fresh, headline: 'Handyman', categories: [plumbing, tiling], municipalities: [centar] });
    http.expectNone('/api/tasker-profiles/me/municipalities');
    await fixture.whenStable();

    expect(page.dirty()).toBe(false);
    expect(TestBed.inject(ToastService).toasts()[0].text).toBe('Profile saved.');
  });

  it('refuses to remove the last category or municipality', async () => {
    await open({ ...fresh, categories: [plumbing], municipalities: [centar] });

    await tapCategory('Plumbing');
    page.save();

    http.expectNone('/api/tasker-profiles/me/categories');
    expect(TestBed.inject(ToastService).toasts()[0].text).toContain('Keep at least one category');
  });
});
