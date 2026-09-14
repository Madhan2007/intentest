/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Detailed view for an individual lead with lifecycle pipeline and activity timeline.
 */
import React, { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { marketApi } from "../api/marketApi";
import { LEAD_STATUSES } from "../constants/manageMyMarketConstants";

export const LeadDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [lead, setLead] = useState<any>(null);
  const [activities, setActivities] = useState<any[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [newActivity, setNewActivity] = useState({ title: "", description: "", activityType: "NOTE" });
  const [disqualifyReason, setDisqualifyReason] = useState("");
  const [showDisqualifyModal, setShowDisqualifyModal] = useState(false);

  const loadData = () => {
    if (!id) return;
    setLoading(true);
    Promise.all([
      marketApi.getLead(id),
      marketApi.listActivities(id),
    ])
      .then(([leadData, actData]) => {
        setLead(leadData);
        setActivities(Array.isArray(actData) ? actData : []);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  };

  useEffect(() => {
    loadData();
  }, [id]);

  const handleUpdateStatus = (newStatus: string) => {
    if (!id || !lead) return;
    marketApi.updateLead(id, { status: newStatus }).then(() => loadData());
  };

  const handleDisqualify = (e: React.FormEvent) => {
    e.preventDefault();
    if (!id || !disqualifyReason) return;
    marketApi.disqualifyLead(id, disqualifyReason).then(() => {
      setShowDisqualifyModal(false);
      loadData();
    });
  };

  const handleAddActivity = (e: React.FormEvent) => {
    e.preventDefault();
    if (!id || !newActivity.title) return;
    marketApi.logActivity(id, newActivity).then(() => {
      setNewActivity({ title: "", description: "", activityType: "NOTE" });
      loadData();
    });
  };

  if (loading || !lead) {
    return (
      <div style={{ padding: "32px", textAlign: "center", color: "#94a3b8" }}>
        Loading lead details...
      </div>
    );
  }

  const currentStatusMeta = LEAD_STATUSES.find((s) => s.value === lead.status) || { label: lead.status, color: "#38bdf8" };
  const stages = ["NEW", "CONTACTED", "QUALIFIED", "PROPOSAL", "NEGOTIATION", "WON"];
  const currentStageIndex = stages.indexOf(lead.status);

  return (
    <div style={{ padding: "24px", maxWidth: "1400px", margin: "0 auto" }}>
      {/* Breadcrumb & Navigation */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "16px" }}>
        <Link to="/market/leads" style={{ color: "#38bdf8", textDecoration: "none", fontSize: "13px" }}>
          &larr; Back to Leads
        </Link>
        <div style={{ display: "flex", gap: "10px" }}>
          {lead.status !== "DISQUALIFIED" && lead.status !== "WON" && (
            <button
              onClick={() => setShowDisqualifyModal(true)}
              style={{ padding: "6px 12px", background: "#ef444422", color: "#ef4444", border: "1px solid #ef444444", borderRadius: "6px", fontSize: "12px", cursor: "pointer" }}
            >
              Disqualify
            </button>
          )}
        </div>
      </div>

      {/* Main Header Card */}
      <div
        style={{
          background: "var(--opz-color-surface, #171a21)",
          border: "1px solid var(--opz-color-border, #2a2f3a)",
          borderRadius: "10px",
          padding: "24px",
          marginBottom: "24px",
        }}
      >
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "20px" }}>
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "6px" }}>
              <span style={{ fontSize: "14px", fontWeight: 700, color: "#38bdf8", background: "#38bdf822", padding: "2px 8px", borderRadius: "4px" }}>
                {lead.lead_code}
              </span>
              <h1 style={{ margin: 0, fontSize: "22px", color: "#fff", fontWeight: 700 }}>
                {lead.display_name}
              </h1>
              <span
                style={{
                  padding: "4px 10px",
                  borderRadius: "6px",
                  fontSize: "12px",
                  fontWeight: 600,
                  background: `${currentStatusMeta.color}22`,
                  color: currentStatusMeta.color,
                  border: `1px solid ${currentStatusMeta.color}44`,
                }}
              >
                {currentStatusMeta.label}
              </span>
            </div>
            <div style={{ color: "#94a3b8", fontSize: "13px", display: "flex", gap: "16px" }}>
              {lead.company_name && <span>Company: <strong style={{ color: "#e2e8f0" }}>{lead.company_name}</strong></span>}
              {lead.email && <span>Email: <strong style={{ color: "#e2e8f0" }}>{lead.email}</strong></span>}
              {lead.phone && <span>Phone: <strong style={{ color: "#e2e8f0" }}>{lead.phone}</strong></span>}
            </div>
          </div>

          <div style={{ textAlign: "right" }}>
            <div style={{ fontSize: "11px", color: "#64748b", textTransform: "uppercase" }}>Estimated Value</div>
            <div style={{ fontSize: "24px", fontWeight: 700, color: "#10b981" }}>
              ${Number(lead.estimated_value || 0).toLocaleString()}
            </div>
          </div>
        </div>

        {/* Pipeline Stepper */}
        {lead.status !== "DISQUALIFIED" && lead.status !== "LOST" && (
          <div style={{ borderTop: "1px solid #2a2f3a", paddingTop: "16px" }}>
            <div style={{ fontSize: "11px", color: "#64748b", textTransform: "uppercase", marginBottom: "10px", fontWeight: 600 }}>
              Lead Stage Progression
            </div>
            <div style={{ display: "flex", gap: "6px" }}>
              {stages.map((stage, idx) => {
                const isPassed = currentStageIndex >= idx;
                const isCurrent = currentStageIndex === idx;
                return (
                  <button
                    key={stage}
                    onClick={() => handleUpdateStatus(stage)}
                    style={{
                      flex: 1,
                      padding: "8px 4px",
                      background: isCurrent ? "#3b82f6" : isPassed ? "#1e293b" : "#0f1115",
                      color: isCurrent ? "#fff" : isPassed ? "#93c5fd" : "#475569",
                      border: isCurrent ? "1px solid #3b82f6" : "1px solid #2a2f3a",
                      borderRadius: "6px",
                      fontSize: "11px",
                      fontWeight: 600,
                      cursor: "pointer",
                      transition: "all 0.15s",
                    }}
                  >
                    {idx + 1}. {stage}
                  </button>
                );
              })}
            </div>
          </div>
        )}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr", gap: "24px" }}>
        {/* Left: Lead Metadata */}
        <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
          <div
            style={{
              background: "var(--opz-color-surface, #171a21)",
              border: "1px solid var(--opz-color-border, #2a2f3a)",
              borderRadius: "8px",
              padding: "16px",
            }}
          >
            <h3 style={{ margin: "0 0 14px 0", fontSize: "14px", color: "#fff" }}>Lead Information</h3>
            <div style={{ display: "flex", flexDirection: "column", gap: "10px", fontSize: "13px" }}>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ color: "#64748b" }}>Source</span>
                <span style={{ color: "#cbd5e1", fontWeight: 500 }}>{lead.lead_source_type}</span>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ color: "#64748b" }}>Priority</span>
                <span style={{ color: "#cbd5e1", fontWeight: 500 }}>{lead.priority || "NORMAL"}</span>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ color: "#64748b" }}>Assigned To</span>
                <span style={{ color: "#cbd5e1", fontWeight: 500 }}>{lead.owner_user_id || "Unassigned"}</span>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ color: "#64748b" }}>Created At</span>
                <span style={{ color: "#cbd5e1", fontWeight: 500 }}>
                  {lead.created_at ? new Date(lead.created_at).toLocaleDateString() : "-"}
                </span>
              </div>
            </div>
          </div>

          {/* Quick Note / Activity Form */}
          <div
            style={{
              background: "var(--opz-color-surface, #171a21)",
              border: "1px solid var(--opz-color-border, #2a2f3a)",
              borderRadius: "8px",
              padding: "16px",
            }}
          >
            <h3 style={{ margin: "0 0 12px 0", fontSize: "14px", color: "#fff" }}>Log Lead Activity</h3>
            <form onSubmit={handleAddActivity} style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
              <select
                value={newActivity.activityType}
                onChange={(e) => setNewActivity({ ...newActivity, activityType: e.target.value })}
                style={{ background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "6px 8px", borderRadius: "4px", fontSize: "12px" }}
              >
                <option value="NOTE">General Note</option>
                <option value="CALL">Phone Call</option>
                <option value="EMAIL">Email Sent</option>
                <option value="MEETING">Meeting Held</option>
              </select>

              <input
                type="text"
                required
                placeholder="Activity Title"
                value={newActivity.title}
                onChange={(e) => setNewActivity({ ...newActivity, title: e.target.value })}
                style={{ background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "6px 8px", borderRadius: "4px", fontSize: "12px" }}
              />

              <textarea
                placeholder="Activity details, outcomes, notes..."
                value={newActivity.description}
                onChange={(e) => setNewActivity({ ...newActivity, description: e.target.value })}
                style={{ background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "6px 8px", borderRadius: "4px", fontSize: "12px", minHeight: "60px" }}
              />

              <button
                type="submit"
                style={{ padding: "8px", background: "#3b82f6", color: "#fff", border: "none", borderRadius: "6px", fontSize: "12px", fontWeight: 600, cursor: "pointer" }}
              >
                Add Activity
              </button>
            </form>
          </div>
        </div>

        {/* Right: Activity Timeline */}
        <div
          style={{
            background: "var(--opz-color-surface, #171a21)",
            border: "1px solid var(--opz-color-border, #2a2f3a)",
            borderRadius: "8px",
            padding: "20px",
          }}
        >
          <h3 style={{ margin: "0 0 16px 0", fontSize: "16px", color: "#fff" }}>Activity & Engagement Timeline</h3>
          {activities.length === 0 ? (
            <div style={{ color: "#64748b", fontSize: "13px", textAlign: "center", padding: "32px" }}>
              No activities logged yet.
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
              {activities.map((act) => (
                <div
                  key={act.id}
                  style={{
                    background: "#0f1115",
                    padding: "14px",
                    borderRadius: "6px",
                    border: "1px solid #2a2f3a",
                    borderLeft: "3px solid #38bdf8",
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "4px" }}>
                    <span style={{ fontWeight: 600, color: "#fff", fontSize: "13px" }}>{act.title}</span>
                    <span style={{ fontSize: "11px", color: "#64748b" }}>
                      {act.created_at ? new Date(act.created_at).toLocaleString() : ""}
                    </span>
                  </div>
                  {act.description && (
                    <p style={{ margin: "4px 0 0 0", color: "#94a3b8", fontSize: "12px" }}>
                      {act.description}
                    </p>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Disqualify Modal */}
      {showDisqualifyModal && (
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
          <div style={{ background: "#171a21", padding: "24px", borderRadius: "10px", width: "400px", border: "1px solid #2a2f3a" }}>
            <h3 style={{ margin: "0 0 12px 0", color: "#ef4444" }}>Disqualify Lead</h3>
            <p style={{ fontSize: "13px", color: "#94a3b8", marginBottom: "16px" }}>
              Please provide a reason for disqualifying this lead.
            </p>
            <form onSubmit={handleDisqualify}>
              <textarea
                required
                placeholder="Reason for disqualification..."
                value={disqualifyReason}
                onChange={(e) => setDisqualifyReason(e.target.value)}
                style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "8px", borderRadius: "6px", minHeight: "80px", marginBottom: "16px" }}
              />
              <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px" }}>
                <button
                  type="button"
                  onClick={() => setShowDisqualifyModal(false)}
                  style={{ padding: "6px 12px", background: "transparent", border: "1px solid #334155", color: "#94a3b8", borderRadius: "4px" }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  style={{ padding: "6px 14px", background: "#ef4444", color: "#fff", border: "none", borderRadius: "4px", fontWeight: 600 }}
                >
                  Disqualify
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
