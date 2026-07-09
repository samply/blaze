import { describe, it, expect } from 'vitest';
import {
  defaultSummarySettings,
  isSummaryEnabled,
  parseSummarySettings,
  serializeSummarySettings,
  withSummaryForType,
  type SummarySettings
} from '$lib/summary.js';

describe('parseSummarySettings test', () => {
  it('falls back to the default on missing input', () => {
    expect(parseSummarySettings(undefined)).toEqual(defaultSummarySettings);
    expect(parseSummarySettings('')).toEqual(defaultSummarySettings);
  });
  it('falls back to the default on malformed JSON', () => {
    expect(parseSummarySettings('not-json')).toEqual(defaultSummarySettings);
  });
  it('falls back to the default on wrong shape', () => {
    expect(parseSummarySettings('[]')).toEqual(defaultSummarySettings);
    expect(parseSummarySettings('42')).toEqual({ default: true, perType: {} });
  });
  it('ignores a non-boolean default', () => {
    expect(parseSummarySettings('{"default":"yes"}')).toEqual({ default: true, perType: {} });
  });
  it('ignores a non-boolean perType map', () => {
    expect(parseSummarySettings('{"perType":{"Patient":"no"}}')).toEqual({
      default: true,
      perType: {}
    });
  });
  it('parses a full settings object', () => {
    expect(parseSummarySettings('{"default":false,"perType":{"Patient":true}}')).toEqual({
      default: false,
      perType: { Patient: true }
    });
  });
});

describe('serializeSummarySettings test', () => {
  it('round-trips through parse', () => {
    const settings: SummarySettings = {
      default: false,
      perType: { Patient: true, Observation: false }
    };
    expect(parseSummarySettings(serializeSummarySettings(settings))).toEqual(settings);
  });
});

describe('isSummaryEnabled test', () => {
  it('returns the default when no type is given', () => {
    expect(isSummaryEnabled({ default: true, perType: {} })).toBe(true);
    expect(isSummaryEnabled({ default: false, perType: {} })).toBe(false);
  });
  it('returns the default for an unknown type', () => {
    expect(isSummaryEnabled({ default: true, perType: { Patient: false } }, 'Observation')).toBe(
      true
    );
  });
  it('returns the per-type choice when present', () => {
    expect(isSummaryEnabled({ default: true, perType: { Patient: false } }, 'Patient')).toBe(false);
    expect(isSummaryEnabled({ default: false, perType: { Patient: true } }, 'Patient')).toBe(true);
  });
});

describe('withSummaryForType test', () => {
  it('sets the choice for a type without mutating the input', () => {
    const settings: SummarySettings = { default: true, perType: {} };
    const updated = withSummaryForType(settings, 'Patient', false);
    expect(updated).toEqual({ default: true, perType: { Patient: false } });
    expect(settings).toEqual({ default: true, perType: {} });
  });
  it('overrides an existing choice', () => {
    const settings: SummarySettings = { default: true, perType: { Patient: false } };
    expect(withSummaryForType(settings, 'Patient', true)).toEqual({
      default: true,
      perType: { Patient: true }
    });
  });
});
