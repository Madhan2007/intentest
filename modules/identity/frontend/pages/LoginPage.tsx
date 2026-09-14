/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz React coding standards.
 */
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ChangeEvent,
  type FormEvent,
} from "react";
import { Navigate } from "react-router-dom";
import { useSession } from "@kernel/auth/SessionStore";
import { ApiError } from "@kernel/api/errors";
import { isAbortError } from "@kernel/api/abortError";
import { BrandingPanel } from "@kernel/brand/BrandingPanel";
import { ThemeToggle } from "@kernel/brand/ThemeToggle";
import { PasswordVisibilityToggle } from "@kernel/fields/controls/text/PasswordVisibilityToggle";
import {
  AUTOCOMPLETE_CURRENT_PASSWORD,
  AUTOCOMPLETE_USERNAME,
  INPUT_TYPE_CHECKBOX,
  INPUT_TYPE_PASSWORD,
  INPUT_TYPE_TEXT,
} from "@kernel/fields/formConstants";
import {
  AUTHENTICATED_HOME_PATH,
  FORGOT_PASSWORD_HREF,
  FORGOT_PASSWORD_LABEL,
  LOGIN_GENERIC_ERROR,
  LOGIN_HEADING,
  LOGIN_IDENTIFIER_FIELD_ID,
  LOGIN_IDENTIFIER_FIELD_LABEL,
  LOGIN_IDENTIFIER_PLACEHOLDER,
  LOGIN_PASSWORD_REQUIRED,
  LOGIN_SUBMIT_LABEL,
  LOGIN_SUBMITTING_LABEL,
  PASSWORD_FIELD_ID,
  PASSWORD_FIELD_LABEL,
  REMEMBER_ME_LABEL,
  validateLoginIdentifier,
} from "../identityFrontendConstants";
import "@kernel/css/split-panel.css";
import "@kernel/css/forms.css";
import "./LoginPage.css";

/**
 * Renders the identity login operation using shared brand and form chrome.
 * @returns The login screen, or a redirect when a session already exists.
 */
