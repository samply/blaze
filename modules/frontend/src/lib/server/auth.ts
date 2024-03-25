import { SvelteKitAuth } from '@auth/sveltekit';
import Keycloak from '@auth/sveltekit/providers/keycloak';
import { env } from '$env/dynamic/private';

export const { handle, signIn, signOut } = SvelteKitAuth({
	providers: [
		Keycloak({
			clientId: env.AUTH_CLIENT_ID,
			clientSecret: env.AUTH_CLIENT_SECRET,
			issuer: env.AUTH_ISSUER
		})
	],
	callbacks: {
		async jwt({ token, account }) {
			if (account) {
				token.accessToken = account.access_token;
			}
			return token;
		},
		async session({ session, token }) {
			session.accessToken = token.accessToken;
			return session;
		}
	}
});
