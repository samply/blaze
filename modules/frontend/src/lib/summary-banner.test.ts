import { describe, it, expect } from 'vitest';
import { render } from 'svelte/server';
import SummaryBanner from '$lib/summary-banner.svelte';

const url = new URL('http://localhost/Patient?_summary=true');

describe('summary banner test', () => {
  it('states that elements are hidden if summary mode is active', () => {
    const { body } = render(SummaryBanner, { props: { url, mode: 'summary' } });
    expect(body).toContain('Summary mode');
    expect(body).toContain('Show all elements');
  });
  it('links the escape hatch to full mode', () => {
    const { body } = render(SummaryBanner, { props: { url, mode: 'summary' } });
    expect(body).toContain('href="http://localhost/Patient"');
  });
  it('states that only the total is returned if count mode is active', () => {
    const countUrl = new URL('http://localhost/Patient?_summary=count');
    const { body } = render(SummaryBanner, { props: { url: countUrl, mode: 'count' } });
    expect(body).toContain('Count mode');
    expect(body).toContain('Show resources');
  });
  it('links the count escape hatch to summary mode', () => {
    const countUrl = new URL('http://localhost/Patient?_summary=count');
    const { body } = render(SummaryBanner, { props: { url: countUrl, mode: 'count' } });
    expect(body).toContain('href="http://localhost/Patient?_summary=true"');
  });
  it('renders nothing if full mode is active', () => {
    const { body } = render(SummaryBanner, { props: { url, mode: 'full' } });
    expect(body).not.toContain('Summary mode');
    expect(body).not.toContain('Count mode');
  });
  it('renders nothing if the summary mode is unknown', () => {
    const { body } = render(SummaryBanner, { props: { url } });
    expect(body).not.toContain('Summary mode');
    expect(body).not.toContain('Count mode');
  });
  it('offers no escape hatch if the mode is fixed', () => {
    const { body } = render(SummaryBanner, { props: { url, mode: 'summary', fixed: true } });
    expect(body).toContain('Summary mode');
    expect(body).not.toContain('Show all elements');
  });
  it('offers the escape hatch if the mode is explicitly not fixed', () => {
    const { body } = render(SummaryBanner, { props: { url, mode: 'summary', fixed: false } });
    expect(body).toContain('Show all elements');
  });
});
