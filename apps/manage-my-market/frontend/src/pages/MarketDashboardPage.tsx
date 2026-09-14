/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Main dashboard for Manage My Market powered by the Dashboard Layout Engine.
 */
import React from "react";
import { Link } from "react-router-dom";
import { DashboardRenderer } from "@modules/dashboard-layout/frontend/index";
import { MARKET_WIDGET_REGISTRY } from "../components/MarketWidgets";
import { APP_KEY } from "../constants/manageMyMarketConstants";

export const MarketDashboardPage: React.FC = () => {
  const customHeader = (
    <div
      style={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        paddingBottom: "16px",
        borderBottom: "1px solid var(--opz-color-border, #2a2f3a)",
      }}
    >
      <div>
        <h1 style={{ margin: "0 0 4px 0", fontSize: "24px", color: "#fff", fontWeight: 700 }}>
          Marketing Command Center
        </h1>
        <p style={{ margin: 0, fontSize: "13px", color: "var(--opz-color-muted, #9aa3af)" }}>
          Real-time marketing KPIs, lead pipelines, active campaigns, and telecalling metrics.
        </p>
      </div>

      <div style={{ display: "flex", gap: "10px" }}>
        <Link
          to="/market/leads/new"
          style={{
            padding: "8px 16px",
            background: "#3b82f6",
            color: "#fff",
            textDecoration: "none",
            borderRadius: "6px",
            fontSize: "13px",
            fontWeight: 600,
          }}
        >
          + New Lead
        </Link>
        <Link
          to="/market/campaigns/new"
          style={{
            padding: "8px 16px",
            background: "#10b981",
            color: "#fff",
            textDecoration: "none",
            borderRadius: "6px",
            fontSize: "13px",
            fontWeight: 600,
          }}
        >
          + New Campaign
        </Link>
        <Link
          to="/admin/dashboard-layout"
          style={{
            padding: "8px 14px",
            background: "#1e293b",
            color: "#94a3b8",
            textDecoration: "none",
            borderRadius: "6px",
            fontSize: "13px",
            fontWeight: 500,
            border: "1px solid #334155",
          }}
        >
          Customize Layout
        </Link>
      </div>
    </div>
  );

  return (
    <div style={{ padding: "24px", maxWidth: "1500px", margin: "0 auto" }}>
      <DashboardRenderer
        appKey={APP_KEY}
        widgetRegistry={MARKET_WIDGET_REGISTRY}
        customHeader={customHeader}
      />
    </div>
  );
};
