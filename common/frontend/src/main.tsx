/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz React coding standards.
 */
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router-dom";
import { SessionProvider } from "@kernel/auth/SessionStore";
import { ThemeProvider } from "@kernel/theme/ThemeProvider";
import { ErrorBoundary } from "@kernel/app/ErrorBoundary";
import { buildRouter } from "@kernel/app/router";
import "@kernel/styles.css";

const router = buildRouter();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ErrorBoundary>
      <ThemeProvider>
        <SessionProvider>
          <RouterProvider router={router} />
        </SessionProvider>
      </ThemeProvider>
    </ErrorBoundary>
  </StrictMode>
);
