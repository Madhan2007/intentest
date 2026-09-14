/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Constants for Manage My Market frontend.
 */
export const APP_KEY = "manage-my-market";
export const API_BASE = "/manage-my-market";

export const LEAD_STATUSES = [
  { value: "NEW", label: "New Lead", color: "#38bdf8" },
  { value: "CONTACTED", label: "Contacted", color: "#818cf8" },
  { value: "QUALIFIED", label: "Qualified", color: "#a855f7" },
  { value: "PROPOSAL", label: "Proposal Sent", color: "#ec4899" },
  { value: "NEGOTIATION", label: "In Negotiation", color: "#f59e0b" },
  { value: "WON", label: "Closed Won", color: "#10b981" },
  { value: "LOST", label: "Closed Lost", color: "#ef4444" },
  { value: "DISQUALIFIED", label: "Disqualified", color: "#6b7280" },
] as const;

export const LEAD_SOURCES = [
  { value: "INBOUND_WEB", label: "Website Form" },
  { value: "GOOGLE_ADS", label: "Google Ads" },
  { value: "META_ADS", label: "Meta / Instagram Ads" },
  { value: "LINKEDIN", label: "LinkedIn" },
  { value: "COLD_CALL", label: "Cold Call" },
  { value: "REFERRAL", label: "Referral Program" },
  { value: "EVENT", label: "Conference / Event" },
  { value: "PARTNER", label: "Channel Partner" },
  { value: "OTHER", label: "Other" },
] as const;

export const CAMPAIGN_STATUSES = [
  { value: "DRAFT", label: "Draft", color: "#9ca3af" },
  { value: "SCHEDULED", label: "Scheduled", color: "#38bdf8" },
  { value: "ACTIVE", label: "Running", color: "#10b981" },
  { value: "PAUSED", label: "Paused", color: "#f59e0b" },
  { value: "COMPLETED", label: "Completed", color: "#6366f1" },
  { value: "CANCELLED", label: "Cancelled", color: "#ef4444" },
] as const;

export const CALL_OUTCOMES = [
  { value: "CONNECTED", label: "Connected" },
  { value: "INTERESTED", label: "Interested" },
  { value: "NOT_INTERESTED", label: "Not Interested" },
  { value: "NO_ANSWER", label: "No Answer" },
  { value: "BUSY", label: "Busy" },
  { value: "WRONG_NUMBER", label: "Wrong Number" },
  { value: "CALLBACK_REQUESTED", label: "Callback Requested" },
] as const;
