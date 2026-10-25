import { describe, it, expect } from 'vitest';
import { params } from './params.js';

/**
 * Whether the param matcher `name` matches `param` the way the router uses it.
 */
function matches(name: keyof typeof params, param: string): boolean {
  const result = params[name]['~standard'].validate(param);
  return !(result instanceof Promise) && result.issues === undefined;
}

describe('id test', () => {
  it.each(['0', 'a', 'DA7Q7XGHLMYGSU5Z', 'search-param-value-index', 'a.b-c', 'a'.repeat(64)])(
    'matches %s',
    (param) => {
      expect(matches('id', param)).toBe(true);
    }
  );

  it.each(['', 'a'.repeat(65), 'a~b', 'a_b', 'a b'])("doesn't match %s", (param) => {
    expect(matches('id', param)).toBe(false);
  });
});

describe('pageId test', () => {
  it.each(['0', 'a_B-c'])('matches %s', (param) => {
    expect(matches('pageId', param)).toBe(true);
  });

  it.each(['', 'a.b', 'a~b'])("doesn't match %s", (param) => {
    expect(matches('pageId', param)).toBe(false);
  });
});

describe('type test', () => {
  it.each(['Patient', 'CodeSystem', 'A', 'A_b0', 'A'.repeat(255)])('matches %s', (param) => {
    expect(matches('type', param)).toBe(true);
  });

  it.each(['', 'patient', 'foo_Bar', '_history', 'Pat-ient', 'A'.repeat(256)])(
    "doesn't match %s",
    (param) => {
      expect(matches('type', param)).toBe(false);
    }
  );
});

describe('vid test', () => {
  it.each(['0', '42'])('matches %s', (param) => {
    expect(matches('vid', param)).toBe(true);
  });

  it.each(['', 'abc1', 'v2x', '1.0'])("doesn't match %s", (param) => {
    expect(matches('vid', param)).toBe(false);
  });
});
