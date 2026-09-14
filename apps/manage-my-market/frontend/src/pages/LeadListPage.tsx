/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Lead management list with filters, search, and quick creation.
 */
import React, { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { marketApi } from "../api/marketApi";
import { LEAD_STATUSES, LEAD_SOURCES } from "../constants/manageMyMarketConstants";

export const LeadListPage: React.FC = () => {
  const navigate = useNavigate();
  const [leads, setLeads] = useState<any[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [search, setSearch] = useState<string>("");
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [sourceFilter, setSourceFilter] = useState<string>("");
  const [showModal, setShowModal] = useState<boolean>(false);

  // New lead form state
  const [formData, setFormData] = useState({
    displayName: "",
    firstName: "",
    lastName: "",
    companyName: "",
    email: "",
    phone: "",
    leadSourceType: "INBOUND_WEB",
    estimatedValue: 10000,
    priority: "MEDIUM",
    notes: "",
  });

  const loadLeads = () => {
    setLoading(true);
    marketApi
      .listLeads({
        search: search || undefined,
        status: statusFilter || undefined,
        lead_source_type: sourceFilter || undefined,
        limit: 50,
      })
      .then((res) => {
        setLeads(Array.isArray(res) ? res : []);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  };

  useEffect(() => {
    loadLeads();
  }, [statusFilter, sourceFilter]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    loadLeads();
  };

  const handleCreateLead = (e: React.FormEvent) => {
    e.preventDefault();
    marketApi.createLead(formData).then((lead) => {
      setShowModal(false);
      loadLeads();
      if (lead?.id) navigate(`/market/leads/${lead.id}`);
    });
  };

  return (
    <div style={{ padding: "24px", maxWidth: "1500px", margin: "0 auto" }}>
      {/* Header */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "24px" }}>
        <div>
          <h1 style={{ margin: "0 0 4px 0", fontSize: "24px", color: "#fff", fontWeight: 700 }}>
            Lead Pipeline
          </h1>
          <p style={{ margin: 0, fontSize: "13px", color: "var(--opz-color-muted, #9aa3af)" }}>
            Track, qualify, and convert marketing prospects into revenue opportunities.
          </p>
        </div>
        <button
          onClick={() => setShowModal(true)}
          style={{
            padding: "9px 18px",
            background: "#3b82f6",
            color: "#fff",
            border: "none",
            borderRadius: "6px",
            fontSize: "13px",
            fontWeight: 600,
            cursor: "pointer",
          }}
        >
          + Create Lead
        </button>
      </div>

      {/* Filter Bar */}
      <div
        style={{
          display: "flex",
          gap: "12px",
          background: "var(--opz-color-surface, #171a21)",
          padding: "16px",
          borderRadius: "8px",
          border: "1px solid var(--opz-color-border, #2a2f3a)",
          marginBottom: "20px",
          flexWrap: "wrap",
        }}
      >
        <form onSubmit={handleSearch} style={{ display: "flex", gap: "8px", flex: 1, minWidth: "250px" }}>
          <input
            type="text"
            placeholder="Search by name, email, company, code..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{
              flex: 1,
              background: "#0f1115",
              border: "1px solid #2a2f3a",
              color: "#fff",
              padding: "8px 12px",
              borderRadius: "6px",
              fontSize: "13px",
            }}
          />
          <button
            type="submit"
            style={{
              padding: "8px 16px",
              background: "#1e293b",
              color: "#e2e8f0",
              border: "1px solid #334155",
              borderRadius: "6px",
              cursor: "pointer",
              fontSize: "13px",
            }}
          >
            Search
          </button>
        </form>

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
          {LEAD_STATUSES.map((s) => (
            <option key={s.value} value={s.value}>{s.label}</option>
          ))}
        </select>

        <select
          value={sourceFilter}
          onChange={(e) => setSourceFilter(e.target.value)}
          style={{
            background: "#0f1115",
            border: "1px solid #2a2f3a",
            color: "#fff",
            padding: "8px 12px",
            borderRadius: "6px",
            fontSize: "13px",
          }}
        >
          <option value="">All Sources</option>
          {LEAD_SOURCES.map((s) => (
            <option key={s.value} value={s.value}>{s.label}</option>
          ))}
        </select>
      </div>

      {/* Leads Table */}
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
              <th style={{ padding: "12px 16px" }}>Lead Name</th>
              <th style={{ padding: "12px 16px" }}>Company</th>
              <th style={{ padding: "12px 16px" }}>Source</th>
              <th style={{ padding: "12px 16px" }}>Est. Value</th>
              <th style={{ padding: "12px 16px" }}>Status</th>
              <th style={{ padding: "12px 16px" }}>Created</th>
              <th style={{ padding: "12px 16px", textAlign: "right" }}>Action</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={8} style={{ padding: "32px", textAlign: "center", color: "#64748b" }}>Loading leads...</td></tr>
            ) : leads.length === 0 ? (
              <tr><td colSpan={8} style={{ padding: "32px", textAlign: "center", color: "#64748b" }}>No leads matching criteria.</td></tr>
            ) : (
              leads.map((l) => {
                const statusMeta = LEAD_STATUSES.find((s) => s.value === l.status) || { label: l.status, color: "#38bdf8" };
                return (
                  <tr
                    key={l.id}
                    style={{
                      borderBottom: "1px solid #1f242e",
                      cursor: "pointer",
                      transition: "background 0.15s",
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.background = "#1a1f2c")}
                    onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
                    onClick={() => navigate(`/market/leads/${l.id}`)}
                  >
                    <td style={{ padding: "12px 16px", fontWeight: 700, color: "#38bdf8" }}>{l.lead_code}</td>
                    <td style={{ padding: "12px 16px", color: "#fff", fontWeight: 500 }}>
                      {l.display_name}
                      {l.email && <div style={{ fontSize: "11px", color: "#64748b" }}>{l.email}</div>}
                    </td>
                    <td style={{ padding: "12px 16px", color: "#cbd5e1" }}>{l.company_name || "-"}</td>
                    <td style={{ padding: "12px 16px", color: "#94a3b8" }}>{l.lead_source_type}</td>
                    <td style={{ padding: "12px 16px", color: "#10b981", fontWeight: 600 }}>
                      ${Number(l.estimated_value || 0).toLocaleString()}
                    </td>
                    <td style={{ padding: "12px 16px" }}>
                      <span
                        style={{
                          padding: "3px 8px",
                          borderRadius: "4px",
                          fontSize: "11px",
                          fontWeight: 600,
                          background: `${statusMeta.color}22`,
                          color: statusMeta.color,
                          border: `1px solid ${statusMeta.color}44`,
                        }}
                      >
                        {statusMeta.label}
                      </span>
                    </td>
                    <td style={{ padding: "12px 16px", color: "#64748b", fontSize: "12px" }}>
                      {l.created_at ? new Date(l.created_at).toLocaleDateString() : "-"}
                    </td>
                    <td style={{ padding: "12px 16px", textAlign: "right" }}>
                      <Link
                        to={`/market/leads/${l.id}`}
                        onClick={(e) => e.stopPropagation()}
                        style={{ color: "#38bdf8", textDecoration: "none", fontWeight: 500 }}
                      >
                        View &rarr;
                      </Link>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* Create Lead Modal */}
      {showModal && (
        <div
          style={{
            position: "fixed",
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: "rgba(0,0,0,0.75)",
            backdropFilter: "blur(4px)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            zIndex: 1000,
          }}
        >
          <div
            style={{
              background: "#171a21",
              border: "1px solid #2a2f3a",
              borderRadius: "10px",
              width: "550px",
              maxWidth: "90%",
              padding: "24px",
              maxHeight: "90vh",
              overflowY: "auto",
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "20px" }}>
              <h3 style={{ margin: 0, color: "#fff", fontSize: "18px" }}>Create New Lead</h3>
              <button
                onClick={() => setShowModal(false)}
                style={{ background: "transparent", border: "none", color: "#94a3b8", fontSize: "18px", cursor: "pointer" }}
              >
                x
              </button>
            </div>

            <form onSubmit={handleCreateLead} style={{ display: "flex", flexDirection: "column", gap: "14px" }}>
              <div style={{ display: "flex", gap: "12px" }}>
                <div style={{ flex: 1 }}>
                  <label style={{ fontSize: "12px", color: "#94a3b8", display: "block", marginBottom: "4px" }}>First Name</label>
                  <input
                    type="text"
                    required
                    value={formData.firstName}
                    onChange={(e) => setFormData({ ...formData, firstName: e.target.value, displayName: `${e.target.value} ${formData.lastName}`.trim() })}
                    style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "8px", borderRadius: "6px" }}
                  />
                </div>
                <div style={{ flex: 1 }}>
                  <label style={{ fontSize: "12px", color: "#94a3b8", display: "block", marginBottom: "4px" }}>Last Name</label>
                  <input
                    type="text"
                    value={formData.lastName}
                    onChange={(e) => setFormData({ ...formData, lastName: e.target.value, displayName: `${formData.firstName} ${e.target.value}`.trim() })}
                    style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "8px", borderRadius: "6px" }}
                  />
                </div>
              </div>

              <div>
                <label style={{ fontSize: "12px", color: "#94a3b8", display: "block", marginBottom: "4px" }}>Display Name</label>
                <input
                  type="text"
                  required
                  value={formData.displayName}
                  onChange={(e) => setFormData({ ...formData, displayName: e.target.value })}
                  style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "8px", borderRadius: "6px" }}
                />
              </div>

              <div>
                <label style={{ fontSize: "12px", color: "#94a3b8", display: "block", marginBottom: "4px" }}>Company Name</label>
                <input
                  type="text"
                  value={formData.companyName}
                  onChange={(e) => setFormData({ ...formData, companyName: e.target.value })}
                  style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "8px", borderRadius: "6px" }}
                />
              </div>

              <div style={{ display: "flex", gap: "12px" }}>
                <div style={{ flex: 1 }}>
                  <label style={{ fontSize: "12px", color: "#94a3b8", display: "block", marginBottom: "4px" }}>Email</label>
                  <input
                    type="email"
                    value={formData.email}
                    onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                    style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "8px", borderRadius: "6px" }}
                  />
                </div>
                <div style={{ flex: 1 }}>
                  <label style={{ fontSize: "12px", color: "#94a3b8", display: "block", marginBottom: "4px" }}>Phone</label>
                  <input
                    type="text"
                    value={formData.phone}
                    onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                    style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "8px", borderRadius: "6px" }}
                  />
                </div>
              </div>

              <div style={{ display: "flex", gap: "12px" }}>
                <div style={{ flex: 1 }}>
                  <label style={{ fontSize: "12px", color: "#94a3b8", display: "block", marginBottom: "4px" }}>Source</label>
                  <select
                    value={formData.leadSourceType}
                    onChange={(e) => setFormData({ ...formData, leadSourceType: e.target.value })}
                    style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "8px", borderRadius: "6px" }}
                  >
                    {LEAD_SOURCES.map((s) => (
                      <option key={s.value} value={s.value}>{s.label}</option>
                    ))}
                  </select>
                </div>
                <div style={{ flex: 1 }}>
                  <label style={{ fontSize: "12px", color: "#94a3b8", display: "block", marginBottom: "4px" }}>Est. Value ($)</label>
                  <input
                    type="number"
                    value={formData.estimatedValue}
                    onChange={(e) => setFormData({ ...formData, estimatedValue: parseFloat(e.target.value) || 0 })}
                    style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "8px", borderRadius: "6px" }}
                  />
                </div>
              </div>

              <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px", marginTop: "16px" }}>
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  style={{ padding: "8px 16px", background: "transparent", border: "1px solid #334155", color: "#94a3b8", borderRadius: "6px", cursor: "pointer" }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  style={{ padding: "8px 18px", background: "#3b82f6", color: "#fff", border: "none", borderRadius: "6px", fontWeight: 600, cursor: "pointer" }}
                >
                  Save Lead
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
