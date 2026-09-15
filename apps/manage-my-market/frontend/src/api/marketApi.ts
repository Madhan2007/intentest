/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: API client for Manage My Market.
 */
import { httpOpzhub } from "@kernel/api/http-client";

export const marketApi = {
  // Leads
  listLeads: (filter?: { status?: string; lead_source_type?: string; search?: string; limit?: number; offset?: number }) =>
    httpOpzhub.post<any[]>("/manage-my-market/leads/read", filter || {}),

  getLead: (id: string) =>
    httpOpzhub.post<any>("/manage-my-market/leads/read", { id }),

  createLead: (data: any) =>
    httpOpzhub.post<any>("/manage-my-market/leads", data),

  updateLead: (id: string, data: any) =>
    httpOpzhub.put<any>(`/manage-my-market/leads/${id}`, data),

  disqualifyLead: (id: string, reason: string) =>
    httpOpzhub.post<any>(`/manage-my-market/leads/${id}/disqualify`, { reason }),

  assignLead: (id: string, assignedToUserId: string, reason?: string) =>
    httpOpzhub.post<any>(`/manage-my-market/leads/${id}/assign`, { assignedToUserId, reason }),

  listActivities: (leadId: string, limit = 50, offset = 0) =>
    httpOpzhub.post<any[]>(`/manage-my-market/leads/${leadId}/activity/read?limit=${limit}&offset=${offset}`),

  logActivity: (leadId: string, data: any) =>
    httpOpzhub.post<any>(`/manage-my-market/leads/${leadId}/activity`, data),

  // Campaigns
  listCampaigns: (filter?: { status?: string; search?: string; limit?: number; offset?: number }) =>
    httpOpzhub.post<any[]>("/manage-my-market/campaigns/read", filter || {}),

  createCampaign: (data: any) =>
    httpOpzhub.post<any>("/manage-my-market/campaigns", data),

  updateCampaign: (id: string, data: any) =>
    httpOpzhub.put<any>(`/manage-my-market/campaigns/${id}`, data),

  // Journeys
  listJourneys: (status?: string) =>
    httpOpzhub.post<any[]>("/manage-my-market/journeys/read", { status }),

  createJourney: (data: any) =>
    httpOpzhub.post<any>("/manage-my-market/journeys", data),

  // Calls
  listCallQueues: (status = "ACTIVE") =>
    httpOpzhub.post<any[]>("/manage-my-market/call-queues/read", { status }),

  listQueueItems: (queueId: string, status?: string) =>
    httpOpzhub.post<any[]>(`/manage-my-market/call-queues/${queueId}/items/read`, { status }),

  createCallLog: (data: any) =>
    httpOpzhub.post<any>("/manage-my-market/call-queues/items/" + (data.callQueueItemId || "general") + "/log", data),

  updateQueueItem: (itemId: string, data: any) =>
    httpOpzhub.put<any>(`/manage-my-market/call-queues/items/${itemId}`, data),

  // Referrers
  listReferrers: (status?: string) =>
    httpOpzhub.post<any[]>("/manage-my-market/referrers/read", { status }),

  createReferrer: (data: any) =>
    httpOpzhub.post<any>("/manage-my-market/referrers", data),

  // Agent Profile
  getMyProfile: () =>
    httpOpzhub.post<any>("/manage-my-market/agents/me/read"),

  updateMyProfile: (data: any) =>
    httpOpzhub.put<any>("/manage-my-market/agents/me", data),

  // Reports
  getLeadSummaryReport: (filter?: any) =>
    httpOpzhub.post<any[]>("/manage-my-market/reports/lead-summary", filter || {}),

  getLeadConversionRateReport: (filter?: any) =>
    httpOpzhub.post<any[]>("/manage-my-market/reports/lead-conversion-rate", filter || {}),

  getCampaignPerformanceReport: (filter?: any) =>
    httpOpzhub.post<any[]>("/manage-my-market/reports/campaign-performance", filter || {}),

  getChannelSpendReport: (filter?: any) =>
    httpOpzhub.post<any[]>("/manage-my-market/reports/channel-spend", filter || {}),

  getTelecallingReport: (filter?: any) =>
    httpOpzhub.post<any[]>("/manage-my-market/reports/telecalling-stats", filter || {}),

  getReferrerLeaderboardReport: (filter?: any) =>
    httpOpzhub.post<any[]>("/manage-my-market/reports/referrer-leaderboard", filter || {}),

  getFollowupComplianceReport: (filter?: any) =>
    httpOpzhub.post<any[]>("/manage-my-market/reports/followup-compliance", filter || {}),

  getJourneyFunnelReport: (filter?: any) =>
    httpOpzhub.post<any[]>("/manage-my-market/reports/journey-funnel", filter || {}),

  // Widgets
  getWidgetData: (widgetKey: string) =>
    httpOpzhub.get<any>(`/manage-my-market/widgets/${encodeURIComponent(widgetKey)}`),

  getWidgetsBatch: (widgetKeys: string[]) =>
    httpOpzhub.post<Record<string, any>>("/manage-my-market/widgets/batch", widgetKeys),
};
