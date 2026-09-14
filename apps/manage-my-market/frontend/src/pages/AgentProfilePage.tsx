/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Telecaller / Marketing Agent Profile settings.
 */
import React, { useEffect, useState } from "react";
import { marketApi } from "../api/marketApi";

export const AgentProfilePage: React.FC = () => {
  const [profile, setProfile] = useState<any>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [statusMsg, setStatusMsg] = useState<string>("");
  const [formData, setFormData] = useState({
    extensionNumber: "",
    skills: "ENGLISH, ENTERPRISE, DEMO",
    dailyTargetCalls: 40,
    dailyTargetWon: 2,
    status: "AVAILABLE",
  });

  useEffect(() => {
    setLoading(true);
    marketApi.getMyProfile()
      .then((p) => {
        if (p) {
          setProfile(p);
          setFormData({
            extensionNumber: p.extension_number || "",
            skills: Array.isArray(p.skills) ? p.skills.join(", ") : (p.skills || ""),
            dailyTargetCalls: p.daily_target_calls || 40,
            dailyTargetWon: p.daily_target_won || 2,
            status: p.status || "AVAILABLE",
          });
        }
        setLoading(false);
      })
      .catch(() => setLoading(false));
  }, []);

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    setStatusMsg("Saving profile...");
    const skillsArray = formData.skills.split(",").map((s) => s.trim()).filter(Boolean);

    marketApi.updateMyProfile({
      extensionNumber: formData.extensionNumber,
      skills: skillsArray,
      dailyTargetCalls: formData.dailyTargetCalls,
      dailyTargetWon: formData.dailyTargetWon,
      status: formData.status,
    })
      .then(() => {
        setStatusMsg("Profile updated successfully!");
        setTimeout(() => setStatusMsg(""), 3000);
      })
      .catch((err: any) => setStatusMsg("Failed: " + (err?.message || String(err))));
  };

  if (loading) return <div style={{ padding: "32px", textAlign: "center", color: "#94a3b8" }}>Loading profile...</div>;

  return (
    <div style={{ padding: "24px", maxWidth: "800px", margin: "0 auto" }}>
      <div style={{ marginBottom: "24px" }}>
        <h1 style={{ margin: "0 0 4px 0", fontSize: "24px", color: "#fff", fontWeight: 700 }}>
          Agent Telephony Profile
        </h1>
        <p style={{ margin: 0, fontSize: "13px", color: "var(--opz-color-muted, #9aa3af)" }}>
          Manage your telecalling extension, active skills, and daily outbound targets.
        </p>
      </div>

      <div style={{ background: "var(--opz-color-surface, #171a21)", borderRadius: "10px", border: "1px solid var(--opz-color-border, #2a2f3a)", padding: "24px" }}>
        <form onSubmit={handleSave} style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
          <div>
            <label style={{ fontSize: "13px", color: "#94a3b8", display: "block", marginBottom: "6px" }}>Agent Availability Status</label>
            <select
              value={formData.status}
              onChange={(e) => setFormData({ ...formData, status: e.target.value })}
              style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "10px", borderRadius: "6px" }}
            >
              <option value="AVAILABLE">Available (Ready to receive calls)</option>
              <option value="ON_CALL">On Call</option>
              <option value="BREAK">On Break</option>
              <option value="OFFLINE">Offline</option>
            </select>
          </div>

          <div>
            <label style={{ fontSize: "13px", color: "#94a3b8", display: "block", marginBottom: "6px" }}>Phone / SIP Extension</label>
            <input
              type="text"
              placeholder="e.g. 1042"
              value={formData.extensionNumber}
              onChange={(e) => setFormData({ ...formData, extensionNumber: e.target.value })}
              style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "10px", borderRadius: "6px" }}
            />
          </div>

          <div>
            <label style={{ fontSize: "13px", color: "#94a3b8", display: "block", marginBottom: "6px" }}>Skills & Competencies (comma-separated)</label>
            <input
              type="text"
              value={formData.skills}
              onChange={(e) => setFormData({ ...formData, skills: e.target.value })}
              style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "10px", borderRadius: "6px" }}
            />
          </div>

          <div style={{ display: "flex", gap: "16px" }}>
            <div style={{ flex: 1 }}>
              <label style={{ fontSize: "13px", color: "#94a3b8", display: "block", marginBottom: "6px" }}>Daily Call Target</label>
              <input
                type="number"
                value={formData.dailyTargetCalls}
                onChange={(e) => setFormData({ ...formData, dailyTargetCalls: parseInt(e.target.value, 10) || 0 })}
                style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "10px", borderRadius: "6px" }}
              />
            </div>
            <div style={{ flex: 1 }}>
              <label style={{ fontSize: "13px", color: "#94a3b8", display: "block", marginBottom: "6px" }}>Daily Won Target</label>
              <input
                type="number"
                value={formData.dailyTargetWon}
                onChange={(e) => setFormData({ ...formData, dailyTargetWon: parseInt(e.target.value, 10) || 0 })}
                style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "10px", borderRadius: "6px" }}
              />
            </div>
          </div>

          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: "12px" }}>
            {statusMsg && <span style={{ color: "#38bdf8", fontSize: "13px", fontWeight: 500 }}>{statusMsg}</span>}
            <button
              type="submit"
              style={{ padding: "10px 24px", background: "#3b82f6", color: "#fff", border: "none", borderRadius: "6px", fontWeight: 600, cursor: "pointer", marginLeft: "auto" }}
            >
              Save Profile
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
