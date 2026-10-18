# HeroArt
A decorative open book that fills the right side of the dashboard hero.

- Built as inline SVG + CSS (`frontend/src/app/features/dashboard/dashboard-hero-art/`), coloured only with tokens: pages `glass-hover` → `glass`, outlines `brand-bright` → `brand`, turning page and its lines `gold`, cover edge `brand`, background light `glow-red` → `glow-gold`.
- Motion: the right-hand page turns once on load (1.4s, ease-out); the book floats 6px over 7s; loose pages and specks drift over 8–10s. All of it stops under `prefers-reduced-motion: reduce`.
- `aria-hidden="true"`: it carries no text and no data.
- Height 240px; it takes the free width between the title block and the edge, and is hidden below 1200px.

**Consumer provides:** nothing — drop `<app-dashboard-hero-art />` into the hero.

**Do** keep it the only illustration on a screen. **Don't** add text, numbers or click targets to it, and don't speed up the motion.
