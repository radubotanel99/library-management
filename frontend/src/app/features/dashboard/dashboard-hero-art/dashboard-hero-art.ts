import { Component } from '@angular/core';

/**
 * Decorative hero illustration for the dashboard: an open book drawn in the
 * brand's crimson and gold, with a page that turns once on load and a few loose
 * pages drifting above it.
 *
 * Pure SVG + CSS, coloured only through design tokens, so it follows the light
 * and dark themes with no image files. It carries no information, so it is
 * hidden from assistive technology, and all motion stops for users who ask for
 * reduced motion.
 */
@Component({
  selector: 'app-dashboard-hero-art',
  templateUrl: './dashboard-hero-art.html',
  styleUrl: './dashboard-hero-art.css',
  host: { 'aria-hidden': 'true' },
})
export class DashboardHeroArt {}
