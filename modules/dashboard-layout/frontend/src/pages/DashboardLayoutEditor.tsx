/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Interactive drag/position layout editor for dashboard administrators.
 */
import React, { useEffect, useState } from "react";
import { httpOpzhub } from "@kernel/api/http-client";

interface CatalogItem {
  widget_key: string;
  widget_name: string;
  description: string;
  default_zone: string;
  default_w: number;
  default_h: number;
}

interface TemplateItem {
  template_key: string;
  template_name: string;
  description: string;
}

export const DashboardLayoutEditor: React.FC = () => {
  const [appKey, setAppKey] = useState<string>("manage-my-market");
  const [roleKey, setRoleKey] = useState<string>("market_admin");
  const [catalog, setCatalog] = useState<CatalogItem[]>([]);
  const [templates, setTemplates] = useState<TemplateItem[]>([]);
  const [selectedTemplate, setSelectedTemplate] = useState<string>("");
  const [layoutData, setLayoutData] = useState<any>({
    zones: {
      header_bar: [],
      main_grid: [],
      quick_actions: [],
    },
    grid_cols: 12,
  });
  const [statusMsg, setStatusMsg] = useState<string>("");

  useEffect(() => {
    httpOpzhub.get<CatalogItem[]>(`/dashboard-layout/catalog?app_key=${appKey}`).then(setCatalog).catch(() => {});
    httpOpzhub.get<TemplateItem[]>(`/dashboard-layout/templates?app_key=${appKey}`).then(setTemplates).catch(() => {});
    loadRoleLayout();
  }, [appKey, roleKey]);

  const loadRoleLayout = () => {
    httpOpzhub.get<any>(`/dashboard-layout/layout?app_key=${appKey}&role_key=${roleKey}`)
      .then((data) => {
        if (data && data.zones) {
          setLayoutData(data);
        } else {
          setLayoutData({ zones: { header_bar: [], main_grid: [], quick_actions: [] }, grid_cols: 12 });
        }
      })
      .catch(() => {});
  };

  const handleApplyTemplate = () => {
    if (!selectedTemplate) return;
    setStatusMsg("Applying template...");
    httpOpzhub.post("/dashboard-layout/layout/apply-template", {
      app_key: appKey,
      role_key: roleKey,
      template_key: selectedTemplate,
    })
      .then(() => {
        setStatusMsg("Template applied successfully!");
        loadRoleLayout();
        setTimeout(() => setStatusMsg(""), 3000);
      })
      .catch((err: any) => setStatusMsg("Failed: " + (err?.message || String(err))));
  };

  const handleSaveLayout = () => {
    setStatusMsg("Saving layout...");
    httpOpzhub.put("/dashboard-layout/layout", {
      app_key: appKey,
      role_key: roleKey,
      template_key: selectedTemplate,
      layout_data: layoutData,
    })
      .then(() => {
        setStatusMsg("Layout saved successfully!");
        setTimeout(() => setStatusMsg(""), 3000);
      })
      .catch((err: any) => setStatusMsg("Failed: " + (err?.message || String(err))));
  };

  const addWidgetToZone = (item: CatalogItem, zoneKey: string) => {
    const next = { ...layoutData };
    if (!next.zones[zoneKey]) next.zones[zoneKey] = [];
    next.zones[zoneKey].push({
      widget_key: item.widget_key,
      x: 0,
      y: 0,
      w: item.default_w || 6,
      h: item.default_h || 3,
      props: {},
    });
    setLayoutData(next);
  };

  const removeWidget = (zoneKey: string, index: number) => {
    const next = { ...layoutData };
    next.zones[zoneKey].splice(index, 1);
    setLayoutData(next);
  };

  return (
    <div style={{ padding: "24px", maxWidth: "1400px", margin: "0 auto" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "24px" }}>
        <div>
          <h2 style={{ margin: "0 0 4px 0", color: "#fff" }}>Dashboard Layout Editor</h2>
          <p style={{ margin: 0, color: "var(--opz-color-muted, #9aa3af)", fontSize: "14px" }}>
            Configure and position dashboard widgets per application and role.
          </p>
        </div>
        <div style={{ display: "flex", gap: "12px", alignItems: "center" }}>
          {statusMsg && (
            <span style={{ color: "#38bdf8", fontSize: "13px", fontWeight: 500 }}>{statusMsg}</span>
          )}
          <button
            onClick={handleSaveLayout}
            style={{
              padding: "10px 20px",
              background: "#3b82f6",
              color: "#fff",
              border: "none",
              borderRadius: "6px",
              fontWeight: 600,
              cursor: "pointer",
            }}
          >
            Save Role Layout
          </button>
        </div>
      </div>

      {/* Selectors Bar */}
      <div
        style={{
          display: "flex",
          gap: "16px",
          background: "var(--opz-color-surface, #171a21)",
          padding: "16px",
          borderRadius: "8px",
          border: "1px solid var(--opz-color-border, #2a2f3a)",
          marginBottom: "24px",
          alignItems: "center",
        }}
      >
        <div>
          <label style={{ fontSize: "12px", color: "var(--opz-color-muted, #9aa3af)", display: "block", marginBottom: "4px" }}>Application</label>
          <select
            value={appKey}
            onChange={(e) => setAppKey(e.target.value)}
            style={{ background: "#0f1115", color: "#fff", border: "1px solid #2a2f3a", borderRadius: "6px", padding: "6px 12px" }}
          >
            <option value="manage-my-market">Manage My Market</option>
          </select>
        </div>

        <div>
          <label style={{ fontSize: "12px", color: "var(--opz-color-muted, #9aa3af)", display: "block", marginBottom: "4px" }}>Target Role</label>
          <select
            value={roleKey}
            onChange={(e) => setRoleKey(e.target.value)}
            style={{ background: "#0f1115", color: "#fff", border: "1px solid #2a2f3a", borderRadius: "6px", padding: "6px 12px" }}
          >
            <option value="market_admin">Marketing Admin</option>
            <option value="market_manager">Campaign Manager</option>
            <option value="market_telecaller">Telemarketing Agent</option>
            <option value="market_field_agent">Field Agent</option>
          </select>
        </div>

        <div style={{ marginLeft: "auto", display: "flex", gap: "8px", alignItems: "flex-end" }}>
          <div>
            <label style={{ fontSize: "12px", color: "var(--opz-color-muted, #9aa3af)", display: "block", marginBottom: "4px" }}>Preset Template</label>
            <select
              value={selectedTemplate}
              onChange={(e) => setSelectedTemplate(e.target.value)}
              style={{ background: "#0f1115", color: "#fff", border: "1px solid #2a2f3a", borderRadius: "6px", padding: "6px 12px" }}
            >
              <option value="">-- Choose Template --</option>
              {templates.map((t) => (
                <option key={t.template_key} value={t.template_key}>{t.template_name}</option>
              ))}
            </select>
          </div>
          <button
            onClick={handleApplyTemplate}
            disabled={!selectedTemplate}
            style={{
              padding: "7px 14px",
              background: "#4b5563",
              color: "#fff",
              border: "none",
              borderRadius: "6px",
              cursor: selectedTemplate ? "pointer" : "not-allowed",
              opacity: selectedTemplate ? 1 : 0.5,
            }}
          >
            Apply
          </button>
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "300px 1fr", gap: "24px" }}>
        {/* Widget Catalog Palette */}
        <div
          style={{
            background: "var(--opz-color-surface, #171a21)",
            padding: "16px",
            borderRadius: "8px",
            border: "1px solid var(--opz-color-border, #2a2f3a)",
            height: "fit-content",
          }}
        >
          <h3 style={{ margin: "0 0 16px 0", fontSize: "15px", color: "#fff" }}>Available Widgets</h3>
          <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
            {catalog.map((item) => (
              <div
                key={item.widget_key}
                style={{
                  background: "#0f1115",
                  border: "1px solid #2a2f3a",
                  borderRadius: "6px",
                  padding: "10px",
                }}
              >
                <div style={{ fontWeight: 600, fontSize: "13px", color: "#e2e8f0" }}>{item.widget_name}</div>
                <div style={{ fontSize: "11px", color: "var(--opz-color-muted, #9aa3af)", margin: "4px 0 8px 0" }}>
                  {item.description}
                </div>
                <div style={{ display: "flex", gap: "6px" }}>
                  <button
                    onClick={() => addWidgetToZone(item, "header_bar")}
                    style={{ fontSize: "11px", padding: "3px 6px", background: "#2563eb", color: "#fff", border: "none", borderRadius: "4px", cursor: "pointer" }}
                  >
                    + Header
                  </button>
                  <button
                    onClick={() => addWidgetToZone(item, "main_grid")}
                    style={{ fontSize: "11px", padding: "3px 6px", background: "#059669", color: "#fff", border: "none", borderRadius: "4px", cursor: "pointer" }}
                  >
                    + Main Grid
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Zones Preview & Configuration */}
        <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
          {/* Header Bar Zone */}
          <div
            style={{
              background: "var(--opz-color-surface, #171a21)",
              padding: "16px",
              borderRadius: "8px",
              border: "1px dashed #3b82f6",
            }}
          >
            <div style={{ fontSize: "12px", fontWeight: 600, color: "#38bdf8", textTransform: "uppercase", marginBottom: "12px" }}>
              Header Bar Zone (KPI Pills)
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "12px" }}>
              {(layoutData.zones?.header_bar || []).map((w: any, idx: number) => (
                <div
                  key={idx}
                  style={{
                    background: "#0f1115",
                    padding: "12px",
                    borderRadius: "6px",
                    border: "1px solid #2a2f3a",
                    position: "relative",
                  }}
                >
                  <div style={{ fontSize: "13px", fontWeight: 600, color: "#fff" }}>{w.widget_key}</div>
                  <button
                    onClick={() => removeWidget("header_bar", idx)}
                    style={{
                      position: "absolute",
                      top: "6px",
                      right: "6px",
                      background: "transparent",
                      border: "none",
                      color: "#ef4444",
                      cursor: "pointer",
                      fontSize: "14px",
                    }}
                  >
                    x
                  </button>
                </div>
              ))}
              {(layoutData.zones?.header_bar || []).length === 0 && (
                <div style={{ gridColumn: "span 4", color: "var(--opz-color-muted, #9aa3af)", fontSize: "13px", textAlign: "center", padding: "16px" }}>
                  No widgets in header bar zone. Click "+ Header" on any widget in palette.
                </div>
              )}
            </div>
          </div>

          {/* Main Grid Zone */}
          <div
            style={{
              background: "var(--opz-color-surface, #171a21)",
              padding: "16px",
              borderRadius: "8px",
              border: "1px dashed #10b981",
            }}
          >
            <div style={{ fontSize: "12px", fontWeight: 600, color: "#34d399", textTransform: "uppercase", marginBottom: "12px" }}>
              Main Grid Zone (12-column grid)
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(12, 1fr)", gap: "16px" }}>
              {(layoutData.zones?.main_grid || []).map((w: any, idx: number) => {
                const span = w.w || 6;
                return (
                  <div
                    key={idx}
                    style={{
                      gridColumn: `span ${span}`,
                      background: "#0f1115",
                      padding: "16px",
                      borderRadius: "6px",
                      border: "1px solid #2a2f3a",
                      position: "relative",
                      minHeight: "120px",
                    }}
                  >
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "12px" }}>
                      <span style={{ fontSize: "14px", fontWeight: 600, color: "#fff" }}>{w.widget_key}</span>
                      <button
                        onClick={() => removeWidget("main_grid", idx)}
                        style={{
                          background: "transparent",
                          border: "none",
                          color: "#ef4444",
                          cursor: "pointer",
                          fontSize: "14px",
                        }}
                      >
                        x
                      </button>
                    </div>
                    <div style={{ display: "flex", gap: "12px", alignItems: "center" }}>
                      <label style={{ fontSize: "11px", color: "#9aa3af" }}>Width (columns):</label>
                      <select
                        value={w.w || 6}
                        onChange={(e) => {
                          const next = { ...layoutData };
                          next.zones.main_grid[idx].w = parseInt(e.target.value, 10);
                          setLayoutData(next);
                        }}
                        style={{ background: "#171a21", color: "#fff", border: "1px solid #2a2f3a", borderRadius: "4px", padding: "2px 6px", fontSize: "12px" }}
                      >
                        <option value={4}>4 cols (1/3)</option>
                        <option value={6}>6 cols (1/2)</option>
                        <option value={8}>8 cols (2/3)</option>
                        <option value={12}>12 cols (Full)</option>
                      </select>
                    </div>
                  </div>
                );
              })}
              {(layoutData.zones?.main_grid || []).length === 0 && (
                <div style={{ gridColumn: "span 12", color: "var(--opz-color-muted, #9aa3af)", fontSize: "13px", textAlign: "center", padding: "24px" }}>
                  No widgets in main grid. Click "+ Main Grid" on any widget in palette.
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
