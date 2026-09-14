/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Route definitions for Manage My Market.
 */
import React from "react";
import type { RouteDef } from "@kernel/kernel/types";
import { MarketDashboardPage } from "./pages/MarketDashboardPage";
import { LeadListPage } from "./pages/LeadListPage";
import { LeadDetailPage } from "./pages/LeadDetailPage";
import { CampaignListPage } from "./pages/CampaignListPage";
import { CampaignDetailPage } from "./pages/CampaignDetailPage";
import { JourneyBuilderPage } from "./pages/JourneyBuilderPage";
import { CallQueuePage } from "./pages/CallQueuePage";
import { ReferrerListPage } from "./pages/ReferrerListPage";
import { AgentProfilePage } from "./pages/AgentProfilePage";
import { MarketReportsPage } from "./pages/MarketReportsPage";

export const MARKET_ROUTES: RouteDef[] = [
  {
    path: "/market",
    element: <MarketDashboardPage />,
  },
  {
    path: "/market/dashboard",
    element: <MarketDashboardPage />,
  },
  {
    path: "/market/leads",
    element: <LeadListPage />,
  },
  {
    path: "/market/leads/:id",
    element: <LeadDetailPage />,
  },
  {
    path: "/market/campaigns",
    element: <CampaignListPage />,
  },
  {
    path: "/market/campaigns/:id",
    element: <CampaignDetailPage />,
  },
  {
    path: "/market/journeys",
    element: <JourneyBuilderPage />,
  },
  {
    path: "/market/calls",
    element: <CallQueuePage />,
  },
  {
    path: "/market/referrers",
    element: <ReferrerListPage />,
  },
  {
    path: "/market/agent-profile",
    element: <AgentProfilePage />,
  },
  {
    path: "/market/reports",
    element: <MarketReportsPage />,
  },
];
