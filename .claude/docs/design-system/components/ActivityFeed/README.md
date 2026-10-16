# ActivityFeed
The right-hand panel: a live timeline of what just happened at the desk.

- Events grouped by day (`label` style headers). Each event has a round icon: lend (`brand-soft`), return (`success-soft`), overdue (`danger-soft`), other (`surface-sunken`).
- Text is one sentence: **member** + verb + *title* + book number; time in `caption`.
- A pill search filters the feed.

**Consumer provides:** the events (type, member, book, time), newest first.

**Do** keep sentences short and verbs consistent: borrowed, returned, is N days overdue, registered. **Don't** show more than ~30 events; older ones go to the Loans page.
