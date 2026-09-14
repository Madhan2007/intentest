/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Applies the kernel theme token set to CSS custom properties.
 */
import { type ReactNode } from "react";
import { tokens } from "@kernel/theme/tokens";

interface ThemeProviderProps {
  children: ReactNode;
}

let hasAppliedThemeTokens = false;

function applyThemeTokens(): void {
  if (hasAppliedThemeTokens) {
    return;
  }
  hasAppliedThemeTokens = true;
  const rootElement = document.documentElement;
  for (const [tokenName, tokenValue] of Object.entries(tokens)) {
    const cssVariableName = `--opz-${tokenName.replace(/[A-Z]/g, (capitalLetter) => `-${capitalLetter.toLowerCase()}`)}`;
    rootElement.style.setProperty(cssVariableName, tokenValue);
  }
}

/**
 * Applies theme tokens once per page lifetime, then renders children.
 *
 * @param props Child React tree that consumes the applied theme variables
 * @returns The original child tree
 */
export function ThemeProvider({ children }: ThemeProviderProps) {
  applyThemeTokens();
  return children;
}
