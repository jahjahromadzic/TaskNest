import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { Select, SelectOption } from './select';

@Component({
  imports: [FormsModule, Select],
  template: `
    <app-select inputId="city" [options]="options" [(ngModel)]="city" />
    <p id="outside">outside</p>
  `,
})
class Host {
  options: SelectOption[] = [
    { value: 'sa', label: 'Sarajevo' },
    { value: 'tz', label: 'Tuzla' },
    { value: 'mo', label: 'Mostar' },
  ];
  city = 'tz';
}

describe('Select', () => {
  let fixture: ComponentFixture<Host>;

  beforeEach(async () => {
    fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
  });

  function button(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('#city');
  }

  function options(): HTMLElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('[role=option]'));
  }

  async function press(key: string): Promise<void> {
    button().dispatchEvent(new KeyboardEvent('keydown', { key }));
    await fixture.whenStable();
  }

  it('shows the label of the current value', () => {
    expect(button().textContent).toContain('Tuzla');
    expect(options()).toHaveLength(0);
  });

  it('opens a list with the current value marked and updates the model on click', async () => {
    button().click();
    await fixture.whenStable();

    expect(options().map((option) => option.textContent?.trim())).toEqual(['Sarajevo', 'Tuzla', 'Mostar']);
    expect(options()[1].getAttribute('aria-selected')).toBe('true');

    options()[2].click();
    await fixture.whenStable();

    expect(fixture.componentInstance.city).toBe('mo');
    expect(button().textContent).toContain('Mostar');
    expect(options()).toHaveLength(0);
  });

  it('can be used with the keyboard alone', async () => {
    await press('ArrowDown');
    expect(options()).toHaveLength(3);

    await press('ArrowDown');
    await press('Enter');

    expect(fixture.componentInstance.city).toBe('mo');
  });

  it('wraps around from the last option to the first', async () => {
    await press('ArrowDown');
    await press('ArrowDown');
    await press('ArrowDown');
    await press('Enter');

    expect(fixture.componentInstance.city).toBe('sa');
  });

  it('closes without a change on Escape or a click elsewhere', async () => {
    await press('ArrowDown');
    await press('ArrowDown');
    await press('Escape');
    expect(options()).toHaveLength(0);

    button().click();
    await fixture.whenStable();
    fixture.nativeElement.querySelector('#outside').click();
    await fixture.whenStable();

    expect(options()).toHaveLength(0);
    expect(fixture.componentInstance.city).toBe('tz');
  });
});

@Component({
  imports: [FormsModule, Select],
  template: `<app-select inputId="place" [options]="options" [searchable]="true" noMatchesText="Nothing" [(ngModel)]="place" />`,
})
class SearchHost {
  options: SelectOption[] = [
    { value: 'il', label: 'Ilidža', group: 'Sarajevo Canton' },
    { value: 'ce', label: 'Centar Sarajevo', group: 'Sarajevo Canton' },
    { value: 'tz', label: 'Tuzla', group: 'Tuzla Canton' },
  ];
  place = '';
}

describe('Select with search', () => {
  let fixture: ComponentFixture<SearchHost>;

  beforeEach(async () => {
    fixture = TestBed.createComponent(SearchHost);
    await fixture.whenStable();
    fixture.nativeElement.querySelector('#place').click();
    await fixture.whenStable();
  });

  function search(): HTMLInputElement {
    return fixture.nativeElement.querySelector('input[type=search]');
  }

  function labels(selector: string): string[] {
    return Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll(selector)).map((item) => item.textContent!.trim());
  }

  async function type(text: string): Promise<void> {
    search().value = text;
    search().dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  it('shows a heading for each group', () => {
    expect(labels('[role=option]')).toEqual(['Ilidža', 'Centar Sarajevo', 'Tuzla']);
    expect(labels('li[role=presentation]')).toEqual(['Sarajevo Canton', 'Tuzla Canton']);
  });

  it('filters by name without caring about diacritics and picks with Enter', async () => {
    await type('ilidza');
    expect(labels('[role=option]')).toEqual(['Ilidža']);

    search().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await fixture.whenStable();

    expect(fixture.componentInstance.place).toBe('il');
  });

  it('matches the group name and says when nothing matches', async () => {
    await type('tuzla canton');
    expect(labels('[role=option]')).toEqual(['Tuzla']);

    await type('zzz');
    expect(labels('[role=option]')).toEqual([]);
    expect(labels('li[role=presentation]')).toEqual(['Nothing']);
  });
});
