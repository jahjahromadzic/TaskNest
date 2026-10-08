import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { currentLang } from '../../i18n/lang';
import { ToastService } from '../../shared/toast/toast.service';
import { ReportDialog } from './report-dialog';

describe('Report dialog', () => {
  let fixture: ComponentFixture<ReportDialog>;
  let http: HttpTestingController;
  let sent: number;

  beforeEach(async () => {
    sent = 0;
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ReportDialog);
    fixture.componentRef.setInput('target', 'TASK');
    fixture.componentRef.setInput('targetId', 't1');
    fixture.componentRef.setInput('targetName', 'Work from home, 500 KM a day');
    fixture.componentInstance.sent.subscribe(() => sent++);
    await fixture.whenStable();
  });

  afterEach(() => {
    http.verify();
    currentLang.set('en');
  });

  function element(): HTMLElement {
    return fixture.nativeElement;
  }

  async function choose(label: string): Promise<void> {
    Array.from<HTMLLabelElement>(element().querySelectorAll('fieldset label'))
      .find((candidate) => candidate.textContent?.includes(label))!
      .querySelector('input')!
      .dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  async function type(text: string): Promise<void> {
    const textarea = element().querySelector('textarea')!;
    textarea.value = text;
    textarea.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function send(): Promise<void> {
    element().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  it('needs a reason, and a description when the reason is Something else', async () => {
    await send();
    expect(element().querySelector('[role=alert]')!.textContent).toContain('Choose what is wrong');

    await choose('Something else');
    await send();
    expect(element().querySelector('[role=alert]')!.textContent).toContain('Describe the problem');

    http.expectNone('/api/tasks/t1/reports');
  });

  it('sends the reason with the trimmed description and thanks the user', async () => {
    await choose('Spam or advertising');
    await type('  Asks for payment on WhatsApp ');
    await send();

    const request = http.expectOne({ method: 'POST', url: '/api/tasks/t1/reports' });
    expect(request.request.body).toEqual({ reason: 'SPAM', comment: 'Asks for payment on WhatsApp' });
    request.flush({ id: 'r1', status: 'OPEN' });
    await fixture.whenStable();

    expect(sent).toBe(1);
    expect(TestBed.inject(ToastService).toasts()[0].text).toBe('Thank you. An administrator will review your report.');
  });

  it('reports a user through the user address', async () => {
    fixture.componentRef.setInput('target', 'USER');
    fixture.componentRef.setInput('targetId', 'u7');
    await fixture.whenStable();

    await choose('Did not show up or did not finish the job');
    await send();

    expect(http.expectOne({ method: 'POST', url: '/api/users/u7/reports' }).request.body).toEqual({
      reason: 'NO_SHOW',
      comment: undefined,
    });
  });

  it('explains in Bosnian that the task was already reported', async () => {
    currentLang.set('bs');
    await fixture.whenStable();
    await choose('Spam ili reklama');
    await send();

    http
      .expectOne('/api/tasks/t1/reports')
      .flush(
        { status: 400, detail: 'You have already reported this. An administrator will review it.' },
        { status: 400, statusText: 'Bad Request' },
      );
    await fixture.whenStable();

    expect(element().querySelector('[role=alert]')!.textContent).toContain('Ovo si već prijavio/la.');
    expect(sent).toBe(0);
  });
});
