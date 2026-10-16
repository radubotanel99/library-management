# RankedList
A numbered list of books (or members) ranked by a count, like "Most borrowed this week".

- Columns: rank (serif, `ink-muted`), a generated cover (`la-cover`, variants `--ink`, `--gold`, `--teal`, `--oxblood`), title + author/category, the count, and a ghost action.
- Rows highlight with `surface-sunken` on hover; the whole row opens the book.

**Consumer provides:** up to 5 items with title, meta line, count, and a link.

**Do** use the generated cover when there is no scanned cover; pick the variant from the category so a title always looks the same. **Don't** show more than 5 rows on the dashboard; link to the full list with "See all".
