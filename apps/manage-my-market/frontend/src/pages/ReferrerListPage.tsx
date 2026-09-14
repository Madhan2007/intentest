/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Referral Partner leaderboard and commission reward management.
 */
import React, { useEffect, useState } from "react";
import { marketApi } from "../api/marketApi";

export const ReferrerListPage: React.FC = () => {
  const [referrers, setReferrers] = useState<any[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [showModal, setShowModal] = useState<boolean>(false);
  const [formData, setFormData] = useState({
    name: "",
    referrerType: "AFFILIATE",
    email: "",
    phone: "",
    commissionRate: 10.0,
  });

  const loadData = () => {
    setLoading(true);
    marketApi.listReferrers()
      .then((res) => {
        setReferrers(Array.isArray(res) ? res : []);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    marketApi.createReferrer(formData).then(() => {
      setShowModal(false);
      loadData();
    });
  };

  return (
    <div style={{ padding: "24px", maxWidth: "1400px", margin: "0 auto" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "24px" }}>
        <div>
          <h1 style={{ margin: "0 0 4px 0", fontSize: "24px", color: "#fff", fontWeight: 700 }}>
            Referral & Affiliate Network
          </h1>
          <p style={{ margin: 0, fontSize: "13px", color: "var(--opz-color-muted, #9aa3af)" }}>
            Manage referral partners, track affiliate conversions, and disburse commission rewards.
          </p>
        </div>
        <button
          onClick={() => setShowModal(true)}
          style={{ padding: "9px 18px", background: "#ec4899", color: "#fff", border: "none", borderRadius: "6px", fontSize: "13px", fontWeight: 600, cursor: "pointer" }}
        >
          + Add Referrer
        </button>
      </div>

      <div style={{ background: "var(--opz-color-surface, #171a21)", borderRadius: "8px", border: "1px solid var(--opz-color-border, #2a2f3a)", overflow: "hidden" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "13px" }}>
          <thead>
            <tr style={{ background: "#0f1115", borderBottom: "1px solid #2a2f3a", color: "#94a3b8", textAlign: "left" }}>
              <th style={{ padding: "12px 16px" }}>Code</th>
              <th style={{ padding: "12px 16px" }}>Partner Name</th>
              <th style={{ padding: "12px 16px" }}>Type</th>
              <th style={{ padding: "12px 16px" }}>Total Referrals</th>
              <th style={{ padding: "12px 16px" }}>Conversions</th>
              <th style={{ padding: "12px 16px" }}>Rewards Earned</th>
              <th style={{ padding: "12px 16px" }}>Pending Rewards</th>
              <th style={{ padding: "12px 16px" }}>Status</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={8} style={{ padding: "32px", textAlign: "center", color: "#64748b" }}>Loading referrers...</td></tr>
            ) : referrers.length === 0 ? (
              <tr><td colSpan={8} style={{ padding: "32px", textAlign: "center", color: "#64748b" }}>No referral partners registered yet.</td></tr>
            ) : (
              referrers.map((r) => (
                <tr key={r.id} style={{ borderBottom: "1px solid #1f242e" }}>
                  <td style={{ padding: "12px 16px", fontWeight: 700, color: "#ec4899" }}>{r.referrer_code}</td>
                  <td style={{ padding: "12px 16px", color: "#fff", fontWeight: 500 }}>
                    {r.name}
                    {r.email && <div style={{ fontSize: "11px", color: "#64748b" }}>{r.email}</div>}
                  </td>
                  <td style={{ padding: "12px 16px", color: "#cbd5e1" }}>{r.referrer_type}</td>
                  <td style={{ padding: "12px 16px", color: "#38bdf8", fontWeight: 600 }}>{r.total_referrals || 0}</td>
                  <td style={{ padding: "12px 16px", color: "#10b981", fontWeight: 600 }}>{r.successful_conversions || 0}</td>
                  <td style={{ padding: "12px 16px", color: "#e2e8f0" }}>${Number(r.total_rewards_earned || 0).toLocaleString()}</td>
                  <td style={{ padding: "12px 16px", color: "#f59e0b" }}>${Number(r.pending_rewards || 0).toLocaleString()}</td>
                  <td style={{ padding: "12px 16px" }}>
                    <span style={{ padding: "2px 8px", borderRadius: "4px", fontSize: "11px", fontWeight: 600, background: "#10b98122", color: "#10b981" }}>
                      {r.status}
                    </span>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Modal */}
      {showModal && (
        <div style={{ position: "fixed", top: 0, left: 0, right: 0, bottom: 0, background: "rgba(0,0,0,0.75)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#171a21", padding: "24px", borderRadius: "10px", width: "450px", border: "1px solid #2a2f3a" }}>
            <h3 style={{ margin: "0 0 16px 0", color: "#fff" }}>Register Referrer Partner</h3>
            <form onSubmit={handleCreate} style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
              <div>
                <label style={{ fontSize: "12px", color: "#94a3b8", display: "block", marginBottom: "4px" }}>Partner Name</label>
                <input
                  type="text"
                  required
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "8px", borderRadius: "6px" }}
                />
              </div>

              <div>
                <label style={{ fontSize: "12px", color: "#94a3b8", display: "block", marginBottom: "4px" }}>Type</label>
                <select
                  value={formData.referrerType}
                  onChange={(e) => setFormData({ ...formData, referrerType: e.target.value })}
                  style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "8px", borderRadius: "6px" }}
                >
                  <option value="AFFILIATE">Affiliate</option>
                  <option value="CUSTOMER">Existing Customer</option>
                  <option value="PARTNER">Agency / Partner</option>
                  <option value="EMPLOYEE">Employee</option>
                </select>
              </div>

              <div>
                <label style={{ fontSize: "12px", color: "#94a3b8", display: "block", marginBottom: "4px" }}>Email</label>
                <input
                  type="email"
                  value={formData.email}
                  onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                  style={{ width: "100%", background: "#0f1115", border: "1px solid #2a2f3a", color: "#fff", padding: "8px", borderRadius: "6px" }}
                />
              </div>

              <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px", marginTop: "12px" }}>
                <button type="button" onClick={() => setShowModal(false)} style={{ padding: "8px 14px", background: "transparent", border: "1px solid #334155", color: "#94a3b8", borderRadius: "6px" }}>
                  Cancel
                </button>
                <button type="submit" style={{ padding: "8px 18px", background: "#ec4899", color: "#fff", border: "none", borderRadius: "6px", fontWeight: 600 }}>
                  Save Referrer
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
