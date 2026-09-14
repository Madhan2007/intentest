/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Renders a dashboard layout according to zone placement and widget grid specs.
 */
import React, { useEffect, useState } from "react";
import { httpOpzhub } from "@kernel/api/http-client";

export interface WidgetPlacement {
  widget_key: string;
  x?: number;
  y?: number;
  w?: number;
  h?: number;
  props?: Record<string, unknown>;
}

export interface DashboardLayoutData {
  zones: Record<string, WidgetPlacement[]>;
  grid_cols?: number;
  grid_row_height?: number;
}

export interface DashboardRendererProps {
  appKey: string;
  widgetRegistry: Record<string, React.ComponentType<{ widgetKey: string; props?: Record<string, unknown> }>>;
  customHeader?: React.ReactNode;
}

export const DashboardRenderer: React.FC<DashboardRendererProps> = ({
  appKey,
  widgetRegistry,
  customHeader,
}) => {
  const [layout, setLayout] = useState<DashboardLayoutData | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    httpOpzhub
      .get<DashboardLayoutData>(`/dashboard-layout/my-layout?app_key=${encodeURIComponent(appKey)}`)
      .then((data) => {
        if (mounted) {
          setLayout(data);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (mounted) {
          setError(err?.message || "Failed to load dashboard layout");
          setLoading(false);
        }
      });

    return () => {
      mounted = false;
    };
  }, [appKey]);

  if (loading) {
    return (
      <div style={{ padding: "32px", textAlign: "center", color: "var(--opz-color-muted, #9aa3af)" }}>
        <div style={{ fontSize: "16px", marginBottom: "8px" }}>Loading dashboard widgets...</div>
        <div style={{ fontSize: "12px" }}>Connecting to real-time analytics engine</div>
      </div>
    );
  }

  if (error || !layout) {
    return (
      <div style={{ padding: "24px", background: "rgba(239, 68, 68, 0.1)", borderRadius: "8px", border: "1px solid #ef4444" }}>
        <h4 style={{ color: "#ef4444", margin: "0 0 8px 0" }}>Unable to load dashboard layout</h4>
        <p style={{ margin: 0, fontSize: "14px" }}>{error || "No layout configuration found"}</p>
      </div>
    );
  }

  const headerWidgets = layout.zones?.header_bar || [];
  const mainWidgets = layout.zones?.main_grid || [];
  const quickActionWidgets = layout.zones?.quick_actions || [];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
      {customHeader}

      {/* Header bar KPI zone */}
      {headerWidgets.length > 0 && (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: `repeat(${headerWidgets.length}, minmax(0, 1fr))`,
            gap: "16px",
          }}
        >
          {headerWidgets.map((w, idx) => {
            const Comp = widgetRegistry[w.widget_key];
            return (
              <div key={w.widget_key + idx} style={{ minWidth: 0 }}>
                {Comp ? (
                  <Comp widgetKey={w.widget_key} props={w.props} />
                ) : (
                  <div style={{ padding: "16px", background: "var(--opz-color-surface, #171a21)", borderRadius: "8px", border: "1px solid var(--opz-color-border, #2a2f3a)" }}>
                    {w.widget_key}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Quick Actions zone */}
      {quickActionWidgets.length > 0 && (
        <div style={{ display: "flex", gap: "12px", flexWrap: "wrap" }}>
          {quickActionWidgets.map((w, idx) => {
            const Comp = widgetRegistry[w.widget_key];
            return Comp ? <Comp key={w.widget_key + idx} widgetKey={w.widget_key} props={w.props} /> : null;
          })}
        </div>
      )}

      {/* Main Grid zone */}
      {mainWidgets.length > 0 && (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(12, 1fr)",
            gap: "20px",
          }}
        >
          {mainWidgets.map((w, idx) => {
            const span = Math.min(12, Math.max(2, w.w || 6));
            const Comp = widgetRegistry[w.widget_key];
            return (
              <div
                key={w.widget_key + idx}
                style={{
                  gridColumn: `span ${span}`,
                  minHeight: `${(w.h || 3) * 75}px`,
                  background: "var(--opz-color-surface, #171a21)",
                  borderRadius: "10px",
                  border: "1px solid var(--opz-color-border, #2a2f3a)",
                  padding: "16px",
                  display: "flex",
                  flexDirection: "column",
                  overflow: "hidden",
                }}
              >
                {Comp ? (
                  <Comp widgetKey={w.widget_key} props={w.props} />
                ) : (
                  <div style={{ color: "var(--opz-color-muted, #9aa3af)" }}>
                    Widget [{w.widget_key}] not found in registry
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
