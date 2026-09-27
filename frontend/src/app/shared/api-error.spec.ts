import { HttpErrorResponse } from '@angular/common/http';
import { readApiError } from './api-error';

describe('readApiError', () => {
  it('turns a validation error into messages per field', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: { detail: 'password: size must be between 8 and 100; email: must be a well-formed email address' },
    });

    expect(readApiError(error)).toEqual({
      message: 'Please fix the highlighted fields.',
      fieldErrors: {
        password: 'Size must be between 8 and 100',
        email: 'Must be a well-formed email address',
      },
    });
  });

  it('keeps a business rule message as the main message', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: { detail: 'An account with this email already exists' },
    });

    expect(readApiError(error)).toEqual({ message: 'An account with this email already exists', fieldErrors: {} });
  });

  it('uses the server message for other errors', () => {
    const error = new HttpErrorResponse({ status: 401, error: { detail: 'Invalid email or password' } });

    expect(readApiError(error).message).toBe('Invalid email or password');
  });

  it('explains when the server cannot be reached', () => {
    const error = new HttpErrorResponse({ status: 0 });

    expect(readApiError(error).message).toContain('Cannot reach the server');
  });
});
