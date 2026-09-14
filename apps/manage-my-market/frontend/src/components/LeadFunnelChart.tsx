/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Interactive SVG funnel visualization for lead stages.
 */
import React from "react";

interface FunnelStage {
  status: string;
  count: number;
  total_estimated_value?: number;
}

interface LeadFunnelChartProps {
  data: FunnelStage[];
}

const STAGE_CONFIG: Record<string, { label: string; color: string }> = {
  NEW: { label: "New", color: "#38bdf8" },
  CONTACTED: { label: "Contacted", color: "#60a5fa" },
  QUALIFIED: { label: "Qualified", color: "#818cf8" },
  PROPOSAL: { label: "Proposal", color: "#a855f7" },
  NEGOTIATION: { label: "Negotiation", color: "#f59e0b" },
  WON: { label: "Won", color: "#10b981" },
  LOST: { label: "Lost", color: "#ef4444" },
  DISQUALIFIED: { label: "Disqualified", color: "#6b7280" },
};

export const LeadFunnelChart: React.FC<LeadFunnelChartProps> = ({ data }) => {
  // Aggregate count by status
  const countsByStatus: Record<string, number> = {};
  let maxCount = 1;

  for (const item of data || []) {
    const s = item.status || "NEW";
    countsByStatus[s] = (countsByStatus[s] || 0) + (Number(item.count) || 0);
  }

  const stages = ["NEW", "CONTACTED", "QUALIFIED", "PROPOSAL", "NEGOTIATION", "WON"];
  stages.forEach((s) => {
    if ((countsByStatus[s] || 0) > maxCount) {
      maxCount = countsByStatus[s];
    }
  });

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "10px", width: "100%" }}>
      {stages.map((stageKey) => {
        const count = countsByStatus[stageKey] || 0;
        const config = STAGE_CONFIG[stageKey] || { label: stageKey, color: "#38bdf8" };
        const pct = Math.max(8, Math.round((count / maxCount) * 100));

        return (
          <div key={stageKey} style={{ display: "flex", alignItems: "center", gap: "12px" }}>
            <div style={{ width: "90px", fontSize: "12px", fontWeight: 600, color: "#cbd5e1" }}>
              {config.label}
            </div>
            <div style={{ flex: 1, background: "#1f242e", borderRadius: "6px", overflow: "hidden", height: "24px" }}>
              <div
                style={{
                  width: `${pct}%`,
                  height: "100%",
                  background: `linear-gradient(90deg, ${config.color}dd, ${config.color})`,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "flex-end",
                  paddingRight: "8px",
                  transition: "width 0.5s cubic-bezier(0.4, 0, 0.2, 1)",
                  boxShadow: `0 0 10px ${config.color}33`,
                }}
              >
                <span style={{ fontSize: "11px", fontWeight: 700, color: "#ffffff", textShadow: "0 1px 2px rgba(0,0,0,0.6)" }}>
                  {count}
                </span>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
};
