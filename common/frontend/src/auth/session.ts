/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz React coding standards.
 */
/** Compact session shape from GET/POST identity endpoints (doc 18 §4). */
export interface SessionUser {
  id: string;
  n: string; // display name
  r?: string[]; // role ids
  c?: string; // company id
}

export type AccessMatrix = Record<string, Record<string, string>>;

export interface SessionState {
  user: SessionUser | null;
  matrix: AccessMatrix;
}

/** Generic `has(app, feature, letter)` — kernel never hardcodes an app id (doc 18 §4.1). */
export function hasAccess(matrix: AccessMatrix, app: string, feature: string, letter: string): boolean {
  const appGrants = matrix[app];
  if (!appGrants) return false;
  const letters = appGrants[feature] ?? appGrants["*"] ?? "";
  return letters.includes(letter);
}
