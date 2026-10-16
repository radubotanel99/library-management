# StatTile
A single headline number with a label and one line of context.

- Label in `label` style (`ink-muted`), value in `stat` (`ink`), context line in `caption`.
- `la-stat--alert` turns the value `danger`; use it only for Overdue when the count is above zero.

**Consumer provides:** label, value (already formatted), one context line.

**Do** show four tiles at most in a row. **Don't** add sparklines or arrows unless the number has a meaningful trend.
