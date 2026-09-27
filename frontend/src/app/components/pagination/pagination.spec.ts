import { visiblePages } from './pagination';

describe('visiblePages', () => {
  it('shows every page when there are only a few', () => {
    expect(visiblePages(0, 3)).toEqual([0, 1, 2]);
  });

  it('keeps the first, last and neighbouring pages and hides the rest behind a gap', () => {
    expect(visiblePages(5, 10)).toEqual([0, null, 4, 5, 6, null, 9]);
  });

  it('does not show a gap next to the first page', () => {
    expect(visiblePages(1, 10)).toEqual([0, 1, 2, null, 9]);
  });
});
