/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz React coding standards.
 */
import { Component, type ErrorInfo, type ReactNode } from "react";

interface Props {
  children: ReactNode;
}

interface State {
  error: Error | null;
}

/**
 * Catches uncaught React render errors and shows a safe fallback screen.
 * @returns A boundary component that wraps the application tree.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error("Unhandled UI error", error, info);
  }

  render(): ReactNode {
    if (this.state.error) {
      return (
        <div className="opz-error-boundary">
          <h1>Something went wrong</h1>
          <p>Please reload the page. If this keeps happening, contact support.</p>
        </div>
      );
    }
    return this.props.children;
  }
}
