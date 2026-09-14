/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz React coding standards.
 */
import { useState } from "react";
import { Navigate } from "react-router-dom";
import { useSession } from "@kernel/auth/SessionStore";
import { ApiError } from "@kernel/api/errors";
import { Form } from "@kernel/fields/Form";
import { TextField } from "@kernel/fields/controls/text/TextField";
import { PasswordField } from "@kernel/fields/controls/text/PasswordField";

/**
 * Renders the local username and password login form.
 * @returns The login screen or a redirect for authenticated users.
 */
export function LoginPage() {
  const { user, login } = useSession();
  const [submitting, setSubmitting] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});

  if (user) return <Navigate to="/" replace />;

  const onSubmit = async (values: Record<string, string>) => {
    setSubmitting(true);
    setErrors({});
    try {
      await login(values.username ?? "", values.password ?? "");
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Sign-in failed. Please try again.";
      setErrors({ password: msg });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="opz-login-page">
      <div className="opz-login-card">
        <h1>Sign in</h1>
        <Form onSubmit={onSubmit} errors={errors}>
          <TextField name="username" label="Username" autoComplete="username" />
          <PasswordField name="password" label="Password" />
          <button className="opz-submit" type="submit" disabled={submitting}>
            {submitting ? "Signing in…" : "Sign in"}
          </button>
        </Form>
      </div>
    </div>
  );
}
