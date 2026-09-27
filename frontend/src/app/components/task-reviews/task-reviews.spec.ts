import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Review, TaskDetail } from '../../api/models';
import { TaskReviews } from './task-reviews';

describe('Task reviews', () => {
  let fixture: ComponentFixture<TaskReviews>;
  let http: HttpTestingController;

  const task: TaskDetail = {
    id: 't1',
    title: 'Paint the kitchen',
    status: 'CLOSED',
    clientId: 'amra',
    clientName: 'Amra Hodžić',
    assignedTaskerName: 'Selma Karić',
  };
  const fromSelma: Review = { id: 'r1', reviewerId: 'selma', reviewerName: 'Selma Karić', rating: 5, comment: 'Lovely client' };
  const fromAmra: Review = { id: 'r2', reviewerId: 'amra', reviewerName: 'Amra Hodžić', rating: 4 };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TaskReviews);
  });

  afterEach(() => http.verify());

  async function show(reviews: Review[]): Promise<void> {
    fixture.componentRef.setInput('task', task);
    fixture.componentRef.setInput('viewerId', 'amra');
    await fixture.whenStable();
    http.expectOne('/api/tasks/t1/reviews').flush(reviews);
    await fixture.whenStable();
  }

  function text(): string {
    return fixture.nativeElement.textContent.replace(/\s+/g, ' ');
  }

  it('invites the client to review the tasker and shows what the tasker wrote', async () => {
    await show([fromSelma]);

    expect(text()).toContain('How did it go with Selma Karić?');
    expect(text()).toContain('Lovely client');
  });

  it('stops inviting once the client has posted a review', async () => {
    await show([fromSelma, fromAmra]);

    expect(text()).not.toContain('Leave a review');
    expect(text()).toContain('You');
  });

  it('adds the new review to the list after it is posted', async () => {
    await show([]);
    fixture.nativeElement.querySelector('button').click();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('app-review-dialog')).not.toBeNull();

    fixture.componentInstance.onSubmitted(fromAmra);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('app-review-dialog')).toBeNull();
    expect(text()).not.toContain('Leave a review');
  });
});
