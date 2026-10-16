import { ComponentRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BookStatus } from '../../../features/book/book.model';
import { LoanState } from '../../../features/loan/loan.model';
import { bookBadge, BadgeSpec, loanStateBadge, StatusBadge } from './status-badge';

describe('loanStateBadge', () => {
  const cases: [LoanState, BadgeSpec][] = [
    ['ACTIVE', { variant: 'loan', icon: 'swap_horiz', labelKey: 'loan.state.ACTIVE' }],
    ['LATE', { variant: 'overdue', icon: 'warning', labelKey: 'loan.state.LATE' }],
    ['FINISHED', { variant: 'available', icon: 'check', labelKey: 'loan.state.FINISHED' }],
  ];

  it.each(cases)('maps %s', (state, expected) => {
    expect(loanStateBadge(state)).toEqual(expected);
  });
});

describe('bookBadge', () => {
  it('maps an active copy on loan to the loan badge', () => {
    expect(bookBadge('ACTIVE', true)).toEqual({
      variant: 'loan',
      icon: 'swap_horiz',
      labelKey: 'book.onLoan.yes',
    });
  });

  it('maps an active copy on the shelf to the available badge', () => {
    expect(bookBadge('ACTIVE', false)).toEqual({
      variant: 'available',
      icon: 'check',
      labelKey: 'book.onLoan.no',
    });
  });

  const removed: BookStatus[] = ['LOST', 'DAMAGED', 'WITHDRAWN'];

  it.each(removed)('maps %s to a muted, word-only badge', (status) => {
    // onLoan is irrelevant for a removed copy; the reason is what is shown.
    expect(bookBadge(status, false)).toEqual({
      variant: 'muted',
      icon: null,
      labelKey: `book.status.${status}`,
    });
  });
});

describe('StatusBadge', () => {
  let fixture: ComponentFixture<StatusBadge>;
  let ref: ComponentRef<StatusBadge>;

  beforeEach(() => {
    Object.defineProperty(navigator, 'language', { value: 'en-US', configurable: true });
    TestBed.configureTestingModule({ imports: [StatusBadge] });
    fixture = TestBed.createComponent(StatusBadge);
    ref = fixture.componentRef;
  });

  function badge(): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector('.la-badge');
  }

  it('renders an overdue loan with an icon and the word, not colour alone', () => {
    ref.setInput('loanState', 'LATE');
    fixture.detectChanges();

    expect(badge()?.classList).toContain('la-badge--overdue');
    expect(badge()?.querySelector('mat-icon')?.textContent?.trim()).toBe('warning');
    expect(badge()?.textContent).toContain('Overdue');
  });

  it('renders a removed copy as a muted word with no icon', () => {
    ref.setInput('bookStatus', 'DAMAGED');
    fixture.detectChanges();

    expect(badge()?.classList).toContain('la-badge--muted');
    expect(badge()?.querySelector('mat-icon')).toBeNull();
    expect(badge()?.textContent?.trim()).toBe('Damaged');
  });

  it('uses onLoan for an active copy', () => {
    ref.setInput('bookStatus', 'ACTIVE');
    ref.setInput('onLoan', true);
    fixture.detectChanges();

    expect(badge()?.classList).toContain('la-badge--loan');
    expect(badge()?.textContent).toContain('On loan');
  });

  it('renders nothing without a state', () => {
    fixture.detectChanges();

    expect(badge()).toBeNull();
  });
});
