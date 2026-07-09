import type { LayoutServerLoad } from './$types';
import { parseSummarySettings, summaryCookieName } from '$lib/summary.js';

export const load: LayoutServerLoad = async ({ locals, cookies }) => {
  const summarySettings = parseSummarySettings(cookies.get(summaryCookieName));
  const session = locals?.session;
  return session
    ? {
        summarySettings,
        session: {
          user: session.user
        }
      }
    : { summarySettings };
};
