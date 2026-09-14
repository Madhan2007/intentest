/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Automation Journey Builder for lead nurturing flows.
 */
import React, { useEffect, useState } from "react";
import { marketApi } from "../api/marketApi";

export const JourneyBuilderPage: React.FC = () => {
  const [journeys, setJourneys] = useState<any[]>([]);
  const [selectedJourney, setSelectedJourney] = useState<any>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [showCreate, setShowCreate] = useState<boolean>(false);
  const [newName, setNewName] = useState("");

  useEffect(() => {
    setLoading(true);
    marketApi.listJourneys()
      .then((res) => {
        const list = Array.isArray(res) ? res : [];
        setJourneys(list);
        if (list.length > 0) setSelectedJourney(list[0]);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  }, []);

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newName) return;
    const defaultFlow = {
      nodes: [
        { id: "node_1", type: "trigger", title: "New Lead Created", detail: "When status = NEW" },
        { id: "node_2", type: "action", title: "Send Welcome Email", detail: "Template: Introduction" },
        { id: "node_3", type: "delay", title: "Wait 2 Days", detail: "Duration: 48h" },
        { id: "node_4", type: "condition", title: "Check If Opened", detail: "Branch on email opened" },
        { id: "node_5", type: "action", title: "Schedule Telecaller Followup", detail: "Priority: HIGH" },
      ],
    };

    marketApi.createJourney({
      name: newName,
      description: "Automated nurturing journey",
      triggerType: "LEAD_CREATED",
      definition: JSON.stringify(defaultFlow),
    }).then((j) => {
      setShowCreate(false);
      setNewName("");
      setJourneys((prev) => [...prev, j]);
      setSelectedJourney(j);
    });
  };

  const sampleNodes = [
    { id: "node_1", type: "trigger", title: "New Lead Created", detail: "Trigger: Lead Created from Web or Ads", icon: "🚀" },
    { id: "node_2", type: "action", title: "Send Welcome Email", detail: "Action: Auto-send introductory brochure", icon: "✉️" },
    { id: "node_3", type: "delay", title: "Wait 2 Days", detail: "Delay: 48 hours for prospect engagement", icon: "⏳" },
    { id: "node_4", type: "condition", title: "Email Engagement Check", detail: "Condition: Did the prospect click link?", icon: "🔀" },
    { id: "node_5", type: "action", title: "Assign to Telecaller", detail: "Action: Add high-priority call to agent queue", icon: "📞" },
  ];

  return (
    <div style={{ padding: "24px", maxWidth: "1500px", margin: "0 auto" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "24px" }}>
        <div>
          <h1 style={{ margin: "0 0 4px 0", fontSize: "24px", color: "#fff", fontWeight: 700 }}>
            Automation Journeys
          </h1>
          <p style={{ margin: 0, fontSize: "13px", color: "var(--opz-color-muted, #9aa3af)" }}>
            Visual multi-step nurturing workflows for leads and customer lifecycle stages.
          </p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          style={{ padding: "9px 18px", background: "#8b5cf6", color: "#fff", border: "none", borderRadius: "6px", fontSize: "13px", fontWeight: 600, cursor: "pointer" }}
        >
          + New Journey
        </button>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "320px 1fr", gap: "24px" }}>
        {/* Journeys List */}
        <div style={{ background: "var(--opz-color-surface, #171a21)", borderRadius: "8px", border: "1px solid var(--opz-color-border, #2a2f3a)", padding: "16px" }}>
          <h3 style={{ margin: "0 0 16px 0", fontSize: "15px", color: "#fff" }}>Journeys</h3>
          {journeys.length === 0 ? (
            <div style={{ color: "#64748b", fontSize: "13px" }}>No journeys yet. Create your first journey!</div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
              {journeys.map((j) => (
                <div
                  key={j.id}
                  onClick={() => setSelectedJourney(j)}
                  style={{
                    padding: "12px",
                    borderRadius: "6px",
                    cursor: "pointer",
                    background: selectedJourney?.id === j.id ? "#1e293b" : "#0f1115",
                    border: selectedJourney?.id === j.id ? "1px solid #8b5cf6" : "1px solid #2a2f3a",
                  }}
                >
                  <div style={{ fontWeight: 600, color: "#fff", fontSize: "13px" }}>{j.name}</div>
                  <div style={{ fontSize: "11px", color: "#94a3b8", marginTop: "4px" }}>Trigger: {j.trigger_type}</div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Visual Workflow Canvas */}
        <div style={{ background: "var(--opz-color-surface, #171a21)", borderRadius: "8px", border: "1px solid var(--opz-color-border, #2a2f3a)", padding: "24px" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "24px", borderBottom: "1px solid #2a2f3a", paddingBottom: "16px" }}>
            <div>
              <h2 style={{ margin: "0 0 4px 0", fontSize: "18px", color: "#fff" }}>
                {selectedJourney ? selectedJourney.name : "Select a Journey"}
              </h2>
              <span style={{ fontSize: "12px", color: "#10b981", background: "#10b98122", padding: "2px 8px", borderRadius: "4px" }}>
                {selectedJourney?.status || "ACTIVE"}
              </span>
            </div>
            <button
              style={{ padding: "6px 14px", background: "#3b82f6", color: "#fff", border: "none", borderRadius: "6px", fontSize: "12px", fontWeight: 600, cursor: "pointer" }}
            >
              Save Workflow
            </button>
          </div>

          {/* Workflow Steps */}
          <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "12px", maxWidth: "600px", margin: "0 auto" }}>
            {sampleNodes.map((node, idx) => (
              <React.Fragment key={node.id}>
                <div
                  style={{
                    width: "100%",
                    background: "#0f1115",
                    border: "1px solid #334155",
                    borderRadius: "8px",
                    padding: "16px",
                    display: "flex",
                    alignItems: "center",
                    gap: "16px",
                    boxShadow: "0 4px 6px -1px rgba(0,0,0,0.3)",
                  }}
                >
                  <div style={{ fontSize: "24px", background: "#1e293b", width: "44px", height: "44px", borderRadius: "8px", display: "flex", alignItems: "center", justifyContent: "center" }}>
                    {node.icon}
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: "11px", color: "#8b5cf6", textTransform: "uppercase", fontWeight: 700 }}>
                      Step {idx + 1} &bull; {node.type}
                    </div>
                    <div style={{ fontSize: "14px", fontWeight: 600, color: "#fff" }}>{node.title}</div>
                    <div style={{ fontSize: "12px", color: "#94a3b8" }}>{node.detail}</div>
                  </div>
                </div>
                {idx < sampleNodes.length - 1 && (
                  <div style={{ width: "2px", height: "24px", background: "#475569" }} />
                )}
              </React.Fragment>
            ))}
          </div>
        </div>
      </div>

      {/* Create Modal */}
      {showCreate && (
        <div style={{ position: "fixed", top: 0, left: 0, right: 0, bottom: 0, background: "rgba(0,0,0,0.75)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#171a21", padding: "24px", borderRadius: "10px", width: "400px", border: "1px solid #2a2f3a" }}>
            <h3 style={{ margin: "0 0 16px 0", color: "#fff" }}>Create Nurturing Journey</h3>
            <form onSubmit={handleCreate}>
              <div style={{ marginBottom: "16px" }}>
                <label style={{ fontSize: "12px", color: "#94a3b8", display: "block", marginBottom: "4px" }}>Journey Name</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Enterprise Welcome Sequence"
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "8px", borderRadius: "6px" }}
                />
              </div>
              <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px" }}>
                <button type="button" onClick={() => setShowCreate(false)} style={{ padding: "6px 12px", background: "transparent", border: "1px solid #334155", color: "#94a3b8", borderRadius: "4px" }}>
                  Cancel
                </button>
                <button type="submit" style={{ padding: "6px 16px", background: "#8b5cf6", color: "#fff", border: "none", borderRadius: "4px", fontWeight: 600 }}>
                  Create
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
