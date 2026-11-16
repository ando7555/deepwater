// Kept for existing imports; the client lives in app/http.js.
export { API, gql, request, restClient, ApiError } from './app/http.js';

export function formatDate(value) {
  if (!value) return 'tomorrow';
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(value));
}
