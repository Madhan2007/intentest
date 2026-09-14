/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Campaign details, performance metrics, and channel configuration.
 */
import React, { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { marketApi } from "../api/marketApi";
import { CAMPAIGN_STATUSES } from "../constants/manageMyMarketConstants";

export const CampaignDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [campaign, setCampaign] = useState<any>(null);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    marketApi.listCampaigns({ limit: 100 })
      .then((list) => {
        const found = (Array.isArray(list) ? list : []).find((c) => c.id === id);
        setCampaign(found || null);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  }, [id]);

  const handleStatusChange = (newStatus: string) => {
    if (!id) return;
    marketApi.updateCampaign(id, { status: newStatus }).then(() => {
      setCampaign((prev: any) => (prev ? { ...prev, status: newStatus } : prev));
    });
  };

  if (loading || !campaign) {
    return <div style={{ padding: "32px", textAlign: "center", color: "#94a3b8" }}>Loading campaign...</div>;
  }

  const sMeta = CAMPAIGN_STATUSES.find((s) => s.value === campaign.status) || { label: campaign.status, color: "#38bdf8" };
  const budget = Number(campaign.budget) || 0;
  const spent = Number(campaign.spent) || 0;

  return (
    <div style={{ padding: "24px", maxWidth: "1400px", margin: "0 auto" }}>
      <div style={{ marginBottom: "16px" }}>
        <Link to="/market/campaigns" style={{ color: "#38bdf8", textDecoration: "none", fontSize: "13px" }}>
          &larr; Back to Campaigns
        </Link>
      </div>

      <div style={{ background: "var(--opz-color-surface, #171a21)", padding: "24px", borderRadius: "10px", border: "1px solid var(--opz-color-border, #2a2f3a)", marginBottom: "24px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "20px" }}>
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "8px" }}>
              <span style={{ fontSize: "13px", fontWeight: 700, color: "#10b981", background: "#10b98122", padding: "2px 8px", borderRadius: "4px" }}>
                {campaign.code}
              </span>
              <h1 style={{ margin: 0, fontSize: "22px", color: "#fff" }}>{campaign.name}</h1>
              <span style={{ padding: "3px 8px", borderRadius: "4px", fontSize: "11px", fontWeight: 600, background: `${sMeta.color}22`, color: sMeta.color }}>
                {sMeta.label}
              </span>
            </div>
            <div style={{ fontSize: "13px", color: "#94a3b8" }}>
              Timeline: {campaign.start_date ? new Date(campaign.start_date).toLocaleDateString() : "-"} to {campaign.end_date ? new Date(campaign.end_date).toLocaleDateString() : "-"}
            </div>
          </div>

          <div style={{ display: "flex", gap: "8px" }}>
            {campaign.status !== "ACTIVE" && (
              <button
                onClick={() => handleStatusChange("ACTIVE")}
                style={{ padding: "8px 16px", background: "#10b981", color: "#fff", border: "none", borderRadius: "6px", fontSize: "12px", fontWeight: 600, cursor: "pointer" }}
              >
                Launch Campaign
              </button>
            )}
            {campaign.status === "ACTIVE" && (
              <button
                onClick={() => handleStatusChange("PAUSED")}
                style={{ padding: "8px 16px", background: "#f59e0b", color: "#fff", border: "none", borderRadius: "6px", fontSize: "12px", fontWeight: 600, cursor: "pointer" }}
              >
                Pause Campaign
              </button>
            )}
          </div>
        </div>

        {/* KPI Strip */}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "16px", borderTop: "1px solid #2a2f3a", paddingTop: "20px" }}>
          <div style={{ background: "#0f1115", padding: "14px", borderRadius: "6px", border: "1px solid #2a2f3a" }}>
            <div style={{ fontSize: "11px", color: "#64748b" }}>TOTAL BUDGET</div>
            <div style={{ fontSize: "22px", fontWeight: 700, color: "#38bdf8" }}>${budget.toLocaleString()}</div>
          </div>
          <div style={{ background: "#0f1115", padding: "14px", borderRadius: "6px", border: "1px solid #2a2f3a" }}>
            <div style={{ fontSize: "11px", color: "#64748b" }}>CURRENT SPEND</div>
            <div style={{ fontSize: "22px", fontWeight: 700, color: "#ec4899" }}>${spent.toLocaleString()}</div>
          </div>
          <div style={{ background: "#0f1115", padding: "14px", borderRadius: "6px", border: "1px solid #2a2f3a" }}>
            <div style={{ fontSize: "11px", color: "#64748b" }}>REMAINING BUDGET</div>
            <div style={{ fontSize: "22px", fontWeight: 700, color: "#10b981" }}>${Math.max(0, budget - spent).toLocaleString()}</div>
          </div>
          <div style={{ background: "#0f1115", padding: "14px", borderRadius: "6px", border: "1px solid #2a2f3a" }}>
            <div style={{ fontSize: "11px", color: "#64748b" }}>SPEND UTILIZATION</div>
            <div style={{ fontSize: "22px", fontWeight: 700, color: "#a855f7" }}>
              {budget > 0 ? Math.round((spent / budget) * 100) : 0}%
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
