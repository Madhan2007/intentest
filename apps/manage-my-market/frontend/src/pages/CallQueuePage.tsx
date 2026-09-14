/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Telemarketing Call Queue and agent dialing console.
 */
import React, { useEffect, useState } from "react";
import { marketApi } from "../api/marketApi";
import { CALL_OUTCOMES } from "../constants/manageMyMarketConstants";

export const CallQueuePage: React.FC = () => {
  const [queues, setQueues] = useState<any[]>([]);
  const [selectedQueueId, setSelectedQueueId] = useState<string>("");
  const [items, setItems] = useState<any[]>([]);
  const [currentItem, setCurrentItem] = useState<any>(null);
  const [loading, setLoading] = useState<boolean>(true);

  // Call logging form state
  const [outcome, setOutcome] = useState<string>("CONNECTED");
  const [notes, setNotes] = useState<string>("");
  const [duration, setDuration] = useState<number>(120);
  const [saving, setSaving] = useState<boolean>(false);

  useEffect(() => {
    setLoading(true);
    marketApi.listCallQueues()
      .then((qList) => {
        const list = Array.isArray(qList) ? qList : [];
        setQueues(list);
        if (list.length > 0) {
          setSelectedQueueId(list[0].id);
        }
        setLoading(false);
      })
      .catch(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!selectedQueueId) return;
    marketApi.listQueueItems(selectedQueueId, "PENDING")
      .then((res) => {
        const list = Array.isArray(res) ? res : [];
        setItems(list);
        if (list.length > 0) setCurrentItem(list[0]);
        else setCurrentItem(null);
      })
      .catch(() => {});
  }, [selectedQueueId]);

  const handleLogCall = (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentItem) return;
    setSaving(true);

    marketApi.createCallLog({
      leadId: currentItem.lead_id,
      callOutcome: outcome,
      callDurationSeconds: duration,
      notes: notes,
    })
      .then(() => {
        // Mark item completed
        marketApi.updateQueueItem(currentItem.id, { status: "COMPLETED" }).then(() => {
          setSaving(false);
          setNotes("");
          // Move to next item
          setItems((prev) => {
            const next = prev.filter((i) => i.id !== currentItem.id);
            setCurrentItem(next.length > 0 ? next[0] : null);
            return next;
          });
        });
      })
      .catch(() => setSaving(false));
  };

  return (
    <div style={{ padding: "24px", maxWidth: "1400px", margin: "0 auto" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "24px" }}>
        <div>
          <h1 style={{ margin: "0 0 4px 0", fontSize: "24px", color: "#fff", fontWeight: 700 }}>
            Telemarketing Console
          </h1>
          <p style={{ margin: 0, fontSize: "13px", color: "var(--opz-color-muted, #9aa3af)" }}>
            High-speed outbound dialer queue and call disposition logging.
          </p>
        </div>

        {queues.length > 0 && (
          <select
            value={selectedQueueId}
            onChange={(e) => setSelectedQueueId(e.target.value)}
            style={{ background: "#171a21", border: "1px solid #2a2f3a", color: "#fff", padding: "8px 12px", borderRadius: "6px" }}
          >
            {queues.map((q) => (
              <option key={q.id} value={q.id}>{q.name}</option>
            ))}
          </select>
        )}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "24px" }}>
        {/* Active Contact Card */}
        <div style={{ background: "var(--opz-color-surface, #171a21)", padding: "24px", borderRadius: "10px", border: "1px solid var(--opz-color-border, #2a2f3a)" }}>
          <div style={{ fontSize: "11px", color: "#f59e0b", textTransform: "uppercase", fontWeight: 700, marginBottom: "8px" }}>
            Current Lead in Queue ({items.length} remaining)
          </div>

          {currentItem ? (
            <div>
              <div style={{ fontSize: "20px", fontWeight: 700, color: "#fff", marginBottom: "4px" }}>
                Lead ID: {currentItem.lead_id}
              </div>
              <div style={{ fontSize: "14px", color: "#38bdf8", marginBottom: "16px" }}>
                Priority: {currentItem.priority} &bull; Attempts: {currentItem.attempts_count || 0}
              </div>

              <div style={{ background: "#0f1115", padding: "16px", borderRadius: "8px", border: "1px solid #2a2f3a", marginBottom: "20px" }}>
                <div style={{ fontSize: "13px", color: "#94a3b8", marginBottom: "8px" }}>Status: Ready for Call</div>
                <div style={{ fontSize: "18px", fontWeight: 700, color: "#10b981" }}>
                  Active Queue Item #{currentItem.id.slice(0, 8)}
                </div>
              </div>
            </div>
          ) : (
            <div style={{ padding: "40px", textAlign: "center", color: "#64748b" }}>
              <div style={{ fontSize: "28px", marginBottom: "8px" }}>🎉</div>
              <div style={{ fontSize: "15px", fontWeight: 600, color: "#e2e8f0" }}>All Queued Calls Completed!</div>
              <div style={{ fontSize: "12px", marginTop: "4px" }}>No pending leads currently in this queue.</div>
            </div>
          )}
        </div>

        {/* Call Disposition Form */}
        <div style={{ background: "var(--opz-color-surface, #171a21)", padding: "24px", borderRadius: "10px", border: "1px solid var(--opz-color-border, #2a2f3a)" }}>
          <h3 style={{ margin: "0 0 16px 0", fontSize: "16px", color: "#fff" }}>Log Call Disposition</h3>
          <form onSubmit={handleLogCall} style={{ display: "flex", flexDirection: "column", gap: "14px" }}>
            <div>
              <label style={{ fontSize: "12px", color: "#94a3b8", display: "block", marginBottom: "4px" }}>Call Outcome</label>
              <select
                value={outcome}
                onChange={(e) => setOutcome(e.target.value)}
                style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "8px", borderRadius: "6px" }}
              >
                {CALL_OUTCOMES.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </div>

            <div>
              <label style={{ fontSize: "12px", color: "#94a3b8", display: "block", marginBottom: "4px" }}>Call Duration (seconds)</label>
              <input
                type="number"
                value={duration}
                onChange={(e) => setDuration(parseInt(e.target.value, 10) || 0)}
                style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "8px", borderRadius: "6px" }}
              />
            </div>

            <div>
              <label style={{ fontSize: "12px", color: "#94a3b8", display: "block", marginBottom: "4px" }}>Call Notes & Follow-up Action</label>
              <textarea
                placeholder="Key discussion points, customer interest, objections, next steps..."
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "8px", borderRadius: "6px", minHeight: "90px" }}
              />
            </div>

            <button
              type="submit"
              disabled={!currentItem || saving}
              style={{
                padding: "10px",
                background: currentItem ? "#f59e0b" : "#475569",
                color: "#fff",
                border: "none",
                borderRadius: "6px",
                fontWeight: 700,
                cursor: currentItem ? "pointer" : "not-allowed",
              }}
            >
              {saving ? "Logging..." : "Complete & Next Call &rarr;"}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};
