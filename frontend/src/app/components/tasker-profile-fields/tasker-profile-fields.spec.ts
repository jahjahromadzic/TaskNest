import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Municipality } from '../../api/models';
import { TaskerProfileFields } from './tasker-profile-fields';

@Component({
  imports: [TaskerProfileFields],
  template: `<app-tasker-profile-fields [municipalities]="municipalities" [(municipalityIds)]="ids" />`,
})
class Host {
  municipalities: Municipality[] = [
    { id: 'tz', name: 'Tuzla', region: 'Tuzla Canton' },
    { id: 'il', name: 'Ilidža', region: 'Sarajevo Canton' },
    { id: 'ce', name: 'Centar Sarajevo', region: 'Sarajevo Canton' },
  ];
  ids: string[] = [];
}

describe('TaskerProfileFields', () => {
  let fixture: ComponentFixture<Host>;

  async function render(ids: string[]): Promise<void> {
    fixture = TestBed.createComponent(Host);
    fixture.componentInstance.ids = ids;
    await fixture.whenStable();
  }

  function chips(): string[] {
    return Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('[role=checkbox]')).map((chip) =>
      chip.textContent!.trim(),
    );
  }

  function button(text: string): HTMLButtonElement {
    return Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button')).find((item) =>
      item.textContent!.includes(text),
    )!;
  }

  it('starts in Sarajevo Canton and selects the whole region at once', async () => {
    await render([]);
    expect(chips()).toEqual(['Centar Sarajevo', 'Ilidža']);

    button('Select all in Sarajevo Canton').click();
    await fixture.whenStable();

    expect(fixture.componentInstance.ids).toEqual(['ce', 'il']);
    expect(button('Clear Sarajevo Canton')).toBeTruthy();
  });

  it('opens the region of the saved municipalities and lists choices from other regions', async () => {
    await render(['ce', 'tz']);
    expect(chips()).toEqual(['Tuzla']);
    expect(fixture.nativeElement.textContent).toContain('Also selected');

    (fixture.nativeElement.querySelector('[aria-label="Remove Centar Sarajevo"]') as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(fixture.componentInstance.ids).toEqual(['tz']);
    expect(fixture.nativeElement.textContent).not.toContain('Also selected');
  });
});
