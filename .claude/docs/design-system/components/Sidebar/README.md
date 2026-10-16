# Sidebar
The fixed left navigation: app name, the main sections, and the category list as quick filters.

- Width 232px; background `sidebar`; labels `sidebar-ink`; counts and the section label `sidebar-muted`.
- The current page gets `aria-current="page"`: a `sidebar-active` fill plus a `gold` marker bar on the left edge.
- The Overdue count uses `la-nav__count--alert` (a `brand` pill) so it is visible at a glance.
- Below the rule, categories link to the Books list pre-filtered, with their copy counts.

**Consumer provides:** the nav items (icon, label, optional count, route), the current route, and the category list.

**Do** keep it to 7 main items. **Don't** put actions (Add book) in the sidebar; they belong in the page header or the checkout bar.
