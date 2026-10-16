# Button
Pill-shaped buttons for every action in the admin; one primary per view.

**Variants** (`la-btn` + one of):
- `la-btn--primary` – the main action of a view (Add book, Save, Lend). `brand` fill, `on-brand` text.
- `la-btn--secondary` – everything else (Edit, Settings, Export). `surface` fill with a `line-strong` border.
- `la-btn--ghost` – low-weight links in rows and section headers (See all, Details). `brand-text`.
- `la-btn--danger` – destructive or loss-recording actions (Mark as lost, Withdraw, Delete member). `danger` outline, never a filled red, so it never competes with the brand.
- Add `la-btn--sm` for buttons inside rows, tables and toolbars.

**Consumer provides:** the label (verb first: "Add book", not "New"), an optional leading 18px stroke icon, and `disabled` when unavailable.

**Do** keep labels short and in sentence case. **Don't** place two primary buttons side by side, and don't use `brand` to signal an error.

In Angular Material map `primary` to `mat-flat-button color="primary"`, `secondary` to `mat-stroked-button`, `ghost` to `mat-button`.
