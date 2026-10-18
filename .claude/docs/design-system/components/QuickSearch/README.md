# QuickSearch
One search pill at the top of the dashboard that looks up books, members and open loans at once.

- Glass pill (`la-search`), focus ring around the whole pill. Results open in a near-opaque glass panel (`glass-hover`, `shadow-float-lg`, `radius-xl`) grouped as Books / Members / Open loans, 3 rows each, with an "Open list" link per group.
- Starts after 2 characters, debounced 300ms; uses the existing list endpoints with `search` (`API_CONTRACT.md` §5, §7, §8) — no search endpoint of its own.
- Book rows show title, author and number together, with the StatusBadge; loan rows show the book, the member and the loan state badge.
- Escape or a click outside closes the panel. All text goes through i18n keys (`dashboard.search.*`).

**Consumer provides:** nothing — `<app-dashboard-search />` brings its own services.

**Do** keep it on the dashboard only; list screens have their own search boxes. **Don't** show more than 3 rows per group.
