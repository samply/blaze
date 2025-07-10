import { SvelteKitAuth } from '@auth/sveltekit';
import Keycloak from '@auth/sveltekit/providers/keycloak';
import { env } from '$env/dynamic/private';

function expiresSoon(token: { expiresAt?: number }): boolean {
  return token.expiresAt ? token.expiresAt - Date.now() < 10000 : false;
}

async function fetchTokenEndpoint(issuer: string) {
  const res = await fetch(issuer + '/.well-known/openid-configuration');

  if (!res.ok) {
    return undefined;
  }

  return (await res.json()).token_endpoint;
}

async function refreshToken(
  tokenEndpoint: string,
  clientId: string,
  clientSecret: string,
  refreshToken: string
) {
  const res = await fetch(tokenEndpoint, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: clientId,
      client_secret: clientSecret,
      grant_type: 'refresh_token',
      refresh_token: refreshToken
    }),
    method: 'POST'
  });

  if (!res.ok) {
    return undefined;
  }

  return await res.json();
}

export const { handle, signIn, signOut } = SvelteKitAuth({
  providers: [
    Keycloak({
      clientId: env.AUTH_CLIENT_ID,
      clientSecret: env.AUTH_CLIENT_SECRET,
      issuer: env.AUTH_ISSUER
    })
  ],
  trustHost: true,
  callbacks: {
    async jwt({ token, account }) {
      if (account) {
        token.accessToken = account.access_token;
        token.refreshToken = account.refresh_token;
        token.expiresAt = account.expires_at ? account.expires_at * 1000 : undefined;
      }

      if (
        expiresSoon(token) &&
        token.refreshToken &&
        env.AUTH_ISSUER &&
        env.AUTH_CLIENT_ID &&
        env.AUTH_CLIENT_SECRET
      ) {
        const tokenEndpoint = await fetchTokenEndpoint(env.AUTH_ISSUER);
        const tokens = tokenEndpoint
          ? await refreshToken(
              tokenEndpoint,
              env.AUTH_CLIENT_ID,
              env.AUTH_CLIENT_SECRET,
              token.refreshToken
            )
          : undefined;

        if (tokens && tokens.expires_in) {
          token.accessToken = tokens.access_token;
          token.refreshToken = tokens.refresh_token;
          token.expiresAt = Date.now() + tokens.expires_in * 1000;
        }
      }

      return token;
    },
    async session({ session, token }) {
      session.accessToken = token.accessToken;
      session.refreshToken = token.refreshToken;
      session.expiresAt = token.expiresAt;
      return session;
    }
  }
});
