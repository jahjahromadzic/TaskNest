import { safeReturnUrl } from './return-url';

describe('safeReturnUrl', () => {
  it('returns to a page inside the app', () => {
    expect(safeReturnUrl('/my-tasks')).toBe('/my-tasks');
  });

  it('refuses addresses that lead to another site', () => {
    expect(safeReturnUrl('https://evil.example')).toBe('/tasks');
    expect(safeReturnUrl('//evil.example')).toBe('/tasks');
  });

  it('falls back to the task list when nothing is given', () => {
    expect(safeReturnUrl(null)).toBe('/tasks');
  });
});
