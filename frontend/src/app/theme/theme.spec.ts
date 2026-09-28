import { TestBed } from '@angular/core/testing';
import { currentLang } from '../i18n/lang';
import { THEME_STORAGE_KEY, ThemeService, preferredTheme } from './theme.service';
import { ThemeToggle } from './theme-toggle';

type Listener = (event: { matches: boolean }) => void;

function fakeSystemTheme(dark: boolean): { change: (dark: boolean) => void } {
  const listeners: Listener[] = [];
  window.matchMedia = vi.fn((query: string) => ({
    matches: query.includes('dark') ? dark : false,
    addEventListener: (_: string, listener: Listener) => listeners.push(listener),
  })) as unknown as typeof window.matchMedia;
  return { change: (next) => listeners.forEach((listener) => listener({ matches: next })) };
}

describe('Theme', () => {
  const originalMatchMedia = window.matchMedia;

  afterEach(() => {
    window.matchMedia = originalMatchMedia;
    localStorage.removeItem(THEME_STORAGE_KEY);
    delete document.documentElement.dataset['theme'];
    currentLang.set('en');
  });

  it('prefers the saved choice and otherwise follows the system', () => {
    expect(preferredTheme('dark', false)).toBe('dark');
    expect(preferredTheme('light', true)).toBe('light');
    expect(preferredTheme(null, true)).toBe('dark');
    expect(preferredTheme('purple', false)).toBe('light');
  });

  it('starts from the saved choice and marks the page with it', () => {
    fakeSystemTheme(false);
    localStorage.setItem(THEME_STORAGE_KEY, 'dark');
    const service = TestBed.inject(ThemeService);

    service.start();

    expect(service.theme()).toBe('dark');
    expect(document.documentElement.dataset['theme']).toBe('dark');
  });

  it('follows the system until the user picks a theme, then remembers the pick', () => {
    const system = fakeSystemTheme(false);
    const service = TestBed.inject(ThemeService);
    service.start();
    expect(service.theme()).toBe('light');

    system.change(true);
    expect(service.theme()).toBe('dark');

    service.toggle();
    expect(service.theme()).toBe('light');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');

    system.change(true);
    expect(service.theme()).toBe('light');
    expect(document.documentElement.dataset['theme']).toBe('light');
  });

  it('switches with one header button that shows the theme it leads to', async () => {
    fakeSystemTheme(false);
    TestBed.inject(ThemeService).start();
    const fixture = TestBed.createComponent(ThemeToggle);
    await fixture.whenStable();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    expect(fixture.nativeElement.querySelectorAll('button').length).toBe(1);
    expect(button.getAttribute('aria-label')).toBe('Switch to dark theme');

    button.click();
    await fixture.whenStable();

    expect(document.documentElement.dataset['theme']).toBe('dark');
    expect(button.getAttribute('aria-label')).toBe('Switch to light theme');

    currentLang.set('bs');
    await fixture.whenStable();
    expect(button.getAttribute('aria-label')).toBe('Uključi svijetlu temu');
  });
});
