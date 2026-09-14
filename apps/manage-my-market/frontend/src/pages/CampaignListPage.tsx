/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Campaign management list with budget tracking and status filters.
 */
import React, { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { marketApi } from "../api/marketApi";
import { CAMPAIGN_STATUSES } from "../constants/manageMyMarketConstants";

export const CampaignListPage: React.FC = () => {
  const navigate = useNavigate();
  const [campaigns, setCampaigns] = useState<any[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [showModal, setShowModal] = useState<boolean>(false);

  const [formData, setFormData] = useState({
    name: "",
    objective: "LEAD_GENERATION",
    budget: 5000,
    startDate: new Date().toISOString().slice(0, 10),
    endDate: new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10),
    utmCampaign: "",
  });

  const loadCampaigns = () => {
    setLoading(true);
    marketApi
      .listCampaigns({ status: statusFilter || undefined, limit: 50 })
      .then((res) => {
        setCampaigns(Array.isArray(res) ? res : []);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  };

  useEffect(() => {
    loadCampaigns();
  }, [statusFilter]);

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    marketApi.createCampaign(formData).then((c) => {
      setShowModal(false);
      loadCampaigns();
      if (c?.id) navigate(`/market/campaigns/${c.id}`);
    });
  };

  return (
    <div style={{ padding: "24px", maxWidth: "1500px", margin: "0 auto" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "24px" }}>
        <div>
          <h1 style={{ margin: "0 0 4px 0", fontSize: "24px", color: "#fff", fontWeight: 700 }}>
            Marketing Campaigns
          </h1>
          <p style={{ margin: 0, fontSize: "13px", color: "var(--opz-color-muted, #9aa3af)" }}>
            Execute multi-channel outreach campaigns and track ROI performance.
          </p>
        </div>
        <button
          onClick={() => setShowModal(true)}
          style={{
            padding: "9px 18px",
            background: "#10b981",
            color: "#fff",
            border: "none",
            borderRadius: "6px",
            fontSize: "13px",
            fontWeight: 600,
            cursor: "pointer",
          }}
        >
          + Create Campaign
        </button>
      </div>

      {/* Filter */}
      <div
        style={{
          display: "flex",
          gap: "12px",
          background: "var(--opz-color-surface, #171a21)",
          padding: "16px",
          borderRadius: "8px",
          border: "1px solid var(--opz-color-border, #2a2f3a)",
          marginBottom: "20px",
        }}
      >
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          style={{
            background: "#0f1115",
            border: "1px solid #2a2f3a",
            color: "#fff",
            padding: "8px 12px",
            borderRadius: "6px",
            fontSize: "13px",
          }}
        >
          <option value="">All Statuses</option>
          {CAMPAIGN_STATUSES.map((s) => (
            <option key={s.value} value={s.value}>{s.label}</option>
          ))}
        </select>
      </div>

      {/* Table */}
      <div
        style={{
          background: "var(--opz-color-surface, #171a21)",
          borderRadius: "8px",
          border: "1px solid var(--opz-color-border, #2a2f3a)",
          overflow: "hidden",
        }}
      >
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "13px" }}>
          <thead>
            <tr style={{ background: "#0f1115", borderBottom: "1px solid #2a2f3a", color: "#94a3b8", textAlign: "left" }}>
              <th style={{ padding: "12px 16px" }}>Code</th>
              <th style={{ padding: "12px 16px" }}>Campaign Name</th>
              <th style={{ padding: "12px 16px" }}>Status</th>
              <th style={{ padding: "12px 16px" }}>Budget</th>
              <th style={{ padding: "12px 16px" }}>Spent</th>
              <th style={{ padding: "12px 16px" }}>Progress</th>
              <th style={{ padding: "12px 16px", textAlign: "right" }}>Action</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={7} style={{ padding: "32px", textAlign: "center", color: "#64748b" }}>Loading campaigns...</td></tr>
            ) : campaigns.length === 0 ? (
              <tr><td colSpan={7} style={{ padding: "32px", textAlign: "center", color: "#64748b" }}>No campaigns found.</td></tr>
            ) : (
              campaigns.map((c) => {
                const sMeta = CAMPAIGN_STATUSES.find((s) => s.value === c.status) || { label: c.status, color: "#38bdf8" };
                const budget = Number(c.budget) || 0;
                const spent = Number(c.spent) || 0;
                const pct = budget > 0 ? Math.min(100, Math.round((spent / budget) * 100)) : 0;

                return (
                  <tr
                    key={c.id}
                    style={{ borderBottom: "1px solid #1f242e", cursor: "pointer" }}
                    onClick={() => navigate(`/market/campaigns/${c.id}`)}
                  >
                    <td style={{ padding: "12px 16px", fontWeight: 700, color: "#38bdf8" }}>{c.code}</td>
                    <td style={{ padding: "12px 16px", color: "#fff", fontWeight: 500 }}>{c.name}</td>
                    <td style={{ padding: "12px 16px" }}>
                      <span
                        style={{
                          padding: "3px 8px",
                          borderRadius: "4px",
                          fontSize: "11px",
                          fontWeight: 600,
                          background: `${sMeta.color}22`,
                          color: sMeta.color,
                          border: `1px solid ${sMeta.color}44`,
                        }}
                      >
                        {sMeta.label}
                      </span>
                    </td>
                    <td style={{ padding: "12px 16px", color: "#cbd5e1" }}>${budget.toLocaleString()}</td>
                    <td style={{ padding: "12px 16px", color: "#ec4899" }}>${spent.toLocaleString()}</td>
                    <td style={{ padding: "12px 16px", width: "160px" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                        <div style={{ flex: 1, height: "6px", background: "#0f1115", borderRadius: "3px", overflow: "hidden" }}>
                          <div style={{ width: `${pct}%`, height: "100%", background: pct > 90 ? "#ef4444" : "#10b981", borderRadius: "3px" }} />
                        </div>
                        <span style={{ fontSize: "11px", color: "#64748b", width: "32px" }}>{pct}%</span>
                      </div>
                    </td>
                    <td style={{ padding: "12px 16px", textAlign: "right" }}>
                      <Link to={`/market/campaigns/${c.id}`} onClick={(e) => e.stopPropagation()} style={{ color: "#38bdf8", textDecoration: "none" }}>
                        Manage &rarr;
                      </Link>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* Create Modal */}
      {showModal && (
        <div style={{ position: "fixed", top: 0, left: 0, right: 0, bottom: 0, background: "rgba(0,0,0,0.75)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#171a21", padding: "24px", borderRadius: "10px", width: "500px", border: "1px solid #2a2f3a" }}>
            <h3 style={{ margin: "0 0 16px 0", color: "#fff" }}>Create Campaign</h3>
            <form onSubmit={handleCreate} style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
              <div>
                <label style={{ fontSize: "12px", color: "#94a3b8", display: "block", marginBottom: "4px" }}>Campaign Name</label>
                <input
                  type="text"
                  required
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "8px", borderRadius: "6px" }}
                />
              </div>

              <div>
                <label style={{ fontSize: "12px", color: "#94a3b8", display: "block", marginBottom: "4px" }}>Budget ($)</label>
                <input
                  type="number"
                  required
                  value={formData.budget}
                  onChange={(e) => setFormData({ ...formData, budget: parseFloat(e.target.value) || 0 })}
                  style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "8px", borderRadius: "6px" }}
                />
              </div>

              <div style={{ display: "flex", gap: "12px" }}>
                <div style={{ flex: 1 }}>
                  <label style={{ fontSize: "12px", color: "#94a3b8", display: "block", marginBottom: "4px" }}>Start Date</label>
                  <input
                    type="date"
                    value={formData.startDate}
                    onChange={(e) => setFormData({ ...formData, startDate: e.target.value })}
                    style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "8px", borderRadius: "6px" }}
                  />
                </div>
                <div style={{ flex: 1 }}>
                  <label style={{ fontSize: "12px", color: "#94a3b8", display: "block", marginBottom: "4px" }}>End Date</label>
                  <input
                    type="date"
                    value={formData.endDate}
                    onChange={(e) => setFormData({ ...formData, endDate: e.target.value })}
                    style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "8px", borderRadius: "6px" }}
                  />
                </div>
              </div>

              <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px", marginTop: "12px" }}>
                <button type="button" onClick={() => setShowModal(false)} style={{ padding: "8px 14px", background: "transparent", border: "1px solid #334155", color: "#94a3b8", borderRadius: "6px" }}>
                  Cancel
                </button>
                <button type="submit" style={{ padding: "8px 18px", background: "#10b981", color: "#fff", border: "none", borderRadius: "6px", fontWeight: 600 }}>
                  Save Campaign
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
