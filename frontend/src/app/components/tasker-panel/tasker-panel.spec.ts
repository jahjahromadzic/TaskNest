import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Offer, TaskDetail } from '../../api/models';
import { ConfirmOptions, ConfirmService } from '../../shared/confirm/confirm.service';
import { TaskerPanel } from './tasker-panel';

describe('Tasker panel', () => {
  let fixture: ComponentFixture<TaskerPanel>;
  let http: HttpTestingController;
  let asked: ConfirmOptions[];
  let answer: boolean;
  let changed: number;

  const task: TaskDetail = { id: 't1', status: 'PUBLISHED', budget: 60, clientName: 'Amra Hodžić' };
  const pending: Offer = { id: 'o1', price: 55, message: 'Tomorrow', status: 'PENDING', createdAt: new Date().toISOString() };

  beforeEach(() => {
    asked = [];
    answer = true;
    changed = 0;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ConfirmService,
          useValue: {
            ask: (options: ConfirmOptions) => {
              asked.push(options);
              return Promise.resolve(answer);
            },
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TaskerPanel);
    fixture.componentInstance.changed.subscribe(() => changed++);
  });

  afterEach(() => http.verify());

  async function show(offer: Offer | null, forTask: Partial<TaskDetail> = {}): Promise<void> {
    fixture.componentRef.setInput('task', { ...task, ...forTask });
    await fixture.whenStable();
    const request = http.expectOne('/api/tasks/t1/offers/mine');
    if (offer) {
      request.flush(offer);
    } else {
      request.flush(null, { status: 204, statusText: 'No Content' });
    }
    await fixture.whenStable();
  }

  function text(): string {
    return fixture.nativeElement.textContent.replace(/\s+/g, ' ');
  }

  async function press(label: string): Promise<void> {
    Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button'))
      .find((button) => button.textContent!.includes(label))!
      .click();
    await fixture.whenStable();
  }

  it('asks for a price before sending an offer', async () => {
    await show(null);

    await press('Send offer');

    expect(text()).toContain('Enter a price above 0 KM');
    http.expectNone('/api/tasks/t1/offers');
  });

  it('refuses an offer of 0 KM', async () => {
    await show(null);
    const price = fixture.nativeElement.querySelector('#offer-price');
    price.value = '0';
    price.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    await press('Send offer');

    expect(text()).toContain('Enter a price above 0 KM');
    http.expectNone('/api/tasks/t1/offers');
  });

  it('sends the offer with the budget filled in and the message trimmed', async () => {
    await show(null);
    await press('Match budget');
    const message = fixture.nativeElement.querySelector('textarea');
    message.value = '  I can come tomorrow.  ';
    message.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    await press('Send offer');

    const request = http.expectOne({ method: 'POST', url: '/api/tasks/t1/offers' });
    expect(request.request.body).toEqual({ price: 60, message: 'I can come tomorrow.' });
    request.flush(pending);
    await fixture.whenStable();
    expect(changed).toBe(1);
  });

  it('shows a pending offer and withdraws it only after confirmation', async () => {
    await show(pending);
    expect(text()).toContain('55 KM');
    expect(text()).toContain('Waiting for Amra to choose');
    const chat = fixture.nativeElement.querySelector('a[href^="/messages"]');
    expect(chat.getAttribute('href')).toBe('/messages?offer=o1');
    expect(chat.textContent).toContain('Message Amra');

    answer = false;
    await press('Withdraw offer');
    http.expectNone('/api/offers/o1/withdraw');

    answer = true;
    await press('Withdraw offer');
    http.expectOne({ method: 'POST', url: '/api/offers/o1/withdraw' }).flush({ ...pending, status: 'WITHDRAWN' });
    await fixture.whenStable();
    expect(changed).toBe(1);
  });

  it('lets the hired tasker start the work and warns before backing out', async () => {
    await show({ ...pending, status: 'ACCEPTED' }, { status: 'ASSIGNED' });
    expect(text()).toContain("You're hired for 55 KM!");

    answer = false;
    await press('Back out of the job');
    expect(asked[0].tone).toBe('danger');
    expect(asked[0].message).toContain('withdrawn job');

    answer = true;
    await press('Start work');
    http.expectOne({ method: 'POST', url: '/api/tasks/t1/start' }).flush({});
  });

  it('lets the tasker mark a started job as done', async () => {
    await show({ ...pending, status: 'ACCEPTED' }, { status: 'IN_PROGRESS', startedAt: new Date().toISOString() });

    await press('Mark as done');

    http.expectOne({ method: 'POST', url: '/api/tasks/t1/complete' }).flush({});
  });

  it('explains when another tasker was chosen', async () => {
    await show({ ...pending, status: 'REJECTED' }, { status: 'ASSIGNED' });

    expect(text()).toContain('Amra chose another tasker for this job.');
    expect(fixture.nativeElement.querySelector('a[href^="/messages"]').textContent).toContain('View conversation');
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });

  it('offers no form once the task stopped taking offers', async () => {
    await show(null, { status: 'ASSIGNED' });

    expect(text()).toContain('This task no longer takes offers.');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });
});
