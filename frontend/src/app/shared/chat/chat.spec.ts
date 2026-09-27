import { avatarTone, buildThread, dayLabel, mergeMessages, preview } from './chat';

describe('Chat helpers', () => {
  const now = new Date(2026, 8, 27, 18, 0);

  it('names the day of a message', () => {
    expect(dayLabel('2026-09-27T08:00:00', now)).toBe('Today');
    expect(dayLabel('2026-09-26T23:59:00', now)).toBe('Yesterday');
    expect(dayLabel('2026-09-22T10:00:00', now)).toBe('Tue 22 Sept');
  });

  it('adds a day separator and starts a new bubble group when the sender changes or time passes', () => {
    const thread = buildThread(
      [
        { id: 'm1', senderId: 'me', createdAt: '2026-09-26T10:00:00' },
        { id: 'm2', senderId: 'me', createdAt: '2026-09-26T10:01:00' },
        { id: 'm3', senderId: 'emir', createdAt: '2026-09-26T10:02:00' },
        { id: 'm4', senderId: 'emir', createdAt: '2026-09-26T10:30:00' },
        { id: 'm5', senderId: 'emir', createdAt: '2026-09-27T09:00:00' },
      ],
      'me',
      now,
    );

    expect(
      thread.map((item) => (item.kind === 'day' ? item.label : `${item.key}:${item.mine ? 'mine' : 'theirs'}:${item.startsGroup}`)),
    ).toEqual([
      'Yesterday',
      'm1:mine:true',
      'm2:mine:false',
      'm3:theirs:true',
      'm4:theirs:true',
      'Today',
      'm5:theirs:true',
    ]);
  });

  it('puts older messages in front and skips ones already shown', () => {
    const merged = mergeMessages([{ id: 'm3' }, { id: 'm4' }], [{ id: 'm1' }, { id: 'm2' }, { id: 'm3' }]);

    expect(merged.map((message) => message.id)).toEqual(['m1', 'm2', 'm3', 'm4']);
  });

  it('marks the preview of your own last message', () => {
    expect(preview('See you at 10', true)).toBe('You: See you at 10');
    expect(preview('See you at 10', false)).toBe('See you at 10');
    expect(preview(undefined, true)).toBe('');
  });

  it('gives the same person the same avatar colour every time', () => {
    expect(avatarTone('Emir Kovačević')).toBe(avatarTone('Emir Kovačević'));
    expect(avatarTone('Emir Kovačević')).toMatch(/^bg-/);
  });
});
