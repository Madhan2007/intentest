/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Interactive SVG bar chart for Campaign Budget vs Spent.
 */
import React from "react";

interface CampaignData {
  name: string;
  budget: number;
  spent: number;
  status: string;
}

interface CampaignROIChartProps {
  campaigns: CampaignData[];
}

export const CampaignROIChart: React.FC<CampaignROIChartProps> = ({ campaigns }) => {
  const items = (campaigns || []).slice(0, 6);
  let maxVal = 1000;

  for (const c of items) {
    const b = Number(c.budget) || 0;
    const s = Number(c.spent) || 0;
    if (b > maxVal) maxVal = b;
    if (s > maxVal) maxVal = s;
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "14px", width: "100%" }}>
      <div style={{ display: "flex", gap: "16px", fontSize: "11px", color: "var(--opz-color-muted, #9aa3af)", marginBottom: "4px" }}>
        <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
          <span style={{ width: "10px", height: "10px", background: "#38bdf8", borderRadius: "2px" }} />
          <span>Budget ($)</span>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
          <span style={{ width: "10px", height: "10px", background: "#ec4899", borderRadius: "2px" }} />
          <span>Spent ($)</span>
        </div>
      </div>

      {items.length === 0 ? (
        <div style={{ color: "var(--opz-color-muted, #9aa3af)", fontSize: "13px", padding: "16px 0" }}>
          No campaigns found.
        </div>
      ) : (
        items.map((c, idx) => {
          const b = Number(c.budget) || 0;
          const s = Number(c.spent) || 0;
          const bPct = Math.round((b / maxVal) * 100);
          const sPct = Math.round((s / maxVal) * 100);

          return (
            <div key={idx} style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: "12px", color: "#e2e8f0" }}>
                <span style={{ fontWeight: 600, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", maxWidth: "200px" }}>
                  {c.name}
                </span>
                <span style={{ fontSize: "11px", color: "var(--opz-color-muted, #9aa3af)" }}>
                  ${s.toLocaleString()} / ${b.toLocaleString()}
                </span>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: "3px" }}>
                {/* Budget Bar */}
                <div style={{ height: "6px", background: "#1f242e", borderRadius: "3px", overflow: "hidden" }}>
                  <div style={{ width: `${bPct}%`, height: "100%", background: "#38bdf8", borderRadius: "3px" }} />
                </div>
                {/* Spent Bar */}
                <div style={{ height: "6px", background: "#1f242e", borderRadius: "3px", overflow: "hidden" }}>
                  <div style={{ width: `${sPct}%`, height: "100%", background: "#ec4899", borderRadius: "3px" }} />
                </div>
              </div>
            </div>
          );
        })
      )}
    </div>
  );
};
