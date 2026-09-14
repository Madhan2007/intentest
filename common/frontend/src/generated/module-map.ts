/** BUILD ARTIFACT — regenerate with `opzhubctl module-gen` (doc 01 §5). Do not hand-edit. */
import * as identity from "@modules/identity/frontend/index";
import * as admin from "@modules/admin/frontend/index";
import * as dashboardLayout from "@modules/dashboard-layout/frontend/index";
import * as manageMyMarket from "@apps/manage-my-market/frontend/index";
import type { ModulePlugin } from "@kernel/kernel/types";

export const MODULES: ModulePlugin[] = [identity, admin, dashboardLayout, manageMyMarket];
