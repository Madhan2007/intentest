# Product branding placeholders (doc 35 §4)

Each subfolder here is one `product_key` from `platform/catalog/products.yaml`.
It holds the **default** branding shown on the login screen before a company
is resolved:

```
products/
├── managemyopz/
│   ├── logo.svg          — shown at rest, before any company is typed
│   └── product.theme.json — color tokens merged into ThemeProvider
├── managemyid/           — placeholder; no apps/ folders shipped yet
└── smartaicampus/        — placeholder; no apps/ folders shipped yet
```

## Branding resolution order (login screen)

1. **Product default** (this folder) — shown immediately on page load, driven
   by which product this build was packaged for
   (`infra/wrappers/package-product.sh --product <key>`).
2. **Company override** — once the user types a company identifier and the
   `/resolve` endpoint (doc 25 §3.4) returns, the company's own
   `logo_uri` / `company_name` replace the product default for the rest of
   the session.

No code in this repository currently reads these files — this folder is a
placeholder for the asset drop and the `product.theme.json` schema. Wire it
into `ThemeProvider.tsx` once real branding assets exist per product.
