import { describe, it, expect, vi } from 'vitest';
import { render } from 'svelte/server';

const env = vi.hoisted(() => ({ enableAuth: true }));
vi.mock('$app/env/public', () => ({
  get ENABLE_AUTH() {
    return env.enableAuth;
  }
}));
vi.mock('$app/state', () => ({
  page: {
    url: new URL('http://localhost/fhir'),
    route: { id: '/' },
    data: { session: { user: { name: 'John' } } }
  }
}));
vi.mock('$app/navigation', () => ({ onNavigate: () => {}, refreshAll: () => {} }));
vi.mock('#lib/auth-client.js', () => ({ signOut: () => {} }));

const { default: Layout } = await import('./+layout.svelte');

function renderLayout(enableAuth: boolean) {
  env.enableAuth = enableAuth;
  return render(Layout, { props: {} }).body;
}

describe('layout test', () => {
  it('shows the admin section and the user menu with auth', () => {
    const body = renderLayout(true);

    expect(body).toContain('href="/fhir/__admin"');
    expect(body).toContain('id="user-menu-button"');
  });

  // Without auth, everybody could use the admin section, and there is nobody
  // to sign out.
  it('hides the admin section and the user menu without auth', () => {
    const body = renderLayout(false);

    expect(body).toContain('href="/fhir/metadata"');
    expect(body).not.toContain('__admin');
    expect(body).not.toContain('id="user-menu-button"');
  });
});
