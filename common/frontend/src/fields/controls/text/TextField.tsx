/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz React coding standards.
 */
import { useFormContext } from "@kernel/fields/Form";

interface TextFieldProps {
  name: string;
  label: string;
  autoComplete?: string;
}

/**
 * Renders a text input bound to the nearest `Form` context.
 * @param props Field name, label, and optional autocomplete hint.
 * @returns A labeled text input with inline field errors.
 */
export function TextField({ name, label, autoComplete }: TextFieldProps) {
  const { values, errors, setValue } = useFormContext();
  return (
    <label className="opz-field">
      <span>{label}</span>
      <input
        type="text"
        name={name}
        autoComplete={autoComplete}
        value={values[name] ?? ""}
        onChange={(e) => setValue(name, e.target.value)}
      />
      {errors[name] && <span className="opz-field-error">{errors[name]}</span>}
    </label>
  );
}
