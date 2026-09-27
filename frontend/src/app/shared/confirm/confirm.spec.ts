import { TestBed } from '@angular/core/testing';
import { ConfirmDialog } from './confirm-dialog';
import { ConfirmService } from './confirm.service';

describe('Confirm dialog', () => {
  let service: ConfirmService;

  beforeEach(() => {
    service = TestBed.inject(ConfirmService);
  });

  const options = { title: 'Hire Emir for 55 KM?', message: 'The other offers are declined.', confirmLabel: 'Hire' };

  async function render() {
    const fixture = TestBed.createComponent(ConfirmDialog);
    await fixture.whenStable();
    return fixture;
  }

  it('answers yes when the confirm button is pressed', async () => {
    const fixture = await render();
    const answer = service.ask(options);
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Hire Emir for 55 KM?');
    Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button'))
      .find((button) => button.textContent!.includes('Hire'))!
      .click();

    await expect(answer).resolves.toBe(true);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role=alertdialog]')).toBeNull();
  });

  it('answers no on Cancel, on Escape and on a click outside the dialog', async () => {
    const fixture = await render();

    const cancelled = service.ask(options);
    await fixture.whenStable();
    Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button'))
      .find((button) => button.textContent!.includes('Cancel'))!
      .click();
    await expect(cancelled).resolves.toBe(false);

    const escaped = service.ask(options);
    await fixture.whenStable();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await expect(escaped).resolves.toBe(false);

    const outside = service.ask(options);
    await fixture.whenStable();
    fixture.nativeElement.querySelector('[aria-hidden=true]').click();
    await expect(outside).resolves.toBe(false);
  });

  it('declines an unanswered question when a new one is asked', async () => {
    const first = service.ask(options);
    const second = service.ask({ ...options, title: 'Something else?' });

    await expect(first).resolves.toBe(false);
    service.answer(true);
    await expect(second).resolves.toBe(true);
  });
});
