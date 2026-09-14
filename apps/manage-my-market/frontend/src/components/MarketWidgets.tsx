/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Widget components for Manage My Market dashboard layout.
 */
import React, { useEffect, useState } from "react";
import { marketApi } from "../api/marketApi";
import { LeadFunnelChart } from "./LeadFunnelChart";
import { CampaignROIChart } from "./CampaignROIChart";

export const MktKpiLeadsWidget: React.FC = () => {
  const [data, setData] = useState<any>(null);
  useEffect(() => {
    marketApi.getWidgetData("mkt-kpi-leads").then(setData).catch(() => {});
  }, []);

  return (
    <div style={{ background: "linear-gradient(135deg, #1e293b, #0f172a)", padding: "16px", borderRadius: "8px", border: "1px solid #334155" }}>
      <div style={{ fontSize: "12px", color: "#94a3b8", fontWeight: 500 }}>TOTAL LEADS</div>
      <div style={{ fontSize: "28px", fontWeight: 700, color: "#38bdf8", margin: "4px 0" }}>
        {data ? Number(data.total_leads || 0).toLocaleString() : "..."}
      </div>
      <div style={{ fontSize: "11px", color: "#64748b" }}>
        <span style={{ color: "#34d399", fontWeight: 600 }}>+{data ? data.new_leads_today || 0 : 0}</span> new today
      </div>
    </div>
  );
};

export const MktKpiConversionWidget: React.FC = () => {
  const [data, setData] = useState<any>(null);
  useEffect(() => {
    marketApi.getWidgetData("mkt-kpi-conversion").then(setData).catch(() => {});
  }, []);

  return (
    <div style={{ background: "linear-gradient(135deg, #1e293b, #0f172a)", padding: "16px", borderRadius: "8px", border: "1px solid #334155" }}>
      <div style={{ fontSize: "12px", color: "#94a3b8", fontWeight: 500 }}>CONVERSION RATE</div>
      <div style={{ fontSize: "28px", fontWeight: 700, color: "#10b981", margin: "4px 0" }}>
        {data ? `${data.conversion_rate_pct || 0}%` : "..."}
      </div>
      <div style={{ fontSize: "11px", color: "#64748b" }}>
        <span style={{ color: "#38bdf8", fontWeight: 600 }}>{data ? data.won_leads || 0 : 0}</span> won deals
      </div>
    </div>
  );
};

export const MktKpiCampaignsWidget: React.FC = () => {
  const [data, setData] = useState<any>(null);
  useEffect(() => {
    marketApi.getWidgetData("mkt-kpi-campaigns").then(setData).catch(() => {});
  }, []);

  return (
    <div style={{ background: "linear-gradient(135deg, #1e293b, #0f172a)", padding: "16px", borderRadius: "8px", border: "1px solid #334155" }}>
      <div style={{ fontSize: "12px", color: "#94a3b8", fontWeight: 500 }}>ACTIVE CAMPAIGNS</div>
      <div style={{ fontSize: "28px", fontWeight: 700, color: "#a855f7", margin: "4px 0" }}>
        {data ? Number(data.active_campaigns || 0).toLocaleString() : "..."}
      </div>
      <div style={{ fontSize: "11px", color: "#64748b" }}>
        ${data ? Number(data.active_campaigns_budget || 0).toLocaleString() : 0} budget
      </div>
    </div>
  );
};

export const MktKpiTelecallingWidget: React.FC = () => {
  const [data, setData] = useState<any>(null);
  useEffect(() => {
    marketApi.getWidgetData("mkt-kpi-telecalling").then(setData).catch(() => {});
  }, []);

  return (
    <div style={{ background: "linear-gradient(135deg, #1e293b, #0f172a)", padding: "16px", borderRadius: "8px", border: "1px solid #334155" }}>
      <div style={{ fontSize: "12px", color: "#94a3b8", fontWeight: 500 }}>CALLS LOGGED TODAY</div>
      <div style={{ fontSize: "28px", fontWeight: 700, color: "#f59e0b", margin: "4px 0" }}>
        {data ? Number(data.calls_today || 0).toLocaleString() : "..."}
      </div>
      <div style={{ fontSize: "11px", color: "#64748b" }}>
        <span style={{ color: "#f59e0b", fontWeight: 600 }}>{data ? data.pending_queue_items || 0 : 0}</span> in queue
      </div>
    </div>
  );
};

export const MktLeadFunnelWidget: React.FC = () => {
  const [data, setData] = useState<any[]>([]);
  useEffect(() => {
    marketApi.getWidgetData("mkt-lead-funnel").then((res) => setData(Array.isArray(res) ? res : [])).catch(() => {});
  }, []);

  return (
    <div>
      <div style={{ fontSize: "14px", fontWeight: 600, color: "#fff", marginBottom: "16px" }}>Lead Pipeline Funnel</div>
      <LeadFunnelChart data={data} />
    </div>
  );
};

