# Kernel field kit (doc 03 §3, doc 22)

Implemented so far, sufficient for the login form:

- `controls/text/TextField.tsx`
- `controls/text/PasswordField.tsx`
- `Form.tsx` / `FormContext.tsx` / `useForm.ts`

The full catalog in doc 03 §3 (numeric, datetime, choice, relation,
structured, media, editorial, collection/grid controls, import/export/bulk
toolbar, etc.) is **not built yet** — there is no packed module that needs
them. Add each control here, generically (no app names), the first time a
real module's `FormEnvelope` requests that `FieldType`.
