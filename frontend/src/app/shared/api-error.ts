import { HttpErrorResponse } from '@angular/common/http';
import { t } from '../i18n/translate';
import { translateServerMessage } from '../i18n/server-messages';

export interface ApiError {
  message: string;
  fieldErrors: Record<string, string>;
}

const FIELD_ERROR = /^(\w+): (.+)$/;

export function readApiError(error: unknown): ApiError {
  if (!(error instanceof HttpErrorResponse)) {
    return { message: t('errors.generic'), fieldErrors: {} };
  }
  if (error.status === 0) {
    return { message: t('errors.offline'), fieldErrors: {} };
  }

  const detail: string | undefined = error.error?.detail;
  if (!detail) {
    return { message: t('errors.generic'), fieldErrors: {} };
  }

  const fieldErrors: Record<string, string> = {};
  if (error.status === 400) {
    for (const part of detail.split('; ')) {
      const match = FIELD_ERROR.exec(part);
      if (!match) {
        return { message: translateServerMessage(detail), fieldErrors: {} };
      }
      fieldErrors[match[1]] ??= capitalize(translateServerMessage(match[2]));
    }
    return { message: t('errors.fixFields'), fieldErrors };
  }

  return { message: translateServerMessage(detail), fieldErrors };
}

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}
