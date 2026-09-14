/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz React coding standards.
 */
export type FieldType =
  | "text"
  | "password"
  | "email"
  | "number"
  // …the full catalog (doc 03 §3) — numeric, datetime, choice, relation,
  // structured, media, editorial, identity, hidden — ships as each type is
  // actually needed by a packed module. See ./README.md.
  ;

export interface FieldDef {
  name: string;
  type: FieldType;
  label: string;
  required?: boolean;
  hint?: string;
}
