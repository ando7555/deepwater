# Deepwater UI components

`frontend/src/ui/index.jsx` holds the small set of components every Deepwater screen is built from.
Each one is a thin wrapper over a CSS class that already exists in `style.css`, so a screen built from
them looks like the rest of the app in all three themes (deep, amber, light) without new CSS.

Rules that keep screens consistent:

- Colours, radii and shadows come from the tokens on `:root` in `style.css`. Never write a hex colour in a component.
- Reach for a component here before writing a new `<div className=…>`. Add a component here when the
  same pattern appears on a second screen; keep product-specific layout (lesson, cards, Harbor job) in its own file.
- Every control has a visible label (`Field`), every icon button an `aria-label`, every status change a `Notice`.

## Components

| Component | Use it for | Example |
| --- | --- | --- |
| `Panel` | The card surface. `as` picks the element. | `<Panel as="aside" className="progress">…</Panel>` |
| `SectionHeading` | Eyebrow + title + lead, with an optional action on the right. | `<SectionHeading eyebrow="PRACTICE" title="Choose a mode" action={<Button>…</Button>} />` |
| `Field` | A labelled input, textarea or select with a hint. | `<Field as="textarea" label="Explain the pattern" hint="One example." value={v} onChange={set('explanation')} />` |
| `FormGrid` | Two columns of fields; `wide` spans both. | `<FormGrid><Field … /><Field wide … /></FormGrid>` |
| `Choice` | A radio or checkbox drawn as a row. | `<Choice type="radio" name="level" checked onChange>I know a few things</Choice>` |
| `Button` | `primary` (default), `quiet`, `link`, `danger`. Defaults to `type="button"`. | `<Button variant="quiet" onClick={…}>+ Option</Button>` |
| `Pill` | A status label: `accent`, `soft`, `good`, `warn`, `muted`. | `<Pill tone="good">Published</Pill>` |
| `Segmented` | A compact exclusive choice. | `<Segmented options={[{id,label}]} value onChange label="Filter" />` |
| `Tabs` | Section tabs with an optional badge. | `<Tabs items={[{ id: 'today', label: 'Today', badge: 3 }]} value onChange label="Sections" />` |
| `Notice` | A banner: `info`, `good`, `error` (error gets `role="alert"`). | `<Notice tone="error">{error}</Notice>` |
| `EmptyState` | "Nothing here yet", with an optional action. | `<EmptyState title="No lessons yet." action={<Button>Add one</Button>}>…</EmptyState>` |
| `ProblemList` | What to fix before a form can be submitted. | `<ProblemList problems={['The title is missing.']} />` |
| `Stat`, `StatGrid` | Numbers with captions. | `<StatGrid><Stat value={12} label="cards" /></StatGrid>` |
| `useForm` | One state object for a form; `set('key')` takes an event or a value. | `const [form, set, reset] = useForm({ title: '' });` |
| `Loading` | A calm placeholder while data loads (announced, no spinner). | `<Loading label="Loading Harbor…" />` |
| `ErrorState` | Something did not load: plain words, and "Try again" when retrying can help. | `<ErrorState title="Harbor didn’t load" error={error} onRetry={load} />` |
| `ErrorBoundary` | Keeps a crash in one product from blanking the page. | `<ErrorBoundary name="Harbor" resetKey={product}>…</ErrorBoundary>` |
| `useResource`, `ResourceStatus` | Load data with loading, error and reload in one object. | `const r = useResource(() => api('/x'), []); if (!r.data) return <ResourceStatus resource={r} label="Loading…" />;` |
| `useDraft` | State that survives switching screens or reloading the tab (half-written answers). | `const [answers, setAnswers, clear] = useDraft('polish-b1-answers', {});` |
| `Menu`, `MenuSection` | A small menu behind a button (the account menu). Escape and outside clicks close it. | `<Menu label="Account" summary={<Avatar />}><MenuSection title="Appearance">…</MenuSection></Menu>` |
| `Notice` `onDismiss` | Adds a close button to a banner. | `<Notice tone="good" onDismiss={() => setNotice('')}>Saved.</Notice>` |

## Below the components

- `app/http.js`: the only way to call the backend. `request()` for REST, `gql()` for GraphQL, `restClient('/api/v1/harbor')` for one area.
  It turns a dropped connection, a timeout, Railway's HTML 502 page and an expired session into an `ApiError` with a sentence a person can act on
  (`error.kind` is offline, timeout, session, server or client; `error.retryable` says if "Try again" can help). An expired session signs the person out.
- `app/storage.js`: localStorage and sessionStorage that never throw, with every key in `KEYS`.
- `app/config.js`: products, themes and Echo languages as data. Add a language or product here, not in a screen.
- `app/theme.jsx`: the colour theme hook and switch.

## Design tokens

`style.css` starts with the tokens. Use them instead of numbers:

- Type: `--fs-2xs` 11, `--fs-xs` 12, `--fs-sm` 13, `--fs-md` 14, `--fs-base` 15, `--fs-lg` 17, `--fs-xl` 21, `--fs-2xl` 27, `--fs-display` 40.
- Radius: `--radius-xs` 6, `--radius-sm` 10, `--radius` 14, `--radius-lg` 20, `--radius-pill`.
- Space: `--space-1` 4 … `--space-7` 48. Motion: `--ease`, `--dur-fast`, `--dur`.
- Breakpoints: phones `max-width:600px`, tablets `max-width:860px` (plus one wide-layout rule at 1000px).

## Tests

`npm test` runs every `*.test.mjs` under `src/`, including `ui/ui.test.mjs`, which renders the components with `react-dom/server`.
`.jsx` files are compiled on the fly by `test/jsx-loader.mjs` with the JSX transform that ships in Vite, so no test packages are needed.

## Worked example: the teacher's review queue

`frontend/src/teacher/` shows the intended split:

- `contentKinds.js`: plain data and functions (no React) that name the kind of a lesson, covered by `contentKinds.test.mjs` (`npm test`).
- `TeacherWorkspace.jsx`: the review queue with `Segmented` filters, `Pill` labels and `EmptyState`; it calls back to `main.jsx`, which owns the API calls and banners.

The in-browser lesson builder was removed on 2026-11-08 (the owner found it too hard to use); lessons arrive as drafts from the course import and the teacher only approves them.

To add a new screen, write its model file first, test it, then compose the screen from the components above.