export function LoginPage() {
  const { user, login } = useSession();
  const [loginIdentifier, setLoginIdentifier] = useState("");
  const [passwordValue, setPasswordValue] = useState("");
  const [isPasswordVisible, setIsPasswordVisible] = useState(false);
  const [isRememberMeChecked, setIsRememberMeChecked] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [identifierError, setIdentifierError] = useState("");
  const [errorMessage, setErrorMessage] = useState("");
  const isPageMountedRef = useRef(true);
  const loginAbortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    isPageMountedRef.current = true;
    return () => {
      isPageMountedRef.current = false;
      loginAbortRef.current?.abort();
    };
  }, []);

  const handleIdentifierChange = useCallback(
    (changeEvent: ChangeEvent<HTMLInputElement>) => {
      setLoginIdentifier(changeEvent.target.value);
      setIdentifierError("");
    },
    []
  );

  const handlePasswordChange = useCallback(
    (changeEvent: ChangeEvent<HTMLInputElement>) => {
      setPasswordValue(changeEvent.target.value);
    },
    []
  );

  const handleRememberMeChange = useCallback(
    (changeEvent: ChangeEvent<HTMLInputElement>) => {
      setIsRememberMeChecked(changeEvent.target.checked);
    },
    []
  );

  const handlePasswordVisibilityToggle = useCallback(() => {
    setIsPasswordVisible((isCurrentlyVisible) => !isCurrentlyVisible);
  }, []);

  const handleSubmit = useCallback(
    async (formEvent: FormEvent<HTMLFormElement>) => {
      formEvent.preventDefault();
      const identifierValidationMessage = validateLoginIdentifier(loginIdentifier);
      if (identifierValidationMessage) {
        setIdentifierError(identifierValidationMessage);
        setErrorMessage("");
        return;
      }
      if (!passwordValue) {
        setIdentifierError("");
        setErrorMessage(LOGIN_PASSWORD_REQUIRED);
        return;
      }
      setIsSubmitting(true);
      setIdentifierError("");
      setErrorMessage("");
      loginAbortRef.current?.abort();
      const loginAbortController = new AbortController();
      loginAbortRef.current = loginAbortController;
      try {
        await login(loginIdentifier.trim(), passwordValue, {
          signal: loginAbortController.signal,
        });
      } catch (loginError) {
        if (!isPageMountedRef.current || isAbortError(loginError)) {
          return;
        }
        const resolvedErrorMessage =
          loginError instanceof ApiError
            ? loginError.message
            : LOGIN_GENERIC_ERROR;
        setErrorMessage(resolvedErrorMessage);
      } finally {
        if (
          isPageMountedRef.current &&
          loginAbortRef.current === loginAbortController
        ) {
          setIsSubmitting(false);
        }
      }
    },
    [loginIdentifier, passwordValue, login]
  );

  if (user) {
    return <Navigate to={AUTHENTICATED_HOME_PATH} replace />;
  }

  const passwordInputType = isPasswordVisible
    ? INPUT_TYPE_TEXT
    : INPUT_TYPE_PASSWORD;
  const submitButtonLabel = isSubmitting
    ? LOGIN_SUBMITTING_LABEL
    : LOGIN_SUBMIT_LABEL;

  return (
    <div className="mmo-fullpage-viewport mmo-login-viewport">
      <ThemeToggle />
      <div className="mmo-split-card">
        <BrandingPanel />
        <div className="mmo-right-panel">
          <h2 className="mmo-panel-heading">{LOGIN_HEADING}</h2>
          <form className="mmo-form" onSubmit={handleSubmit} noValidate>
            <div className="mmo-form-group">
              <label className="mmo-form-label" htmlFor={LOGIN_IDENTIFIER_FIELD_ID}>
                {LOGIN_IDENTIFIER_FIELD_LABEL}
              </label>
              <div className="mmo-input-wrapper">
                <input
                  id={LOGIN_IDENTIFIER_FIELD_ID}
                  type={INPUT_TYPE_TEXT}
                  className="mmo-input"
                  value={loginIdentifier}
                  onChange={handleIdentifierChange}
                  placeholder={LOGIN_IDENTIFIER_PLACEHOLDER}
                  autoComplete={AUTOCOMPLETE_USERNAME}
                  required
                />
              </div>
              {identifierError && (
                <span className="mmo-form-error">{identifierError}</span>
              )}
            </div>
            <div className="mmo-form-group">
              <label className="mmo-form-label" htmlFor={PASSWORD_FIELD_ID}>
                {PASSWORD_FIELD_LABEL}
              </label>
              <div className="mmo-input-wrapper">
                <input
                  id={PASSWORD_FIELD_ID}
                  type={passwordInputType}
                  className="mmo-input mmo-input-password"
                  value={passwordValue}
                  onChange={handlePasswordChange}
                  autoComplete={AUTOCOMPLETE_CURRENT_PASSWORD}
                  required
                />
                <PasswordVisibilityToggle
                  isPasswordVisible={isPasswordVisible}
                  onToggle={handlePasswordVisibilityToggle}
                />
              </div>
              {errorMessage && (
                <span className="mmo-form-error">{errorMessage}</span>
              )}
            </div>
            <div className="mmo-option-row">
              <label className="mmo-checkbox-label">
                <input
                  type={INPUT_TYPE_CHECKBOX}
                  className="mmo-checkbox"
                  checked={isRememberMeChecked}
                  onChange={handleRememberMeChange}
                />
                <span className="mmo-checkbox-text">{REMEMBER_ME_LABEL}</span>
              </label>
              <a href={FORGOT_PASSWORD_HREF} className="mmo-text-link">
                {FORGOT_PASSWORD_LABEL}
              </a>
            </div>
            <button
              type="submit"
              className="mmo-primary-btn"
              disabled={isSubmitting}
            >
              {submitButtonLabel}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
