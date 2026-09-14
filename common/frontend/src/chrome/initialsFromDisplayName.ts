/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Builds avatar initials from a signed-in display name.
 */

const EMPTY_INITIALS = "U";
const SINGLE_NAME_INITIAL_LENGTH = 2;
const WHITESPACE_PATTERN = /\s+/;

/**
 * Returns two-letter initials for an avatar fallback.
 *
 * @param displayName signed-in user's display name
 * @returns uppercase initials
 */
export function initialsFromDisplayName(displayName: string): string {
  const nameParts = displayName.trim().split(WHITESPACE_PATTERN).filter(Boolean);
  if (nameParts.length === 0) {
    return EMPTY_INITIALS;
  }
  if (nameParts.length === 1) {
    return nameParts[0].slice(0, SINGLE_NAME_INITIAL_LENGTH).toUpperCase();
  }
  const firstInitial = nameParts[0][0];
  const lastInitial = nameParts[nameParts.length - 1][0];
  return `${firstInitial}${lastInitial}`.toUpperCase();
}
