// Empty VITE_API_URL keeps API calls on the frontend origin, where Vite can proxy them.
export const API = import.meta.env.VITE_API_URL ?? '';
const GRAPHQL = `${API}/graphql`;

export async function gql(query, variables = {}, token = sessionStorage.getItem('deepwater-token')) {
  const response = await fetch(GRAPHQL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ query, variables }),
  });
  const payload = await response.json();
  if (!response.ok || payload.errors?.length) throw new Error(payload.errors?.[0]?.message || 'The request could not be completed.');
  return payload.data;
}

export function formatDate(value) {
  if (!value) return 'tomorrow';
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(value));
}
