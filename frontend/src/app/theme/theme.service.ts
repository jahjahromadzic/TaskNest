import { ApplicationRef, Injectable, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

export const THEME_STORAGE_KEY = 'tasknest-theme';

const REVEAL_DURATION = 550;

export function preferredTheme(stored: string | null, prefersDark: boolean): Theme {
  if (stored === 'light' || stored === 'dark') {
    return stored;
  }
  return prefersDark ? 'dark' : 'light';
}

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly current = signal<Theme>('light');
  readonly theme = this.current.asReadonly();
  private chosen = false;

  constructor(private appRef: ApplicationRef) {}

  start(): void {
    const stored = readStored();
    this.chosen = stored === 'light' || stored === 'dark';
    const systemDark = media('(prefers-color-scheme: dark)');
    this.apply(preferredTheme(stored, systemDark?.matches ?? false));
    systemDark?.addEventListener('change', (event) => {
      if (!this.chosen) {
        this.apply(event.matches ? 'dark' : 'light');
      }
    });
  }

  toggle(origin?: { x: number; y: number }): void {
    const next: Theme = this.current() === 'dark' ? 'light' : 'dark';
    this.chosen = true;
    this.switchTo(next, origin);
    try {
      localStorage.setItem(THEME_STORAGE_KEY, next);
    } catch {
      return;
    }
  }

  private switchTo(theme: Theme, origin?: { x: number; y: number }): void {
    const reducedMotion = media('(prefers-reduced-motion: reduce)')?.matches ?? false;
    if (!origin || reducedMotion || typeof document.startViewTransition !== 'function') {
      this.apply(theme);
      return;
    }
    const root = document.documentElement;
    root.classList.add('theme-switching');
    const transition = document.startViewTransition(() => {
      this.apply(theme);
      this.appRef.tick();
    });
    transition.ready
      .then(() => {
        const radius = Math.hypot(
          Math.max(origin.x, window.innerWidth - origin.x),
          Math.max(origin.y, window.innerHeight - origin.y),
        );
        root.animate(
          {
            clipPath: [
              `circle(0px at ${origin.x}px ${origin.y}px)`,
              `circle(${radius}px at ${origin.x}px ${origin.y}px)`,
            ],
          },
          { duration: REVEAL_DURATION, easing: 'cubic-bezier(0.16, 1, 0.3, 1)', pseudoElement: '::view-transition-new(root)' },
        );
      })
      .catch(() => undefined);
    transition.finished.finally(() => root.classList.remove('theme-switching'));
  }

  private apply(theme: Theme): void {
    this.current.set(theme);
    document.documentElement.dataset['theme'] = theme;
  }
}

function media(query: string): MediaQueryList | null {
  return typeof window.matchMedia === 'function' ? window.matchMedia(query) : null;
}

function readStored(): string | null {
  try {
    return localStorage.getItem(THEME_STORAGE_KEY);
  } catch {
    return null;
  }
}
