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
  'nav.books': 'Cărți',

  'common.language': 'Limbă',
  'common.actions': 'Acțiuni',
  'common.edit': 'Modifică',
  'common.delete': 'Șterge',
  'common.remove': 'Scoate din colecție',
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

  'book.list.title': 'Cărți',
  'book.list.empty': 'Nicio carte nu corespunde filtrelor curente.',
  'book.list.loadError': 'Cărțile nu au putut fi încărcate.',
  'book.list.add': 'Adaugă carte',
  'book.list.search': 'Caută după titlu, autor sau număr',
  'book.list.category': 'Categorie',
  'book.list.allCategories': 'Toate categoriile',
  'book.column.bookNumber': 'Număr',
  'book.column.title': 'Titlu',
  'book.column.author': 'Autor',
  'book.column.category': 'Categorie',
  'book.column.publisher': 'Editură',
  'book.column.price': 'Preț',
  'book.column.onLoan': 'Împrumutată',
  'book.onLoan.yes': 'Împrumutată',
  'book.onLoan.no': 'La raft',
  'book.form.createTitle': 'Adaugă carte',
  'book.form.editTitle': 'Modifică cartea',
  'book.form.title': 'Titlu',
  'book.form.author': 'Autor',
  'book.form.bookNumber': 'Număr de inventar',
  'book.form.categoryId': 'Categorie',
  'book.form.publisher': 'Editură',
  'book.form.price': 'Preț',
  'book.remove.title': 'Scoate cartea din colecție',
  'book.remove.message':
    'Scoți din colecție „{{title}}” de {{author}} (nr. {{bookNumber}})? Indică mai jos motivul.',
  'book.remove.reason': 'Motiv',
  'book.remove.note': 'Observații',
  'book.remove.blocked': 'O carte împrumutată nu poate fi scoasă din colecție.',
  'book.status.LOST': 'Pierdută',
  'book.status.DAMAGED': 'Deteriorată',
  'book.status.WITHDRAWN': 'Retrasă',

  'validation.required': 'Acest câmp este obligatoriu.',
  'validation.maxLength': 'Folosește cel mult {{max}} caractere.',
  'validation.min': 'Folosește o valoare de cel puțin {{min}}.',
  'validation.server': 'Valoarea a fost respinsă de server.',

  'error.BOOK_NUMBER_ALREADY_EXISTS': 'Există deja o carte activă cu acest număr.',
  'error.BOOK_NOT_FOUND': 'Această carte nu mai există.',
  'error.BOOK_HAS_OPEN_LOAN':
    'Cartea este împrumutată și nu poate fi scoasă din colecție până nu este returnată.',
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
