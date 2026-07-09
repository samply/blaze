import { describe, it, expect } from 'vitest';
import { render } from 'svelte/server';
import SummaryControl from '$lib/summary-control.svelte';

const url = new URL('http://localhost/Patient');

/** Returns whether the link of the option with the given label is marked as current. */
function isCurrent(body: string, label: string): boolean {
  const options = body.split('<a').filter((s) => s.includes(`>${label}<`));
  expect(options).toHaveLength(1);
  return /aria-current="page"/.test(options[0]);
}

describe('summary control test', () => {
  it('offers all three modes for search kind', () => {
    const { body } = render(SummaryControl, { props: { url, mode: 'summary', kind: 'search' } });
    expect(body).toContain('Result');
    expect(body).toContain('>Count<');
    expect(body).toContain('>Summary<');
    expect(body).toContain('>Full<');
  });
  it('offers only summary and full for history kind', () => {
    const { body } = render(SummaryControl, { props: { url, mode: 'summary', kind: 'history' } });
    expect(body).toContain('Result');
    expect(body).not.toContain('>Count<');
    expect(body).toContain('>Summary<');
    expect(body).toContain('>Full<');
  });
  it('renders each option as a link carrying its own _summary value', () => {
    const { body } = render(SummaryControl, { props: { url, mode: 'full', kind: 'search' } });
    expect(body).toContain('href="http://localhost/Patient?_summary=count"');
    expect(body).toContain('href="http://localhost/Patient?_summary=true"');
  });
  it('marks summary as current if summary mode is active', () => {
    const { body } = render(SummaryControl, { props: { url, mode: 'summary', kind: 'search' } });
    expect(isCurrent(body, 'Summary')).toBe(true);
    expect(isCurrent(body, 'Full')).toBe(false);
    expect(isCurrent(body, 'Count')).toBe(false);
  });
  it('marks full as current if full mode is active', () => {
    const { body } = render(SummaryControl, { props: { url, mode: 'full', kind: 'search' } });
    expect(isCurrent(body, 'Summary')).toBe(false);
    expect(isCurrent(body, 'Full')).toBe(true);
    expect(isCurrent(body, 'Count')).toBe(false);
  });
  it('marks count as current if count mode is active', () => {
    const { body } = render(SummaryControl, { props: { url, mode: 'count', kind: 'search' } });
    expect(isCurrent(body, 'Summary')).toBe(false);
    expect(isCurrent(body, 'Full')).toBe(false);
    expect(isCurrent(body, 'Count')).toBe(true);
  });
  it('renders links with none current if the mode is unknown and not fixed', () => {
    const { body } = render(SummaryControl, { props: { url, kind: 'search' } });
    expect(body).toContain('Result');
    expect(body).toContain('>Count<');
    expect(body).toContain('>Summary<');
    expect(body).toContain('>Full<');
    expect(isCurrent(body, 'Summary')).toBe(false);
    expect(isCurrent(body, 'Full')).toBe(false);
    expect(isCurrent(body, 'Count')).toBe(false);
  });
  it('renders nothing if the mode is unknown and fixed', () => {
    const { body } = render(SummaryControl, { props: { url, kind: 'search', fixed: true } });
    expect(body).not.toContain('Result');
    expect(body).not.toContain('>Summary<');
  });
  it('renders spans instead of links if the mode is fixed', () => {
    const { body } = render(SummaryControl, {
      props: { url, mode: 'summary', kind: 'search', fixed: true }
    });
    expect(body).not.toContain('<a ');
    expect(body).toContain('>Summary<');
    expect(body).toContain('>Full<');
    expect(body).toContain('>Count<');
  });
  it('renders links if the mode is explicitly not fixed', () => {
    const { body } = render(SummaryControl, {
      props: { url, mode: 'summary', kind: 'search', fixed: false }
    });
    expect(body).toContain('<a ');
  });
});
