import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = async ({ locals }) => {
  const session = locals?.session;
  return session
    ? {
        session: {
          user: session.user
        }
      }
    : {};
};
