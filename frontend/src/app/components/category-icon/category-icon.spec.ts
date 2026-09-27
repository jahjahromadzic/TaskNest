import { TestBed } from '@angular/core/testing';
import { CategoryIcon } from './category-icon';

describe('CategoryIcon', () => {
  const seededNames = [
    'Vodoinstalacije',
    'Elektroinstalacije',
    'Selidbe',
    'Montaza namjestaja',
    'Ciscenje',
    'Molerski radovi',
    'Keramicarski radovi',
    'Stolarski radovi',
    'Klima uredjaji',
    'Grijanje',
    'Popravka kucanskih aparata',
    'Bravarski radovi',
    'Vrtlarstvo',
    'Racunarska pomoc',
  ];

  function keyFor(name: string): string {
    const icon = TestBed.createComponent(CategoryIcon);
    icon.componentRef.setInput('name', name);
    return icon.componentInstance.key;
  }

  it('gives every seeded category its own icon', () => {
    const keys = seededNames.map(keyFor);

    expect(keys).not.toContain('other');
    expect(new Set(keys).size).toBe(seededNames.length);
  });

  it('falls back to a tag for a category it does not know', () => {
    expect(keyFor('Nesto novo')).toBe('other');
  });
});
