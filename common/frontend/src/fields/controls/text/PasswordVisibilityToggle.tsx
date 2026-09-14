/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Toggle control for showing or hiding a password value.
 */
import { memo } from "react";
import { EyeOffIcon } from "@kernel/icons/EyeOffIcon";
import { EyeOpenIcon } from "@kernel/icons/EyeOpenIcon";
import {
  HIDE_PASSWORD_LABEL,
  SHOW_PASSWORD_LABEL,
} from "@kernel/fields/formConstants";
import "@kernel/css/forms.css";

interface PasswordVisibilityToggleProps {
  isPasswordVisible: boolean;
  onToggle: () => void;
}

/**
 * Renders the password visibility button used inside an input wrapper.
 * @param props Visibility state and toggle handler.
 * @returns The toggle button with the matching eye icon.
 */
function PasswordVisibilityToggleComponent({
  isPasswordVisible,
  onToggle,
}: PasswordVisibilityToggleProps) {
  const visibilityLabel = isPasswordVisible
    ? HIDE_PASSWORD_LABEL
    : SHOW_PASSWORD_LABEL;
  return (
    <button
      type="button"
      className="mmo-password-toggle-btn"
      onClick={onToggle}
      title={visibilityLabel}
      aria-label={visibilityLabel}
    >
      {isPasswordVisible ? <EyeOpenIcon /> : <EyeOffIcon />}
    </button>
  );
}

export const PasswordVisibilityToggle = memo(
  PasswordVisibilityToggleComponent
);
