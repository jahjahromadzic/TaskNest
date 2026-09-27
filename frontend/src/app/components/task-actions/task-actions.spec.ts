import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TaskDetail } from '../../api/models';
import { ConfirmOptions, ConfirmService } from '../../shared/confirm/confirm.service';
import { TaskActions } from './task-actions';

describe('Task actions', () => {
  let fixture: ComponentFixture<TaskActions>;
  let http: HttpTestingController;
  let asked: ConfirmOptions[];
  let answer: boolean;
  let changed: number;

  const base: TaskDetail = { id: 't1', assignedTaskerName: 'Emir Kovačević' };

  beforeEach(async () => {
    asked = [];
    answer = true;
    changed = 0;
    TestBed.configureTestingModule({
      providers: [
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
    fixture = TestBed.createComponent(TaskActions);
    fixture.componentInstance.changed.subscribe(() => changed++);
  });

  afterEach(() => http.verify());

  async function show(task: Partial<TaskDetail>): Promise<void> {
    fixture.componentRef.setInput('task', { ...base, ...task });
    await fixture.whenStable();
  }

  function buttons(): string[] {
    return Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button')).map((button) =>
      button.textContent!.replace(/\s+/g, ' ').trim(),
    );
  }

  async function press(label: string): Promise<void> {
    Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button'))
      .find((button) => button.textContent!.includes(label))!
      .click();
    await fixture.whenStable();
  }

  it('offers only the actions that the status allows', async () => {
    await show({ status: 'DRAFT' });
    expect(buttons()).toEqual(['Publish task', 'Discard draft']);

    await show({ status: 'PUBLISHED' });
    expect(buttons()).toEqual(['Cancel task']);

    await show({ status: 'ASSIGNED' });
    expect(buttons()).toEqual(["Tasker can't make it? Reopen", 'Cancel task']);

    await show({ status: 'COMPLETED', completedAt: new Date().toISOString() });
    expect(buttons()).toEqual(['Confirm and close']);

    await show({ status: 'CLOSED' });
    expect(buttons()).toEqual([]);
  });

  it('asks in red before discarding a draft and then cancels it', async () => {
    await show({ status: 'DRAFT' });

    await press('Discard draft');

    expect(asked[0].tone).toBe('danger');
    expect(asked[0].title).toBe('Discard this draft?');
    http.expectOne({ method: 'POST', url: '/api/tasks/t1/cancel' }).flush({});
    await fixture.whenStable();
    expect(changed).toBe(1);
  });

  it('keeps the task when the client does not confirm the cancellation', async () => {
    answer = false;
    await show({ status: 'PUBLISHED' });

    await press('Cancel task');

    http.expectNone('/api/tasks/t1/cancel');
    expect(changed).toBe(0);
  });

  it('names the hired tasker when reopening the task', async () => {
    await show({ status: 'ASSIGNED' });

    await press('Reopen');

    expect(asked[0].message).toContain('Emir Kovačević is released');
    http.expectOne({ method: 'POST', url: '/api/tasks/t1/reopen' }).flush({});
    await fixture.whenStable();
    expect(changed).toBe(1);
  });

  it('closes a finished job and says when it would close by itself', async () => {
    const twoDaysAgo = new Date(Date.now() - 2 * 86_400_000).toISOString();
    await show({ status: 'COMPLETED', completedAt: twoDaysAgo });

    expect(fixture.nativeElement.textContent).toContain('closes by itself in 5 days');
    await press('Confirm and close');

    http.expectOne({ method: 'POST', url: '/api/tasks/t1/close' }).flush({});
    await fixture.whenStable();
    expect(changed).toBe(1);
  });
});
