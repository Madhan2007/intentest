/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Menu contributions for Manage My Market.
 */
import type { MenuItem } from "@kernel/kernel/types";

export const MARKET_MENU_ITEMS: MenuItem[] = [
  {
    id: "market",
    label: "Marketing",
    path: "/market/dashboard",
  },
  {
    id: "market.dashboard",
    label: "Dashboard",
    path: "/market/dashboard",
    parent: "market",
  },
  {
    id: "market.leads",
    label: "Leads",
    path: "/market/leads",
    parent: "market",
  },
  {
    id: "market.campaigns",
    label: "Campaigns",
    path: "/market/campaigns",
    parent: "market",
  },
  {
    id: "market.journeys",
    label: "Journeys",
    path: "/market/journeys",
    parent: "market",
  },
  {
    id: "market.calls",
    label: "Call Queue",
    path: "/market/calls",
    parent: "market",
  },
  {
    id: "market.referrers",
    label: "Referrers",
    path: "/market/referrers",
    parent: "market",
  },
  {
    id: "market.agent-profile",
    label: "Agent Profile",
    path: "/market/agent-profile",
    parent: "market",
  },
  {
    id: "market.reports",
    label: "Reports",
    path: "/market/reports",
    parent: "market",
  },
];
