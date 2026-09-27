import { daysLeft, formatBudget, timeAgo } from './format';

describe('format', () => {
  const now = new Date('2026-09-27T12:00:00');

  it('shows the budget in KM or says it is open', () => {
    expect(formatBudget(120)).toBe('120 KM');
    expect(formatBudget(1250.5)).toBe('1,250.5 KM');
    expect(formatBudget(null)).toBe('Open budget');
  });

  it('describes how long ago a task was posted', () => {
    expect(timeAgo('2026-09-27T11:59:40', now)).toBe('just now');
    expect(timeAgo('2026-09-27T11:15:00', now)).toBe('45 min ago');
    expect(timeAgo('2026-09-27T09:00:00', now)).toBe('3 hours ago');
    expect(timeAgo('2026-09-26T10:00:00', now)).toBe('yesterday');
    expect(timeAgo('2026-09-20T12:00:00', now)).toBe('7 days ago');
  });

  it('counts calendar days left and never goes below zero', () => {
    expect(daysLeft('2026-10-07T12:00:00', now)).toBe(10);
    expect(daysLeft('2026-09-28T08:00:00', now)).toBe(1);
    expect(daysLeft('2026-09-27T18:00:00', now)).toBe(0);
    expect(daysLeft('2026-09-20T12:00:00', now)).toBe(0);
  });

  it('is not thrown off by the clock change at the end of October', () => {
    expect(daysLeft('2026-10-27T19:05:00', new Date('2026-09-27T19:05:00'))).toBe(30);
  });
});
