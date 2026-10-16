A dashboard for the person who runs a library: books, members, loans and what is overdue, in one screen. The look is a library reading room: warm paper, deep crimson cloth bindings, a touch of gold foil. The layout is a calm dark-or-light media dashboard with a fixed sidebar, a hero header, card sections, a live activity panel and an always-on bar along the bottom.

## Content fundamentals

- Write for a librarian at the desk: short, plain, verb-first. "Lend", "Return", "Mark as lost", "Add book", never "Submit" or "Process".
- Sentence case everywhere except `label` text (section eyebrows, table headers, badges, "See all"), which is uppercase with letter-spacing.
- Talk about **copies** when it is a physical book with a number (#00412) and **titles** when it is the work. A loan is always one copy lent to one member.
- Activity sentences follow one pattern: **member** + verb + *title* + book number ("**Ana Popescu** borrowed *Baltagul* #00412").
- Numbers use thousands separators (12,480). Dates are short ("due 29 Oct"); relative only within a week ("3 days overdue").
- No emoji, no exclamation marks.

## Colour

- Lay every screen on `paper`; put content in `surface` cards with a `line` hairline and `shadow-card`. Use `surface-sunken` for inputs, table headers and row hover.
- `brand` (deep crimson) is the identity colour. Use it for the one primary action per view, the round Lend button, the library monogram and active tabs. Text on it is always `on-brand`.
- `brand` never means an error. Overdue and destructive states use `danger`, always with an icon and a word (see StatusBadge). Due soon uses `warning` (amber); available and returned use `success` (teal, so status never relies on red-versus-green alone).
- The sidebar is the binding: `sidebar` (oxblood in light, near-black in dark), `sidebar-ink` labels, `sidebar-active` for the current page plus a `gold` marker.
- `gold` is foil: small marks only (the active nav marker, cover bands, decorative spines). Never body text.
- Use `brand-text` for red text and links on `paper` and `surface`; it is lighter in dark theme so it stays readable.
- Every text pair named in a token's usage note meets 4.5:1 in both themes; `line-strong`, `focus` and `gold` meet 3:1 where they mark something.

## Type

- Headings are set in **Fraunces** (`--font-serif`): `display` for the library name, `stat` for big numbers, `h1` for page titles, `h2` for section titles.
- Everything you read or type in is set in **Source Sans 3** (`--font-sans`): `title` for book and member names, `body` for cells and text, `caption` for authors, numbers and times (in `ink-muted`), `label` for uppercase eyebrows.
- Both are Google Fonts; `components/bundle.css` imports them. Do not mix in a third family.

## Space, shape, depth

- Spacing steps: `space-1` 4 · `space-2` 8 · `space-3` 12 · `space-4` 16 · `space-6` 24 · `space-8` 32 · `space-12` 48. Cards pad `space-4`; sections sit `space-6` apart.
- Radii: `radius-sm` for badges, covers and inputs; `radius-md` for rows and nav items; `radius-lg` for cards and panels; `radius-pill` for buttons, search fields, avatars and the Lend button.
- Depth is quiet: `shadow-card` on cards, `shadow-bar` on the checkout bar, nothing else. No gradients.
- Focus: a 2px solid `focus` outline with a 2px offset; inside the light sidebar the ring is `sidebar-ink`.

## Layout

- Dashboard grid: 232px Sidebar · fluid main column · 300px ActivityFeed, with the CheckoutBar fixed across the bottom.
- Main column order: LibraryHeader, four StatTiles, MemberStrip, RankedList, then ReportCards.
- Below 1200px the activity panel collapses behind a button; below 900px the sidebar becomes a top bar and the checkout bar keeps only the Book no. field and the Lend button.

## Iconography

- 24px-grid stroke icons drawn at 18px, `stroke-width` 1.8, round caps and joins, `currentColor`. In Angular, Material Symbols *Outlined* at weight 300 is the closest match.
- Every icon in a button or badge sits next to a word; icon-only buttons (the Lend button) carry an `aria-label`.
- No logo has been provided: the app name is set in Fraunces next to an open-book stroke icon.
- Book covers without a scan use the generated `la-cover` (a coloured block with two gold bands); pick its colour from the category so a title always looks the same.

## Using it in Angular Material

- Map `brand` to the primary palette, `danger` to `warn`, and the surfaces and ink to the theme's surface and on-surface roles; keep the dark theme on `[data-theme="dark"]`.
- Copy the token custom properties from `tokens.css` into your global styles and build components on the variables, never on raw hex values.
