import { Pipe, PipeTransform } from '@angular/core';
import { TranslationKey, TranslationParams, t, tOptional } from './translate';

@Pipe({ name: 't', pure: false })
export class TranslatePipe implements PipeTransform {
  transform(key: TranslationKey, params?: TranslationParams): string {
    return t(key, params);
  }
}

@Pipe({ name: 'category', pure: false })
export class CategoryPipe implements PipeTransform {
  transform(slug: string | null | undefined, fallback: string | null | undefined): string {
    return (slug ? tOptional(`categories.${slug}`) : null) ?? fallback ?? '';
  }
}
