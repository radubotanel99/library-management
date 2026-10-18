A dashboard for the person who runs a library: books, members, loans and what is overdue, in one screen. The look is a library reading room seen through frosted glass: warm paper, soft blurred glows and bookshelf, deep crimson gradient accents, a touch of gold foil. The layout is a calm dark-or-light media dashboard with a floating glass sidebar, a hero header, card sections, a live activity panel and an always-on bar along the bottom.

## Content fundamentals

- Write for a librarian at the desk: short, plain, verb-first. "Lend", "Return", "Mark as lost", "Add book", never "Submit" or "Process".
- Sentence case everywhere except `label` text (section eyebrows, table headers, badges, "See all"), which is uppercase with letter-spacing.
- Talk about **copies** when it is a physical book with a number (#00412) and **titles** when it is the work. A loan is always one copy lent to one member.
- Activity sentences follow one pattern: **member** + verb + *title* + book number ("**Ana Popescu** borrowed *Baltagul* #00412").
- Numbers use thousands separators (12,480). Dates are short ("due 29 Oct"); relative only within a week ("3 days overdue").
- No emoji, no exclamation marks.

## Colour

- Lay every screen on `paper`, under the backdrop: three blurred `glow-*` blobs (`glow-rose`, `glow-gold`, `glow-blush`) and a blurred bookshelf washed out by `scrim` / `scrim-strong`. Floating content is **glass**: `glass` fill, backdrop blur, `shadow-float`, no border. Use `glass-strong` where a control must stay legible over the backdrop (secondary buttons, badges, rings) and `glass-hover` for its hover. Use opaque `surface` only where translucency would hurt (menus, dialogs, table bodies) and `surface-sunken` for inputs, table headers and row hover.
- `line` is now translucent and decorative only (dividers, row rules). Cards are separated by glass and shadow, not by a border.
- `brand` (deep crimson) is the identity colour. Use it for the one primary action per view, the round Lend button, the library monogram, the current nav item and active tabs. Text on it is always `on-brand`.
- Brand fills are gradients: `linear-gradient(135deg, brand-bright, brand)` with `shadow-brand` underneath; on hover the gradient shifts to `brand` → `brand-hover`. The hero mark uses the radial version (`brand-bright` to `brand` at 70%). Never use a gradient for anything that is not brand.
- Stat tiles carry a tint pair on top of glass: rose (default), gold, teal or alert, using the matching `glow-*` and `*-soft` tokens.
- `brand` never means an error. Overdue and destructive states use `danger`, always with an icon and a word (see StatusBadge). Due soon uses `warning` (amber); available and returned use `success` (teal, so status never relies on red-versus-green alone).
- The sidebar is a glass panel floating beside the content: `sidebar` (same frosted fill as `glass`, blur 24px, `radius-2xl`, `shadow-float`), `sidebar-ink` labels, `sidebar-muted` icons, section labels and counts. `sidebar-active` is only the hover fill; the **current page** is the one crimson object in the sidebar: brand gradient, `on-brand` text, `shadow-brand`. The dark theme keeps the same structure with a dark glass fill.
- `gold` is foil: small marks only (cover bands, decorative spines). Never body text.
- Use `brand-text` for red text and links on `paper` and `surface`; it is lighter in dark theme so it stays readable.
- Every text pair named in a token's usage note meets 4.5:1 in both themes; `line-strong`, `focus` and `gold` meet 3:1 where they mark something.

## Type

- Headings are set in **Fraunces** (`--font-serif`): `display` for the library name, `stat` for big numbers, `h1` for page titles, `h2` for section titles.
- Everything you read or type in is set in **Source Sans 3** (`--font-sans`): `title` for book and member names, `body` for cells and text, `caption` for authors, numbers and times (in `ink-muted`), `label` for uppercase eyebrows.
- Both are Google Fonts; `components/bundle.css` imports them. Do not mix in a third family.

## Space, shape, depth

- Spacing steps: `space-1` 4 · `space-2` 8 · `space-3` 12 · `space-4` 16 · `space-5` 20 · `space-6` 24 · `space-8` 32 · `space-10` 40 · `space-12` 48. Panels pad `space-6`; sections sit `space-6` apart.
- Radii: `radius-sm` for covers and inputs; `radius-md` for row hover; `radius-lg` for nav items and CTA tiles; `radius-xl` for glass surfaces and stat tiles; `radius-2xl` for large panels and the sidebar; `radius-pill` for buttons, badges, search fields, avatars, the hero mark and the Lend button.
- Depth is soft and floating: `shadow-float` on glass surfaces, `shadow-float-lg` on the largest panels, `shadow-brand` under brand gradients, `shadow-bar` on the checkout bar. `shadow-card` is a legacy alias of `shadow-float`; do not use it in new work.
- Glass blur: `blur(var(--blur-glass)) saturate(1.2)` (22px). The sidebar uses 24px; glass buttons and bars use 16px. Always include the `-webkit-backdrop-filter` prefix.
- Gradients are reserved for brand fills (see Colour), the backdrop scrim, book covers, ranked-list bars and the CTA tiles (`*-soft` to `glass-strong`).
- Focus: a 2px solid `focus` outline with a 2px offset; inside the light sidebar the ring is `sidebar-ink`.

## Motion

- Motion is decoration only and always slow and small: a few pixels of float, one page turn on load. Nothing that carries information ever depends on it.
- Every animation stops under `@media (prefers-reduced-motion: reduce)`.
- The only animated element today is the dashboard HeroArt (see below).

## Layout

- Dashboard grid: 248px floating glass Sidebar · fluid main column · 300px ActivityFeed, with the CheckoutBar fixed across the bottom.
- Main column order: LibraryHeader, four StatTiles, MemberStrip, RankedList, then ReportCards.
- Below 1200px the activity panel collapses behind a button; below 900px the sidebar becomes a top bar and the checkout bar keeps only the Book no. field and the Lend button.

## Dashboard hero

- Top of the dashboard, on the backdrop with no card: the QuickSearch pill alone on the first row; then the hero, at least 240px tall.
- Hero left: the round brand mark, the date eyebrow (`brand-text`, uppercase label), the page title in `display` size serif, the lead line in `ink-muted`, and under it the two desk actions — "View loans" as a glass pill and "Lend a book" as the one brand-gradient button.
- Hero right: **HeroArt**, an open book drawn in SVG — pages in `glass-hover` → `glass`, outlines in the brand gradient, the turning page and its lines in `gold`, the cover edge in `brand`, a soft `glow-red` → `glow-gold` light behind it, and a few loose pages and specks drifting above. The page turns once on load (1.4s); the book floats 6px over 7s; loose pages drift over 8–10s.
- HeroArt is decorative: `aria-hidden`, no text, no data. It is coloured only through tokens, so it works in both themes with no image files. It is hidden below 1200px, where the hero shrinks back to its content.
- Do not put a photo, a second logo or live data in this slot.

## Iconography

- 24px-grid stroke icons drawn at 18px, `stroke-width` 1.8, round caps and joins, `currentColor`. In Angular, Material Symbols *Outlined* at weight 300 is the closest match.
- Every icon in a button or badge sits next to a word; icon-only buttons (the Lend button) carry an `aria-label`.
- No logo has been provided: the app name is set in Fraunces next to an open-book stroke icon.
- Book covers without a scan use the generated `la-cover` (a coloured block with two gold bands); pick its colour from the category so a title always looks the same.

## Using it in Angular Material

- Map `brand` to the primary palette, `danger` to `warn`, and the surfaces and ink to the theme's surface and on-surface roles; keep the dark theme on `[data-theme="dark"]`.
- `tokens.json` is the source; `frontend/src/styles/tokens.css` is generated from it. Keep the two in step.
- Copy the token custom properties from `tokens.css` into your global styles and build components on the variables, never on raw hex values.
