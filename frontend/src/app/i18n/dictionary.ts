export interface Plural {
  one: string;
  few?: string;
  other: string;
}

export interface Dictionary {
  [key: string]: string | Plural | Dictionary;
}

export type Paths<T> = {
  [K in keyof T & string]: T[K] extends string | Plural ? K : `${K}.${Paths<T[K]>}`;
}[keyof T & string];

export type SameShape<T> = {
  [K in keyof T]: T[K] extends string ? string : T[K] extends Plural ? Required<Plural> : SameShape<T[K]>;
};

export function isPlural(value: unknown): value is Plural {
  return typeof value === 'object' && value !== null && 'one' in value && 'other' in value;
}

export function lookup(dictionary: Dictionary, path: string): string | Plural | null {
  let node: string | Plural | Dictionary | undefined = dictionary;
  for (const part of path.split('.')) {
    if (typeof node !== 'object' || node === null || isPlural(node)) {
      return null;
    }
    node = (node as Dictionary)[part];
  }
  return typeof node === 'string' || isPlural(node) ? node : null;
}
