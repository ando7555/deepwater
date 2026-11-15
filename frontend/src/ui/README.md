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

## Worked example: the teacher's review queue

`frontend/src/teacher/` shows the intended split:

- `contentKinds.js`: plain data and functions (no React) that name the kind of a lesson, covered by `contentKinds.test.mjs` (`npm test`).
- `TeacherWorkspace.jsx`: the review queue with `Segmented` filters, `Pill` labels and `EmptyState`; it calls back to `main.jsx`, which owns the API calls and banners.

The in-browser lesson builder was removed on 2026-11-08 (the owner found it too hard to use); lessons arrive as drafts from the course import and the teacher only approves them.

To add a new screen, write its model file first, test it, then compose the screen from the components above.
