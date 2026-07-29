import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'svelte/server';
import LoadingIndicator from '$lib/loading-indicator.svelte';

/**
 * Renders the indicator as if the query had been running for `runningMillis`.
 *
 * Server-side rendering never runs the `$effect` driving the clock, so the
 * elapsed time is fixed at the difference between the render and the start.
 * The clock is frozen so that difference is exactly `runningMillis`.
 */
function renderAfter(runningMillis: number): string {
  return render(LoadingIndicator, { props: { start: Date.now() - runningMillis } }).body;
}

describe('loading-indicator test', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('stays hidden for a query that just started', () => {
    expect(renderAfter(0)).not.toContain('loading...');
  });

  it('stays hidden at the delay', () => {
    expect(renderAfter(300)).not.toContain('loading...');
  });

  it('shows up just past the delay', () => {
    expect(renderAfter(301)).toContain('loading...');
  });

  it('rounds the elapsed seconds to one fraction digit', () => {
    expect(renderAfter(5432)).toMatch(/5[.,]4\s*s/);
  });

  it('pads whole seconds to one fraction digit', () => {
    expect(renderAfter(5000)).toMatch(/5[.,]0\s*s/);
  });
});
