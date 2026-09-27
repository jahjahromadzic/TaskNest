import { TestBed } from '@angular/core/testing';
import { Tag } from 'lucide';
import { CategoryIcon } from './category-icon';

describe('CategoryIcon', () => {
  const seededSlugs = [
    'plumbing',
    'electrical',
    'moving',
    'furniture-assembly',
    'cleaning',
    'painting',
    'tiling',
    'carpentry',
    'air-conditioning',
    'heating',
    'appliance-repair',
    'locksmith',
    'gardening',
    'computer-help',
  ];

  function iconFor(slug: string) {
    const icon = TestBed.createComponent(CategoryIcon);
    icon.componentRef.setInput('slug', slug);
    return icon.componentInstance.icon;
  }

  it('gives every seeded category its own icon', () => {
    const icons = seededSlugs.map(iconFor);

    expect(icons).not.toContain(Tag);
    expect(new Set(icons).size).toBe(seededSlugs.length);
  });

  it('falls back to a tag for a category it does not know', () => {
    expect(iconFor('something-new')).toBe(Tag);
  });
});
