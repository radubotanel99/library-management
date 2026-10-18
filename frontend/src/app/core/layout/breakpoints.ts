import { BreakpointObserver } from '@angular/cdk/layout';
import { inject, Signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';

/**
 * Layout breakpoints from the design system README ("Layout"). Kept here, in
 * TypeScript, so components react to them through `BreakpointObserver` and the
 * component stylesheets stay free of pixel literals.
 */

/** Below 900px the sidebar becomes a top bar with a menu toggle. */
export const COMPACT_LAYOUT = '(max-width: 899.98px)';

/**
 * Below 1200px — a tablet at the front desk, or a desktop with the sidebar
 * taking its width — wide tables drop their low-priority columns.
 */
export const TABLET_LAYOUT = '(max-width: 1199.98px)';

/**
 * Below 1600px — a laptop at 125-150 % display scaling, which gives the browser
 * only ~1280-1536 CSS pixels. The "laptop tier" tightens spacing, large type and
 * component sizes (tokens.css) and Material density (library-material-theme.scss).
 * CSS owns the visual change; use this only when TypeScript needs to know.
 */
export const LAPTOP_LAYOUT = '(max-width: 1599.98px)';

/**
 * `true` while `query` matches, as a signal. Must be called in an injection
 * context (a field initialiser or constructor); the subscription ends with it.
 */
export function breakpointMatches(query: string): Signal<boolean> {
  const observer = inject(BreakpointObserver);
  return toSignal(observer.observe(query).pipe(map((state) => state.matches)), {
    initialValue: observer.isMatched(query),
  });
}
