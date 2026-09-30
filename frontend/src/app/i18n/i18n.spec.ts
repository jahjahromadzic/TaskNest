import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Title } from '@angular/platform-browser';
import { Router, TitleStrategy, provideRouter } from '@angular/router';
import { Component } from '@angular/core';
import { readApiError } from '../shared/api-error';
import { formatBudget, formatDate, timeAgo } from '../shared/format/format';
import { dayLabel } from '../shared/chat/chat';
import { TASK_STATUS } from '../shared/task-status/task-status';
import { bs } from './bs';
import { Dictionary, isPlural } from './dictionary';
import { en } from './en';
import { I18nService, LANG_STORAGE_KEY, preferredLang } from './i18n.service';
import { LangSwitch } from './lang-switch';
import { currentLang, pluralForm } from './lang';
import { translateNotification, translateServerMessage } from './server-messages';
import { TranslatedTitleStrategy } from './title-strategy';
import { t } from './translate';

function leaves(dictionary: Dictionary, prefix = ''): [string, string][] {
  return Object.entries(dictionary).flatMap(([key, value]): [string, string][] => {
    const path = prefix ? `${prefix}.${key}` : key;
    if (typeof value === 'string') return [[path, value]];
    if (isPlural(value)) return Object.entries(value).map(([form, text]): [string, string] => [`${path}.${form}`, text ?? '']);
    return leaves(value, path);
  });
}

function params(text: string): string[] {
  return [...text.matchAll(/\{(\w+)\}/g)].map((match) => match[1]).sort();
}

describe('Translations', () => {
  afterEach(() => currentLang.set('en'));

  it('gives every Bosnian text the same placeholders as the English one', () => {
    const english = new Map(leaves(en));
    for (const [path, text] of leaves(bs)) {
      const plural = /\.(one|few|other)$/.test(path) && !english.has(path.replace(/\.(one|few|other)$/, ''));
      const source = plural ? english.get(path.replace(/\.(one|few)$/, '.other')) : english.get(path);
      expect(text.trim(), path).not.toBe('');
      if (plural) {
        expect(params(source ?? ''), path).toEqual(expect.arrayContaining(params(text)));
      } else {
        expect(params(text), path).toEqual(params(source ?? ''));
      }
    }
  });

  it('uses the Bosnian plural rules', () => {
    expect([1, 2, 4, 5, 11, 12, 21, 22, 25, 101].map((count) => pluralForm(count, 'bs'))).toEqual([
      'one', 'few', 'few', 'other', 'other', 'other', 'one', 'few', 'other', 'one',
    ]);
    currentLang.set('bs');
    expect(t('common.jobsDone', { count: 1 })).toBe('1 završen posao');
    expect(t('common.jobsDone', { count: 3 })).toBe('3 završena posla');
    expect(t('common.jobsDone', { count: 7 })).toBe('7 završenih poslova');
    expect(t('time.hoursAgo', { count: 21 })).toBe('prije 21 sat');
  });

  it('fills in parameters and leaves unknown ones visible', () => {
    expect(t('auth.welcomeBack', { name: 'Amra' })).toBe('Welcome back, Amra!');
    expect(t('auth.welcomeBack')).toBe('Welcome back, {name}!');
  });

  it('switches labels, dates and prices together with the language', () => {
    const now = new Date(2026, 8, 27, 12, 0);
    expect(TASK_STATUS.PUBLISHED.label).toBe('Open for offers');
    expect(timeAgo('2026-09-27T09:00:00', now)).toBe('3 hours ago');

    currentLang.set('bs');

    expect(TASK_STATUS.PUBLISHED.label).toBe('Prima ponude');
    expect(timeAgo('2026-09-27T09:00:00', now)).toBe('prije 3 sata');
    expect(timeAgo('2026-09-20T12:00:00', now)).toBe('prije 7 dana');
    expect(formatBudget(null)).toBe('Budžet po dogovoru');
    expect(formatBudget(1250.5)).toBe('1.250,5 KM');
    expect(formatDate('2026-09-26T10:00:00')).toBe('26. sep 2026.');
    expect(dayLabel('2026-09-22T10:00:00', now)).toBe('uto, 22. sep');
  });

  it('translates known server messages and keeps unknown ones as they are', () => {
    expect(translateServerMessage('Invalid email or password')).toBe('Invalid email or password');

    currentLang.set('bs');

    expect(translateServerMessage('Invalid email or password')).toBe('Pogrešan email ili lozinka');
    expect(translateServerMessage('Task not found: 7f1c')).toBe('Traženi podatak nije pronađen');
    expect(translateServerMessage('size must be between 8 and 100')).toBe('dužina mora biti između 8 i 100 znakova');
    expect(translateServerMessage('Something new from the server')).toBe('Something new from the server');
  });

  it('translates field errors from the server as well', () => {
    currentLang.set('bs');
    const error = new HttpErrorResponse({
      status: 400,
      error: { detail: 'password: size must be between 8 and 100; email: must be a well-formed email address' },
    });

    expect(readApiError(error)).toEqual({
      message: 'Ispravi označena polja.',
      fieldErrors: {
        password: 'Dužina mora biti između 8 i 100 znakova',
        email: 'Mora biti ispravna email adresa',
      },
    });
  });

  it('rewrites notification texts from the server, keeping names and titles', () => {
    currentLang.set('bs');

    expect(translateNotification('Tarik Hasanović offered 55.5 KM for: Leaking tap')).toBe('Tarik Hasanović nudi 55.5 KM za: Leaking tap');
    expect(translateNotification('Your task was removed by a moderator: Old sofa. Reason: Spam or advertising')).toBe(
      'Moderator je uklonio tvoj oglas: Old sofa. Razlog: Spam or advertising',
    );
    expect(translateNotification('Something else')).toBe('Something else');
  });
});

