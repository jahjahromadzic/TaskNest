import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TaskDetail, TaskOffer } from '../../api/models';
import { ConfirmService } from '../../shared/confirm/confirm.service';
import { TaskOffers, sortOffers } from './task-offers';

describe('Task offers', () => {
  let fixture: ComponentFixture<TaskOffers>;
  let http: HttpTestingController;
  let confirmAnswer: boolean;

  const task: TaskDetail = { id: 't1', status: 'PUBLISHED', categoryName: 'Plumbing', municipalityName: 'Centar' };

  const emir: TaskOffer = {
    id: 'o1',
    price: 70,
    status: 'PENDING',
    taskerName: 'Emir Kovačević',
    taskerRating: 4.5,
    taskerReviewCount: 2,
    taskerCompletedJobs: 3,
    taskerVerified: true,
    createdAt: '2026-09-20T10:00:00',
  };
  const tarik: TaskOffer = {
    id: 'o2',
    price: 55,
    status: 'PENDING',
    taskerName: 'Tarik Hasanović',
    taskerCompletedJobs: 0,
    taskerReviewCount: 0,
    createdAt: '2026-09-21T10:00:00',
  };
  const adnan: TaskOffer = { id: 'o3', price: 90, status: 'WITHDRAWN', taskerName: 'Adnan Delić' };

  beforeEach(async () => {
    confirmAnswer = true;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ConfirmService, useValue: { ask: () => Promise.resolve(confirmAnswer) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TaskOffers);
  });

  afterEach(() => http.verify());

  async function show(offers: TaskOffer[], forTask: TaskDetail = task): Promise<void> {
    fixture.componentRef.setInput('task', forTask);
    await fixture.whenStable();
    http.expectOne('/api/tasks/t1/offers').flush(offers);
    await fixture.whenStable();
  }

  function cards(): string[] {
    return Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('article')).map((card) =>
      card.textContent!.replace(/\s+/g, ' '),
    );
  }

  function acceptButton(name: string): HTMLButtonElement {
    const card = Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('article')).find((article) =>
      article.textContent!.includes(name),
    )!;
    return card.querySelector('button')!;
  }

  it('lists the cheapest offer first, marks it and shows the reputation of each tasker', async () => {
    await show([emir, tarik, adnan]);

    expect(cards()[0]).toContain('Tarik Hasanović');
    expect(cards()[0]).toContain('Lowest price');
    expect(cards()[0]).toContain('New on TaskNest');
    expect(cards()[1]).toContain('4.5 (2 reviews)');
    expect(cards()[1]).toContain('3 jobs done');
    expect(fixture.nativeElement.textContent).toContain('Declined and withdrawn offers (1)');
  });

  it('hires the tasker after the client confirms', async () => {
    await show([emir, tarik]);
    let accepted = false;
    fixture.componentInstance.accepted.subscribe(() => (accepted = true));

    acceptButton('Emir').click();
    await fixture.whenStable();

    http.expectOne({ method: 'POST', url: '/api/offers/o1/accept' }).flush({});
    await fixture.whenStable();
    expect(accepted).toBe(true);
  });

  it('does nothing when the client changes their mind', async () => {
    confirmAnswer = false;
    await show([emir, tarik]);

    acceptButton('Emir').click();
    await fixture.whenStable();

    http.expectNone('/api/offers/o1/accept');
  });

  it('offers no accept button once the task is assigned', async () => {
    await show([{ ...emir, status: 'ACCEPTED' }, { ...tarik, status: 'REJECTED' }], { ...task, status: 'ASSIGNED' });

    expect(cards()).toHaveLength(1);
    expect(cards()[0]).toContain('Hired');
    expect(fixture.nativeElement.querySelector('article button')).toBeNull();
  });

  it('offers no accept button on an expired task even if offers are still pending', async () => {
    await show([emir, tarik], { ...task, status: 'EXPIRED' });

    expect(cards()).toHaveLength(2);
    expect(fixture.nativeElement.querySelector('article button')).toBeNull();
  });

  it('tells the client who was notified when there are no offers yet', async () => {
    await show([]);

    expect(fixture.nativeElement.textContent).toContain('Taskers who cover Plumbing in Centar were notified.');
  });
});

describe('sortOffers', () => {
  const offers: TaskOffer[] = [
    { id: 'cheap-new', price: 40, taskerCompletedJobs: 0, createdAt: '2026-09-22' },
    { id: 'rated', price: 80, taskerRating: 4.9, taskerCompletedJobs: 10, createdAt: '2026-09-20' },
    { id: 'middle', price: 60, taskerRating: 4.2, taskerCompletedJobs: 2, createdAt: '2026-09-21' },
  ];

  it('sorts by price, by rating or by date', () => {
    expect(sortOffers(offers, 'price').map((offer) => offer.id)).toEqual(['cheap-new', 'middle', 'rated']);
    expect(sortOffers(offers, 'rating').map((offer) => offer.id)).toEqual(['rated', 'middle', 'cheap-new']);
    expect(sortOffers(offers, 'newest').map((offer) => offer.id)).toEqual(['cheap-new', 'middle', 'rated']);
  });
});
