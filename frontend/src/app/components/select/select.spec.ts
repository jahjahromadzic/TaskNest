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
