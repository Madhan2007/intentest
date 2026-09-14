/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Login screen copy and field identifiers. Account values come
 * from the identity database, not from this file.
 */

export const LOGIN_HEADING = "Welcome Back!";
export const LOGIN_IDENTIFIER_FIELD_ID = "mmo-login-identifier";
export const LOGIN_IDENTIFIER_FIELD_LABEL = "Company username or email";
export const LOGIN_IDENTIFIER_PLACEHOLDER = "company/username or email";
export const LOGIN_IDENTIFIER_REQUIRED =
  "Enter company/username or your email address.";
export const LOGIN_EMAIL_INVALID = "Enter a valid email address.";
export const LOGIN_USERNAME_INVALID =
  "Use company/username or an email address.";
export const LOGIN_EMAIL_PATTERN = /^[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}$/;
export const LOGIN_COMPANY_USERNAME_PATTERN =
  /^[a-z0-9][a-z0-9.-]*\/[a-z][a-z0-9._-]{0,63}$/;
export const PASSWORD_FIELD_ID = "mmo-password";
export const PASSWORD_FIELD_LABEL = "Password";
export const REMEMBER_ME_LABEL = "Remember Me";
export const FORGOT_PASSWORD_LABEL = "Forgot Password";
export const FORGOT_PASSWORD_HREF = "#forgot-password";
export const LOGIN_SUBMIT_LABEL = "Login";
export const LOGIN_SUBMITTING_LABEL = "Logging in…";
export const LOGIN_PASSWORD_REQUIRED = "Enter your password.";
export const LOGIN_GENERIC_ERROR = "Sign-in failed. Please try again.";
export const AUTHENTICATED_HOME_PATH = "/";

/**
 * Validates a login identifier as company/username or an email address.
 * A bare username is not accepted.
 *
 * @param identifier typed company/username or email
 * @returns An error message, or an empty string when the value is valid
 */
export function validateLoginIdentifier(identifier: string): string {
  const normalizedIdentifier = identifier.trim().toLowerCase();
  if (!normalizedIdentifier) {
    return LOGIN_IDENTIFIER_REQUIRED;
  }
  if (normalizedIdentifier.includes("@")) {
    return LOGIN_EMAIL_PATTERN.test(normalizedIdentifier)
      ? ""
      : LOGIN_EMAIL_INVALID;
  }
  return LOGIN_COMPANY_USERNAME_PATTERN.test(normalizedIdentifier)
    ? ""
    : LOGIN_USERNAME_INVALID;
}
