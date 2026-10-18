import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    // No stored preference and an English test browser, so the shell renders EN.
    Object.defineProperty(navigator, 'language', { value: 'en-US', configurable: true });
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the translated brand in the sidebar', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.la-sidebar__brand')?.textContent).toContain('My Library');
  });

  it('should link every screen from the sidebar', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const links = [
      ...(fixture.nativeElement as HTMLElement).querySelectorAll<HTMLAnchorElement>('nav a.la-nav'),
    ];
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/dashboard',
      '/books',
      '/categories',
      '/members',
      '/loans',
      '/books/archive',
      '/settings',
    ]);
    expect(links.map((link) => link.textContent?.trim().replace(/^\S+\s+/, ''))).toEqual([
      'Dashboard',
      'Books',
      'Categories',
      'Members',
      'Loans',
      'Archive',
      'Settings',
    ]);
  });

  it('should offer the theme toggle with a translated action label', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const toggle = (fixture.nativeElement as HTMLElement).querySelector(
      '.app-sidebar__footer button',
    );
    expect(toggle?.textContent).toMatch(/(Dark|Light) theme/);
  });

  it('should set the translated document title', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    expect(TestBed.inject(Title).getTitle()).toBe('My Library');
  });
});
