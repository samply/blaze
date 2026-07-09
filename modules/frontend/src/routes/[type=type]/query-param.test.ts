import { describe, it, expect } from 'vitest';
import { initQueryParams, submitParams } from './query-param.js';
import { SearchParamType } from '$lib/fhir.js';

describe('initQueryParams test', () => {
  it('returns empty query params on empty input', () => {
    const result = initQueryParams(new URLSearchParams());
    expect(result.queryParams).toHaveLength(1);
    expect(result.queryParams[0].name).toBe('__select');
    expect(result.queryPlan).toBe(false);
  });
  it('extracts query plan from __explain', () => {
    const result = initQueryParams(new URLSearchParams('__explain=true'));
    expect(result.queryPlan).toBe(true);
  });
  it('skips _summary param', () => {
    const result = initQueryParams(new URLSearchParams('_summary=true&gender=male'));
    expect(result.queryParams).toHaveLength(1);
    expect(result.queryParams[0].name).toBe('gender');
    expect(result.queryParams[0].value).toBe('male');
  });
  it('skips _summary=count param', () => {
    const result = initQueryParams(new URLSearchParams('_summary=count'));
    expect(result.queryParams).toHaveLength(1);
    expect(result.queryParams[0].name).toBe('__select');
  });
  it('skips _count at default value', () => {
    const result = initQueryParams(new URLSearchParams('_count=20&gender=male'));
    expect(result.queryParams).toHaveLength(1);
    expect(result.queryParams[0].name).toBe('gender');
  });
  it('keeps _count at non-default value', () => {
    const result = initQueryParams(new URLSearchParams('_count=50'));
    expect(result.queryParams).toHaveLength(1);
    expect(result.queryParams[0].name).toBe('_count');
    expect(result.queryParams[0].value).toBe('50');
  });
  it('handles inactive modifier', () => {
    const result = initQueryParams(new URLSearchParams('gender:inactive=male'));
    expect(result.queryParams).toHaveLength(1);
    expect(result.queryParams[0].name).toBe('gender');
    expect(result.queryParams[0].active).toBe(false);
    expect(result.queryParams[0].value).toBe('male');
  });
});

describe('submitParams test', () => {
  it('emits no _summary when URL has none and no query plan', () => {
    const params = submitParams(
      [{ id: 0, active: true, name: 'gender', type: SearchParamType.string, value: 'male' }],
      false,
      null
    );
    expect(params.get('_summary')).toBeNull();
    expect(params.get('gender')).toBe('male');
  });
  it('carries over _summary from URL', () => {
    const params = submitParams(
      [{ id: 0, active: true, name: 'gender', type: SearchParamType.string, value: 'male' }],
      false,
      'true'
    );
    expect(params.get('_summary')).toBe('true');
  });
  it('carries over _summary=count from URL', () => {
    const params = submitParams(
      [{ id: 0, active: true, name: 'gender', type: SearchParamType.string, value: 'male' }],
      false,
      'count'
    );
    expect(params.get('_summary')).toBe('count');
  });
  it('emits exactly one _summary even when URL has one', () => {
    // _summary should not be in query params because initQueryParams filters it out
    const params = submitParams(
      [{ id: 0, active: true, name: 'gender', type: SearchParamType.string, value: 'male' }],
      false,
      'true'
    );
    expect(params.getAll('_summary')).toHaveLength(1);
    expect(params.get('_summary')).toBe('true');
  });
  it('emits __explain when query plan is true', () => {
    const params = submitParams(
      [{ id: 0, active: true, name: 'gender', type: SearchParamType.string, value: 'male' }],
      true,
      null
    );
    expect(params.get('__explain')).toBe('true');
  });
  it('skips __select placeholder', () => {
    const params = submitParams(
      [{ id: 0, active: true, name: '__select', type: SearchParamType.special, value: '' }],
      false,
      null
    );
    expect(params.get('__select')).toBeNull();
  });
  it('skips empty values', () => {
    const params = submitParams(
      [{ id: 0, active: true, name: 'gender', type: SearchParamType.string, value: '' }],
      false,
      null
    );
    expect(params.get('gender')).toBeNull();
  });
  it('trims values', () => {
    const params = submitParams(
      [{ id: 0, active: true, name: 'gender', type: SearchParamType.string, value: '  male  ' }],
      false,
      null
    );
    expect(params.get('gender')).toBe('male');
  });
  it('adds :inactive suffix when active is false', () => {
    const params = submitParams(
      [{ id: 0, active: false, name: 'gender', type: SearchParamType.string, value: 'male' }],
      false,
      null
    );
    expect(params.get('gender:inactive')).toBe('male');
    expect(params.get('gender')).toBeNull();
  });
});
