import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../auth/auth.service';
import { ReferenceService } from '../../services/reference.service';
import { ToastService } from '../../shared/toast/toast.service';
import { BecomeTasker } from './become-tasker';

describe('Become a tasker page', () => {
  let becomeTasker: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    becomeTasker = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '**', children: [] }]),
        { provide: AuthService, useValue: { becomeTasker } },
        {
          provide: ReferenceService,
          useValue: {
            getCategories: () => of([{ id: 'c1', slug: 'plumbing', name: 'Plumbing' }, { id: 'c2', slug: 'moving', name: 'Moving' }]),
            getMunicipalities: () => of([{ id: 'm1', name: 'Ilidža' }, { id: 'm2', name: 'Centar Sarajevo' }]),
          },
        },
      ],
    });
  });

  async function open() {
    const fixture = TestBed.createComponent(BecomeTasker);
    await fixture.whenStable();
    return fixture;
  }

  function submitButton(fixture: { nativeElement: HTMLElement }): HTMLButtonElement {
    return Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('aside button')).find((button) =>
      button.textContent!.includes('Become a tasker'),
    )!;
  }

  async function fillIn(fixture: Awaited<ReturnType<typeof open>>) {
    const headline: HTMLInputElement = fixture.nativeElement.querySelector('#profile-headline');
    headline.value = 'Plumber with 10 years of experience';
    headline.dispatchEvent(new Event('input'));
    const [plumbing] = fixture.nativeElement.querySelectorAll('.category-tile');
    plumbing.click();
    const ilidza = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button[role=checkbox]')).find((button) =>
      button.textContent!.includes('Ilidža'),
    )!;
    ilidza.click();
    await fixture.whenStable();
  }

  it('does not make anyone a tasker until the profile has what clients need', async () => {
    const fixture = await open();

    expect(submitButton(fixture).disabled).toBe(true);
    expect(fixture.nativeElement.querySelectorAll('aside li .text-status-success')).toHaveLength(0);

    fixture.componentInstance.submit();
    expect(becomeTasker).not.toHaveBeenCalled();
    expect(TestBed.inject(ToastService).toasts()[0].text).toBe('Add a headline, at least one category and one municipality first.');
  });

  it('saves the whole profile in one step and opens the dashboard', async () => {
    becomeTasker.mockReturnValue(of({}));
    const fixture = await open();
    await fillIn(fixture);

    expect(submitButton(fixture).disabled).toBe(false);
    submitButton(fixture).click();
    await fixture.whenStable();

    expect(becomeTasker).toHaveBeenCalledWith({
      headline: 'Plumber with 10 years of experience',
      bio: '',
      categoryIds: ['c1'],
      municipalityIds: ['m1'],
    });
    expect(TestBed.inject(Router).url).toBe('/tasker');
  });

  it('stays on the page with everything filled in and explains what went wrong', async () => {
    becomeTasker.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { detail: 'Tasker role is already active on this account' } })),
    );
    const fixture = await open();
    await fillIn(fixture);

    submitButton(fixture).click();
    await fixture.whenStable();

    expect(TestBed.inject(ToastService).toasts().at(-1)!.text).toBe('Tasker role is already active on this account');
    expect(fixture.componentInstance.saving()).toBe(false);
    expect(fixture.componentInstance.categoryIds()).toEqual(['c1']);
    expect(TestBed.inject(Router).url).toBe('/');
  });
});
