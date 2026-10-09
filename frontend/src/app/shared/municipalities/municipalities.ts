import { Municipality } from '../../api/models';
import { SelectOption } from '../../components/select/select';
import { tOptional } from '../../i18n/translate';

export const REGION_ORDER = [
  'Una-Sana Canton',
  'Posavina Canton',
  'Tuzla Canton',
  'Zenica-Doboj Canton',
  'Bosnian-Podrinje Canton Goražde',
  'Central Bosnia Canton',
  'Herzegovina-Neretva Canton',
  'West Herzegovina Canton',
  'Sarajevo Canton',
  'Canton 10',
  'Republika Srpska',
  'Brčko District',
];

export function regionLabel(region: string | null | undefined): string {
  return (region ? tOptional(`regions.${region}`) : null) ?? region ?? '';
}

function regionRank(region: string | null | undefined): number {
  const index = REGION_ORDER.indexOf(region ?? '');
  return index < 0 ? REGION_ORDER.length : index;
}

export function sortByRegion(municipalities: Municipality[]): Municipality[] {
  return [...municipalities].sort(
    (a, b) => regionRank(a.region) - regionRank(b.region) || (a.name ?? '').localeCompare(b.name ?? '', 'bs'),
  );
}

export function regionsOf(municipalities: Municipality[]): string[] {
  return [...new Set(sortByRegion(municipalities).map((municipality) => municipality.region ?? ''))];
}

export function regionOf(municipalities: Municipality[], municipalityId: string | null | undefined): string | null {
  return municipalities.find((municipality) => municipality.id === municipalityId)?.region ?? null;
}

export function regionOptions(municipalities: Municipality[]): SelectOption[] {
  return regionsOf(municipalities).map((region) => ({
    value: region,
    get label() {
      return regionLabel(region);
    },
  }));
}

export function municipalityOptions(municipalities: Municipality[], region: string | null): SelectOption[] {
  if (!region) {
    return [];
  }
  return sortByRegion(municipalities)
    .filter((municipality) => (municipality.region ?? '') === region)
    .map((municipality) => ({ value: municipality.id ?? '', label: municipality.name ?? '' }));
}

export const SEARCHABLE_FROM = 12;
