/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz React coding standards.
 */
import { createContext, useContext, useState, type FormEvent, type ReactNode } from "react";

interface FormContextValue {
  values: Record<string, string>;
  errors: Record<string, string>;
  setValue: (name: string, value: string) => void;
}

const FormContext = createContext<FormContextValue | null>(null);

/**
 * Reads the active form state and field error bindings.
 * @returns The current form context for child field controls.
 */
export function useFormContext(): FormContextValue {
  const ctx = useContext(FormContext);
  if (!ctx) throw new Error("Field must be used inside <Form>");
  return ctx;
}

interface FormProps {
  children: ReactNode;
  onSubmit: (values: Record<string, string>) => void | Promise<void>;
  errors?: Record<string, string>;
}

/**
 * Hosts a minimal controlled form for field-kit inputs.
 * @param props Form children, submit handler, and optional field errors.
 * @returns The form context provider and HTML form element.
 */
export function Form({ children, onSubmit, errors = {} }: FormProps) {
  const [values, setValues] = useState<Record<string, string>>({});

  const setValue = (name: string, value: string) => setValues((prev) => ({ ...prev, [name]: value }));

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    void onSubmit(values);
  };

  return (
    <FormContext.Provider value={{ values, errors, setValue }}>
      <form onSubmit={handleSubmit}>{children}</form>
    </FormContext.Provider>
  );
}
