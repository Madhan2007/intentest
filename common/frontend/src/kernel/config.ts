/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz React coding standards.
 */
/** Runtime config the browser is allowed to see — no DB/cache secrets (doc 08 §6). */
export interface PublicConfig {
  solutionId: string;
  profile: string;
  modules: string[];
  wsPath: string;
  apiOpzhub: string;
  apiAi: string;
  auth: { methods: string[] };
  gui: { mode: "lite" | "rich"; allowUserChoice: boolean };
}

let cached: PublicConfig | null = null;

export async function loadPublicConfig(): Promise<PublicConfig> {
  if (cached) return cached;
  const res = await fetch("/api/v1/opzhub/meta/config", { credentials: "include" });
  const body = await res.json();
  cached = body.data as PublicConfig;
  return cached;
}
