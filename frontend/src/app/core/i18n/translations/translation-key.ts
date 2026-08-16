import { en } from './en';

/** Every key present in the English dictionary. Other languages must cover all of them. */
export type TranslationKey = keyof typeof en;

/** Narrows an arbitrary string (e.g. `error.${code}` built from a backend code) to a known key. */
export function isTranslationKey(key: string): key is TranslationKey {
  return Object.prototype.hasOwnProperty.call(en, key);
}
