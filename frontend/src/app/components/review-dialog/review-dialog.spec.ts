import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Review } from '../../api/models';
import { ReviewDialog } from './review-dialog';

describe('Review dialog', () => {
  let fixture: ComponentFixture<ReviewDialog>;
  let http: HttpTestingController;
  let posted: Review | null;
  let dismissed: boolean;

  beforeEach(async () => {
    posted = null;
    dismissed = false;
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ReviewDialog);
    fixture.componentRef.setInput('taskId', 't1');
    fixture.componentRef.setInput('taskTitle', 'Paint the kitchen');
    fixture.componentRef.setInput('revieweeName', 'Selma Karić');
    fixture.componentInstance.submitted.subscribe((review) => (posted = review));
    fixture.componentInstance.dismissed.subscribe(() => (dismissed = true));
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function stars(): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('[role=radio]'));
  }

  async function post(): Promise<void> {
    fixture.nativeElement.querySelector('button[type=submit]').click();
    await fixture.whenStable();
  }

  it('asks for a rating before posting anything', async () => {
    await post();

    expect(fixture.nativeElement.textContent).toContain('Choose from one to five stars.');
    http.expectNone('/api/tasks/t1/reviews');
  });

  it('posts the chosen stars with the comment', async () => {
    stars()[3].click();
    const comment = fixture.nativeElement.querySelector('textarea');
    comment.value = '  Clean and quick.  ';
    comment.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Good');

    await post();

    const request = http.expectOne({ method: 'POST', url: '/api/tasks/t1/reviews' });
    expect(request.request.body).toEqual({ rating: 4, comment: 'Clean and quick.' });
    request.flush({ id: 'r1', rating: 4 });
    expect(posted).toEqual({ id: 'r1', rating: 4 });
  });

  it('lets the stars be chosen with the arrow keys', async () => {
    stars()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    stars()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    await fixture.whenStable();

    expect(fixture.componentInstance.rating()).toBe(2);
    expect(stars()[1].getAttribute('aria-checked')).toBe('true');
  });

  it('shows the server message when the review is refused', async () => {
    stars()[4].click();
    await post();

    http
      .expectOne('/api/tasks/t1/reviews')
      .flush({ detail: 'You have already reviewed this task' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('You have already reviewed this task');
    expect(posted).toBeNull();
  });

  it('closes on Escape', () => {
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(dismissed).toBe(true);
  });
});
