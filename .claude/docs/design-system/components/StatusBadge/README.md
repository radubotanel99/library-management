# StatusBadge
A small uppercase chip showing the state of one physical copy or loan.

| State | Class | Meaning |
| --- | --- | --- |
| Available | `la-badge--available` | Copy is ACTIVE and on the shelf (`success` on `success-soft`). |
| On loan | `la-badge--loan` | Copy is lent and not yet due (`brand-text` on `brand-soft`). |
| Due soon | `la-badge--due` | Due within the reminder window from Settings (`warning` on `warning-soft`). |
| Overdue | `la-badge--overdue` | Past its computed due date (`danger` on `danger-soft`). |
| Lost / Damaged / Withdrawn | `la-badge--muted` | The copy's non-active status (`ink-muted` on `neutral-soft`). |

**Consumer provides:** the state; the label and icon follow from it.

**Do** always keep the icon and the word: colour alone never carries the state. **Don't** invent new colours per category; categories are not statuses.
