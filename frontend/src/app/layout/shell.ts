import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { I18nService, Lang } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { TranslationKey } from '../core/i18n/translations/translation-key';

/** Application chrome: title bar, navigation, language switcher, routed content. */
@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    TranslatePipe,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  private readonly i18n = inject(I18nService);

  protected readonly lang = this.i18n.lang;
  protected readonly langs = this.i18n.availableLangs;

  protected langLabel(lang: Lang): TranslationKey {
    return lang === 'ro' ? 'lang.ro' : 'lang.en';
  }

  protected setLang(lang: Lang): void {
    this.i18n.setLang(lang);
  }
}
