/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz React coding standards.
 */
import { useFormContext } from "@kernel/fields/Form";

interface PasswordFieldProps {
  name: string;
  label: string;
}

/**
 * Renders a password input bound to the nearest `Form` context.
 * @param props Field name and label.
 * @returns A labeled password input with inline field errors.
 */
export function PasswordField({ name, label }: PasswordFieldProps) {
  const { values, errors, setValue } = useFormContext();
  return (
    <label className="opz-field">
      <span>{label}</span>
      <input
        type="password"
        name={name}
        autoComplete="current-password"
        value={values[name] ?? ""}
        onChange={(e) => setValue(name, e.target.value)}
      />
      {errors[name] && <span className="opz-field-error">{errors[name]}</span>}
    </label>
  );
}
