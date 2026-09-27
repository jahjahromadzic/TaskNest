import { AppNotification } from '../../api/models';
import { groupByDay, notificationKind, notificationLink } from './notification-kind';

describe('Notification kinds', () => {
  it('opens a message notification in the conversation and any other one on its task', () => {
    expect(notificationLink({ type: 'NEW_MESSAGE', relatedEntityId: 'c1' })).toBe('/messages?conversation=c1');
    expect(notificationLink({ type: 'NEW_OFFER', relatedEntityId: 't1' })).toBe('/tasks/t1');
    expect(notificationLink({ type: 'NEW_OFFER' })).toBeNull();
  });

  it('falls back to a plain look for a type the app does not know yet', () => {
    expect(notificationKind('NEW_OFFER').title).toBe('New offer');
    expect(notificationKind('SOMETHING_NEW' as AppNotification['type']).title).toBe('Notification');
  });

  it('groups notifications by calendar day, newest first', () => {
    const now = new Date(2026, 8, 27, 9, 0);
    const local = (day: number, hour: number) =>
      `2026-09-${String(day).padStart(2, '0')}T${String(hour).padStart(2, '0')}:00:00`;

    const groups = groupByDay(
      [
        { id: 'a', createdAt: local(27, 1) },
        { id: 'b', createdAt: local(26, 23) },
        { id: 'c', createdAt: local(22, 12) },
        { id: 'd', createdAt: local(10, 12) },
        { id: 'e', createdAt: local(1, 12) },
      ],
      now,
    );

    expect(groups.map((group) => [group.label, group.items.map((item) => item.id)])).toEqual([
      ['Today', ['a']],
      ['Yesterday', ['b']],
      ['This week', ['c']],
      ['Earlier', ['d', 'e']],
    ]);
  });
});
