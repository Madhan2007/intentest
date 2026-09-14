/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Left-side brand column reused by login and similar operations.
 */
import { memo } from "react";
import { CompanyLogo } from "@kernel/brand/CompanyLogo";
import { COMPANY_NAME, COMPANY_TAGLINE } from "@kernel/brand/brandConstants";
import "@kernel/css/brand.css";
import "@kernel/css/split-panel.css";

/**
 * Renders the branded left panel with logo, company name, and tagline.
 * @returns The branding column.
 */
function BrandingPanelComponent() {
  return (
    <div className="mmo-left-panel">
      <div className="mmo-brand-header">
        <CompanyLogo />
        <div className="mmo-brand-text">
          <h1 className="mmo-company-name">{COMPANY_NAME}</h1>
          <p className="mmo-tagline">{COMPANY_TAGLINE}</p>
        </div>
      </div>
    </div>
  );
}

export const BrandingPanel = memo(BrandingPanelComponent);
