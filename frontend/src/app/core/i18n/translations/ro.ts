import { TranslationKey } from './translation-key';

/**
 * Romanian dictionary.
 *
 * Typed as `Record<TranslationKey, string>` on purpose: a key added to `en.ts`
 * and forgotten here is a compile error, not a missing string at runtime.
 */
export const ro: Record<TranslationKey, string> = {
  'app.title': 'Gestiune bibliotecă',

  'nav.categories': 'Categorii',

  'common.language': 'Limbă',
  'common.actions': 'Acțiuni',
  'common.edit': 'Modifică',
  'common.delete': 'Șterge',
  'common.cancel': 'Anulează',
  'common.save': 'Salvează',
  'common.retry': 'Reîncearcă',
  'common.dismiss': 'Închide',
  'common.notSet': '—',

  'lang.en': 'Engleză',
  'lang.ro': 'Română',

  'category.list.title': 'Categorii',
  'category.list.empty': 'Nu există categorii. Adaugă prima categorie pentru a începe.',
  'category.list.loadError': 'Categoriile nu au putut fi încărcate.',
  'category.list.add': 'Adaugă categorie',
  'category.column.name': 'Nume',
  'category.column.description': 'Descriere',
  'category.column.bookCount': 'Cărți',
  'category.delete.title': 'Șterge categoria',
  'category.delete.message': 'Ștergi categoria „{{name}}”? Acțiunea nu poate fi anulată.',
  'category.delete.blocked': 'O categorie care are cărți nu poate fi ștearsă.',
  'category.form.createTitle': 'Adaugă categorie',
  'category.form.editTitle': 'Modifică categoria',
  'category.form.name': 'Nume',
  'category.form.description': 'Descriere',

  'validation.required': 'Acest câmp este obligatoriu.',
  'validation.maxLength': 'Folosește cel mult {{max}} caractere.',
  'validation.server': 'Valoarea a fost respinsă de server.',

  'error.CATEGORY_NAME_ALREADY_EXISTS': 'Există deja o categorie cu acest nume.',
  'error.CATEGORY_HAS_BOOKS': 'Categoria are cărți asociate și nu poate fi ștearsă.',
  'error.CATEGORY_NOT_FOUND': 'Această categorie nu mai există.',
  'error.VALIDATION_ERROR': 'O parte dintre datele trimise nu sunt valide.',
  'error.RESOURCE_NOT_FOUND': 'Resursa cerută nu a fost găsită.',
  'error.DATA_INTEGRITY_VIOLATION': 'Modificarea intră în conflict cu datele existente.',
  'error.INTERNAL_ERROR': 'A apărut o eroare pe server. Încearcă din nou.',
  'error.NETWORK_ERROR': 'Serverul nu poate fi contactat. Verifică conexiunea.',
  'error.UNKNOWN': 'A apărut o eroare neașteptată.',
};
