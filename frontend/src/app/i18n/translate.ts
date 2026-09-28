import { bs } from './bs';
import { Dictionary, Paths, isPlural, lookup } from './dictionary';
import { en } from './en';
import { currentLang, pluralForm } from './lang';

export type TranslationKey = Paths<typeof en>;
export type TranslationParams = Record<string, string | number | null | undefined>;

const DICTIONARIES: Record<string, Dictionary> = { en, bs };

function resolve(path: string, params?: TranslationParams): string | null {
  const lang = currentLang();
  const value = lookup(DICTIONARIES[lang], path) ?? lookup(en, path);
  if (value === null) {
    return null;
  }
  const text = isPlural(value) ? (value[pluralForm(Number(params?.['count'] ?? 0), lang)] ?? value.other) : value;
  return text.replace(/\{(\w+)\}/g, (match, name: string) => {
    const param = params?.[name];
    return param === null || param === undefined ? match : String(param);
  });
}

export function t(key: TranslationKey, params?: TranslationParams): string {
  return resolve(key, params) ?? key;
}

export function tOptional(path: string, params?: TranslationParams): string | null {
  return resolve(path, params);
}

export function translated<T extends object, K extends string>(
  value: T,
  fields: Record<K, TranslationKey>,
): T & Readonly<Record<K, string>> {
  const result = { ...value };
  for (const [field, key] of Object.entries(fields) as [K, TranslationKey][]) {
    Object.defineProperty(result, field, { get: () => t(key), enumerable: true });
  }
  return result as T & Readonly<Record<K, string>>;
}
