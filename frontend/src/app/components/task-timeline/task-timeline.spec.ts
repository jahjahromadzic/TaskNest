import { TestBed } from '@angular/core/testing';
import { TaskDetail } from '../../api/models';
import { TaskTimeline } from './task-timeline';

describe('TaskTimeline', () => {
  function stepsFor(status: TaskDetail['status']): string[] {
    const timeline = TestBed.createComponent(TaskTimeline);
    timeline.componentRef.setInput('task', { status });
    return timeline.componentInstance.steps.map((step) => `${step.label}:${step.state}`);
  }

  it('marks earlier steps done and the current one as current', () => {
    expect(stepsFor('ASSIGNED')).toEqual([
      'Published:done',
      'Assigned:current',
      'In progress:upcoming',
      'Completed:upcoming',
      'Closed:upcoming',
    ]);
  });

  it('marks every step done once the task is closed', () => {
    expect(stepsFor('CLOSED').every((step) => step.endsWith(':done'))).toBe(true);
  });

  it('fills the progress line in proportion to the current step', () => {
    const timeline = TestBed.createComponent(TaskTimeline);
    timeline.componentRef.setInput('task', { status: 'IN_PROGRESS' });

    expect(timeline.componentInstance.progress).toBe(50);
  });
});
