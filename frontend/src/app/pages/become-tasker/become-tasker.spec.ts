import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../auth/auth.service';
import { ToastService } from '../../shared/toast/toast.service';
import { BecomeTasker } from './become-tasker';

describe('Become a tasker page', () => {
  let becomeTasker: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    becomeTasker = vi.fn();
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '**', children: [] }]), { provide: AuthService, useValue: { becomeTasker } }],
    });
  });

  async function pressActivate() {
    const fixture = TestBed.createComponent(BecomeTasker);
    await fixture.whenStable();
    Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button'))
      .find((button) => button.textContent!.includes('Become a tasker'))!
      .click();
    await fixture.whenStable();
    return fixture;
  }

  it('activates the tasker role and opens the profile with a welcome', async () => {
    becomeTasker.mockReturnValue(of({}));

    await pressActivate();

    expect(becomeTasker).toHaveBeenCalledOnce();
    expect(TestBed.inject(Router).url).toBe('/tasker/profile?welcome=true');
  });

  it('stays on the page and explains what went wrong', async () => {
    becomeTasker.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { detail: 'Tasker role is already active on this account' } })),
    );

    const fixture = await pressActivate();

    expect(TestBed.inject(ToastService).toasts()[0].text).toBe('Tasker role is already active on this account');
    expect(fixture.componentInstance.activating()).toBe(false);
  });
});
