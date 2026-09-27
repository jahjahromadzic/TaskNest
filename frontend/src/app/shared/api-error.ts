import { HttpErrorResponse } from '@angular/common/http';

export interface ApiError {
  message: string;
  fieldErrors: Record<string, string>;
}

const FIELD_ERROR = /^(\w+): (.+)$/;

export function readApiError(error: unknown): ApiError {
  if (!(error instanceof HttpErrorResponse)) {
    return { message: 'Something went wrong. Please try again.', fieldErrors: {} };
  }
  if (error.status === 0) {
    return { message: 'Cannot reach the server. Check your connection and try again.', fieldErrors: {} };
  }

  const detail: string | undefined = error.error?.detail;
  if (!detail) {
    return { message: 'Something went wrong. Please try again.', fieldErrors: {} };
  }

  const fieldErrors: Record<string, string> = {};
  if (error.status === 400) {
    for (const part of detail.split('; ')) {
      const match = FIELD_ERROR.exec(part);
      if (!match) {
        return { message: detail, fieldErrors: {} };
      }
      fieldErrors[match[1]] ??= capitalize(match[2]);
    }
    return { message: 'Please fix the highlighted fields.', fieldErrors };
  }

  return { message: detail, fieldErrors };
}

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}