describe('Choosing the language', () => {
  afterEach(() => {
    currentLang.set('en');
    localStorage.removeItem(LANG_STORAGE_KEY);
  });

  it('prefers the saved choice, then a local browser language, then English', () => {
    expect(preferredLang('en', ['bs-BA'])).toBe('en');
    expect(preferredLang(null, ['hr-HR', 'en'])).toBe('bs');
    expect(preferredLang(null, ['sr-Latn-RS'])).toBe('bs');
    expect(preferredLang('fr', ['de-DE'])).toBe('en');
  });

  it('remembers the choice and marks the page language', () => {
    const service = TestBed.inject(I18nService);

    service.use('bs');

    expect(service.lang()).toBe('bs');
    expect(localStorage.getItem(LANG_STORAGE_KEY)).toBe('bs');
    expect(document.documentElement.lang).toBe('bs');
  });

  it('switches from the header control and updates the text right away', async () => {
    const fixture = TestBed.createComponent(LangSwitch);
    await fixture.whenStable();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    expect(fixture.nativeElement.querySelectorAll('button').length).toBe(1);
    expect(button.textContent?.trim()).toBe('EN');
    expect(button.getAttribute('aria-label')).toBe('Switch language to Bosanski');

    button.click();
    await fixture.whenStable();

    expect(currentLang()).toBe('bs');
    expect(button.textContent?.trim()).toBe('BS');
    expect(button.getAttribute('aria-label')).toBe('Promijeni jezik na English');

    button.click();
    await fixture.whenStable();

    expect(currentLang()).toBe('en');
    expect(button.textContent?.trim()).toBe('EN');
  });
});

@Component({ template: '' })
class Blank {}

describe('Page titles', () => {
  afterEach(() => currentLang.set('en'));

  it('translates the route title and follows the language', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'tasks', title: 'titles.browse', component: Blank },
          { path: 'custom', title: 'titles.task', component: Blank },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TitleStrategy, useExisting: TranslatedTitleStrategy },
      ],
    });
    const title = TestBed.inject(Title);
    await TestBed.inject(Router).navigateByUrl('/tasks');
    expect(title.getTitle()).toBe('Browse tasks · TaskNest');

    currentLang.set('bs');
    TestBed.tick();
    expect(title.getTitle()).toBe('Pregled oglasa · TaskNest');

    await TestBed.inject(Router).navigateByUrl('/custom');
    title.setTitle('Fix the tap · TaskNest');
    currentLang.set('en');
    TestBed.tick();
    expect(title.getTitle()).toBe('Fix the tap · TaskNest');
  });

  it('keeps a page title set by the page when only the query changes', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'tasks', title: 'titles.browse', component: Blank },
          { path: 'users/:id', title: 'titles.userProfile', component: Blank },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TitleStrategy, useExisting: TranslatedTitleStrategy },
      ],
    });
    const title = TestBed.inject(Title);
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/users/u1');
    title.setTitle('Adnan Delić · TaskNest');
    await router.navigateByUrl('/users/u1?as=client');
    expect(title.getTitle()).toBe('Adnan Delić · TaskNest');

    await router.navigateByUrl('/users/u2');
    expect(title.getTitle()).toBe('Profile · TaskNest');

    await router.navigateByUrl('/tasks');
    expect(title.getTitle()).toBe('Browse tasks · TaskNest');
  });
});
