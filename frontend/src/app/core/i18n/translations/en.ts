/**
 * English dictionary — the source of truth for translation keys.
 *
 * `as const` freezes the key set so `TranslationKey` can be derived from it and
 * `ro.ts` is forced by the compiler to provide every key.
 */
export const en = {
  'app.title': 'Library Management',

  'nav.categories': 'Categories',

  'common.language': 'Language',
  'common.actions': 'Actions',
  'common.edit': 'Edit',
  'common.delete': 'Delete',
  'common.cancel': 'Cancel',
  'common.save': 'Save',
  'common.retry': 'Retry',
  'common.dismiss': 'Dismiss',
  'common.notSet': '—',

  'lang.en': 'English',
  'lang.ro': 'Romanian',

  'category.list.title': 'Categories',
  'category.list.empty': 'No categories yet. Add the first one to get started.',
  'category.list.loadError': 'The categories could not be loaded.',
  'category.list.add': 'Add category',
  'category.column.name': 'Name',
  'category.column.description': 'Description',
  'category.column.bookCount': 'Books',
  'category.delete.title': 'Delete category',
  'category.delete.message': 'Delete the category "{{name}}"? This cannot be undone.',
  'category.delete.blocked': 'A category that still has books cannot be deleted.',
  'category.form.createTitle': 'Add category',
  'category.form.editTitle': 'Edit category',
  'category.form.name': 'Name',
  'category.form.description': 'Description',

  'validation.required': 'This field is required.',
  'validation.maxLength': 'Use at most {{max}} characters.',
  'validation.server': 'This value was rejected by the server.',

  'error.CATEGORY_NAME_ALREADY_EXISTS': 'A category with this name already exists.',
  'error.CATEGORY_HAS_BOOKS': 'This category still has books and cannot be deleted.',
  'error.CATEGORY_NOT_FOUND': 'This category no longer exists.',
  'error.VALIDATION_ERROR': 'Some of the submitted data is not valid.',
  'error.RESOURCE_NOT_FOUND': 'The requested resource was not found.',
  'error.DATA_INTEGRITY_VIOLATION': 'The change conflicts with existing data.',
  'error.INTERNAL_ERROR': 'Something went wrong on the server. Please try again.',
  'error.NETWORK_ERROR': 'The server cannot be reached. Check your connection.',
  'error.UNKNOWN': 'An unexpected error occurred.',
} as const;