export const MktCampaignRoiWidget: React.FC = () => {
  const [data, setData] = useState<any[]>([]);
  useEffect(() => {
    marketApi.getWidgetData("mkt-campaign-roi").then((res) => setData(Array.isArray(res) ? res : [])).catch(() => {});
  }, []);

  return (
    <div>
      <div style={{ fontSize: "14px", fontWeight: 600, color: "#fff", marginBottom: "16px" }}>Campaign Spend vs Budget</div>
      <CampaignROIChart campaigns={data} />
    </div>
  );
};

export const MktRecentLeadsWidget: React.FC = () => {
  const [leads, setLeads] = useState<any[]>([]);
  useEffect(() => {
    marketApi.getWidgetData("mkt-recent-leads").then((res) => setLeads(Array.isArray(res) ? res : [])).catch(() => {});
  }, []);

  return (
    <div>
      <div style={{ fontSize: "14px", fontWeight: 600, color: "#fff", marginBottom: "12px" }}>Recent Inbound Leads</div>
      <div style={{ overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "12px" }}>
          <thead>
            <tr style={{ borderBottom: "1px solid #2a2f3a", color: "#94a3b8", textAlign: "left" }}>
              <th style={{ padding: "8px" }}>Code</th>
              <th style={{ padding: "8px" }}>Name</th>
              <th style={{ padding: "8px" }}>Source</th>
              <th style={{ padding: "8px" }}>Status</th>
            </tr>
          </thead>
          <tbody>
            {leads.length === 0 ? (
              <tr><td colSpan={4} style={{ padding: "16px", textAlign: "center", color: "#64748b" }}>No recent leads</td></tr>
            ) : (
              leads.slice(0, 5).map((l) => (
                <tr key={l.id} style={{ borderBottom: "1px solid #1f242e" }}>
                  <td style={{ padding: "8px", color: "#38bdf8", fontWeight: 600 }}>{l.lead_code}</td>
                  <td style={{ padding: "8px", color: "#e2e8f0" }}>{l.display_name}</td>
                  <td style={{ padding: "8px", color: "#94a3b8" }}>{l.lead_source_type}</td>
                  <td style={{ padding: "8px" }}>
                    <span style={{ padding: "2px 6px", borderRadius: "4px", fontSize: "10px", fontWeight: 600, background: "#1e293b", color: "#38bdf8" }}>
                      {l.status}
                    </span>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export const MktCallQueueStatusWidget: React.FC = () => {
  const [queues, setQueues] = useState<any[]>([]);
  useEffect(() => {
    marketApi.getWidgetData("mkt-call-queue-status").then((res) => setQueues(Array.isArray(res) ? res : [])).catch(() => {});
  }, []);

  return (
    <div>
      <div style={{ fontSize: "14px", fontWeight: 600, color: "#fff", marginBottom: "12px" }}>Active Call Queues</div>
      <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
        {queues.length === 0 ? (
          <div style={{ color: "#64748b", fontSize: "12px", padding: "16px 0" }}>No active queues</div>
        ) : (
          queues.slice(0, 4).map((q) => (
            <div key={q.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", background: "#0f1115", padding: "8px 12px", borderRadius: "6px", border: "1px solid #2a2f3a" }}>
              <div>
                <div style={{ fontSize: "12px", fontWeight: 600, color: "#e2e8f0" }}>{q.name}</div>
                <div style={{ fontSize: "10px", color: "#64748b" }}>Strategy: {q.distribution_strategy}</div>
              </div>
              <span style={{ fontSize: "10px", padding: "2px 6px", background: "#10b98122", color: "#10b981", borderRadius: "4px", fontWeight: 600 }}>
                {q.status}
              </span>
            </div>
          ))
        )}
      </div>
    </div>
  );
};

export const MARKET_WIDGET_REGISTRY: Record<string, React.ComponentType<any>> = {
  "mkt-kpi-leads": MktKpiLeadsWidget,
  "mkt-kpi-conversion": MktKpiConversionWidget,
  "mkt-kpi-campaigns": MktKpiCampaignsWidget,
  "mkt-kpi-telecalling": MktKpiTelecallingWidget,
  "mkt-lead-funnel": MktLeadFunnelWidget,
  "mkt-campaign-roi": MktCampaignRoiWidget,
  "mkt-recent-leads": MktRecentLeadsWidget,
  "mkt-call-queue-status": MktCallQueueStatusWidget,
};
