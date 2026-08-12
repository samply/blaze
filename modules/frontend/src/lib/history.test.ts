import { describe, it, expect } from 'vitest';
import { instanceHistoryQuery, historySummaryFromUrl } from '$lib/history.js';

function params(query: string): URLSearchParams {
  return new URLSearchParams(query);
}

describe('instanceHistoryQuery test', () => {
  it('requests summary representations when summary mode is enabled', () => {
    expect(instanceHistoryQuery({ mode: 'summary' })).toBe('_count&_summary=true');
  });
  it('omits _summary when full mode is enabled', () => {
    expect(instanceHistoryQuery({ mode: 'full' })).toBe('_count');
  });
  it('omits _summary when count mode is enabled', () => {
    expect(instanceHistoryQuery({ mode: 'count' })).toBe('_count');
  });
  it('omits _summary when the mode is unknown', () => {
    expect(instanceHistoryQuery({})).toBe('_count');
  });
});

describe('historySummaryFromUrl test', () => {
  it('returns summary on _summary=true', () => {
    expect(historySummaryFromUrl(params('_summary=true'))).toEqual({ mode: 'summary' });
  });
  it('returns full on _summary=false', () => {
    expect(historySummaryFromUrl(params('_summary=false'))).toEqual({ mode: 'full' });
  });
  it('returns full on absent _summary', () => {
    expect(historySummaryFromUrl(params(''))).toEqual({ mode: 'full' });
  });
  it('returns unknown on _summary=count because the server does not honour it for history', () => {
    expect(historySummaryFromUrl(params('_summary=count'))).toEqual({});
  });
  it('returns unknown on _summary=text and _summary=data', () => {
    expect(historySummaryFromUrl(params('_summary=text'))).toEqual({});
    expect(historySummaryFromUrl(params('_summary=data'))).toEqual({});
  });
});
