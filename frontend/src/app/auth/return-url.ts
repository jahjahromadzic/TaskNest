export function safeReturnUrl(value: string | null): string {
  if (value && value.startsWith('/') && !value.startsWith('//')) {
    return value;
  }
  return '/tasks';
}
