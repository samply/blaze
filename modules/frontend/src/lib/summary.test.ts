import { describe, it, expect } from 'vitest';
import { summaryFromUrl, withSummaryParam } from '$lib/summary.js';

describe('summaryFromUrl test', () => {
  function params(query: string): URLSearchParams {
    return new URLSearchParams(query);
  }

  it('returns full on absent _summary', () => {
    expect(summaryFromUrl(params(''))).toEqual({ mode: 'full' });
    expect(summaryFromUrl(params('gender=male'))).toEqual({ mode: 'full' });
  });
  it('returns summary on _summary=true', () => {
    expect(summaryFromUrl(params('_summary=true'))).toEqual({ mode: 'summary' });
  });
  it('returns full on _summary=false', () => {
    expect(summaryFromUrl(params('_summary=false'))).toEqual({ mode: 'full' });
  });
  it('returns count on _summary=count', () => {
    expect(summaryFromUrl(params('_summary=count'))).toEqual({ mode: 'count' });
  });
  it('returns unknown on _summary=text and _summary=data', () => {
    expect(summaryFromUrl(params('_summary=text'))).toEqual({});
    expect(summaryFromUrl(params('_summary=data'))).toEqual({});
  });
  it('returns unknown on an unsupported _summary value', () => {
    expect(summaryFromUrl(params('_summary=foo'))).toEqual({});
  });
});

describe('withSummaryParam test', () => {
  it('sets _summary=true for summary mode', () => {
    const url = new URL('http://localhost/Patient');
    expect(withSummaryParam(url, 'summary')).toBe('http://localhost/Patient?_summary=true');
  });
  it('sets _summary=count for count mode', () => {
    const url = new URL('http://localhost/Patient');
    expect(withSummaryParam(url, 'count')).toBe('http://localhost/Patient?_summary=count');
  });
  it('deletes _summary for full mode', () => {
    const url = new URL('http://localhost/Patient?_summary=true');
    expect(withSummaryParam(url, 'full')).toBe('http://localhost/Patient');
  });
  it('does not mutate the original URL', () => {
    const url = new URL('http://localhost/Patient?_summary=true');
    withSummaryParam(url, 'full');
    expect(url.toString()).toBe('http://localhost/Patient?_summary=true');
  });
  it('leaves unrelated search params untouched', () => {
    const url = new URL('http://localhost/Patient?gender=male&_count=50');
    expect(withSummaryParam(url, 'summary')).toBe(
      'http://localhost/Patient?gender=male&_count=50&_summary=true'
    );
    expect(withSummaryParam(url, 'full')).toBe('http://localhost/Patient?gender=male&_count=50');
  });
});
