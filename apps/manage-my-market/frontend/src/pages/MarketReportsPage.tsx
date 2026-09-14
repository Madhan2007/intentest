/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Analytics and reporting center for Manage My Market.
 */
import React, { useEffect, useState } from "react";
import { marketApi } from "../api/marketApi";

const REPORT_TABS = [
  { id: "lead_summary", label: "Lead Pipeline Summary" },
  { id: "conversion_rate", label: "Lead Conversion Rate" },
  { id: "campaign_perf", label: "Campaign Performance" },
  { id: "channel_spend", label: "Channel Spend Breakdown" },
  { id: "telecalling", label: "Telecalling Agent Stats" },
  { id: "referrer_leaderboard", label: "Referral Leaderboard" },
  { id: "followup_compliance", label: "Follow-up Compliance" },
  { id: "journey_funnel", label: "Journey Funnel" },
];

export const MarketReportsPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<string>("lead_summary");
  const [data, setData] = useState<any[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    setLoading(true);
    let fetcher: Promise<any[]>;

    switch (activeTab) {
      case "lead_summary":
        fetcher = marketApi.getLeadSummaryReport();
        break;
      case "conversion_rate":
        fetcher = marketApi.getLeadConversionRateReport();
        break;
      case "campaign_perf":
        fetcher = marketApi.getCampaignPerformanceReport();
        break;
      case "channel_spend":
        fetcher = marketApi.getChannelSpendReport();
        break;
      case "telecalling":
        fetcher = marketApi.getTelecallingReport();
        break;
      case "referrer_leaderboard":
        fetcher = marketApi.getReferrerLeaderboardReport();
        break;
      case "followup_compliance":
        fetcher = marketApi.getFollowupComplianceReport();
        break;
      case "journey_funnel":
        fetcher = marketApi.getJourneyFunnelReport();
        break;
      default:
        fetcher = Promise.resolve([]);
    }

    fetcher
      .then((res) => {
        setData(Array.isArray(res) ? res : []);
        setLoading(false);
      })
      .catch(() => {
        setData([]);
        setLoading(false);
      });
  }, [activeTab]);

  return (
    <div style={{ padding: "24px", maxWidth: "1500px", margin: "0 auto" }}>
      <div style={{ marginBottom: "24px" }}>
        <h1 style={{ margin: "0 0 4px 0", fontSize: "24px", color: "#fff", fontWeight: 700 }}>
          Marketing Intelligence Reports
        </h1>
        <p style={{ margin: 0, fontSize: "13px", color: "var(--opz-color-muted, #9aa3af)" }}>
          Real-time aggregated SQL reporting across leads, campaigns, telecalling, and partner channels.
        </p>
      </div>

      {/* Tabs */}
      <div style={{ display: "flex", gap: "8px", borderBottom: "1px solid #2a2f3a", marginBottom: "24px", overflowX: "auto" }}>
        {REPORT_TABS.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            style={{
              padding: "10px 16px",
              background: "transparent",
              border: "none",
              borderBottom: activeTab === tab.id ? "2px solid #38bdf8" : "2px solid transparent",
              color: activeTab === tab.id ? "#38bdf8" : "#94a3b8",
              fontWeight: 600,
              fontSize: "13px",
              cursor: "pointer",
              whiteSpace: "nowrap",
            }}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Report Content Table */}
      <div style={{ background: "var(--opz-color-surface, #171a21)", borderRadius: "8px", border: "1px solid var(--opz-color-border, #2a2f3a)", overflow: "hidden" }}>
        {loading ? (
          <div style={{ padding: "40px", textAlign: "center", color: "#94a3b8" }}>Loading report data...</div>
        ) : data.length === 0 ? (
          <div style={{ padding: "40px", textAlign: "center", color: "#64748b" }}>No data available for this report.</div>
        ) : (
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "13px" }}>
            <thead>
              <tr style={{ background: "#0f1115", borderBottom: "1px solid #2a2f3a", color: "#94a3b8", textAlign: "left" }}>
                {Object.keys(data[0]).map((col) => (
                  <th key={col} style={{ padding: "12px 16px", textTransform: "capitalize" }}>
                    {col.replace(/_/g, " ")}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.map((row, idx) => (
                <tr key={idx} style={{ borderBottom: "1px solid #1f242e" }}>
                  {Object.entries(row).map(([key, val], cellIdx) => (
                    <td key={cellIdx} style={{ padding: "12px 16px", color: cellIdx === 0 ? "#fff" : "#cbd5e1", fontWeight: cellIdx === 0 ? 600 : 400 }}>
                      {val !== null && val !== undefined ? String(val) : "-"}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
};
