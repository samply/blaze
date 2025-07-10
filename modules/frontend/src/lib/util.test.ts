import { describe, it, expect } from 'vitest';
import { toTitleCase, joinStrings, isTabActive, withTab, moveDownAtIndex } from '$lib/util.js';

describe('toTitleCase test', () => {
  it('works with empty strings', () => {
    expect(toTitleCase('')).toBe('');
  });
  it('works with single char strings', () => {
    expect(toTitleCase('a')).toBe('A');
  });
  it('does not convert the second char to upper case', () => {
    expect(toTitleCase('aa')).toBe('Aa');
  });
});

describe('joinStrings test', () => {
  it('works without any strings', () => {
    expect(joinStrings('/')).toBe(undefined);
  });
  it('works with a single string', () => {
    expect(joinStrings('/', 'a')).toBe('a');
  });
  it('works with a single undefined value', () => {
    expect(joinStrings('/', undefined)).toBe(undefined);
  });
  it('works with two strings', () => {
    expect(joinStrings('/', 'a', 'b')).toBe('a/b');
  });
  it('works with two undefined values', () => {
    expect(joinStrings('/', undefined, undefined)).toBe(undefined);
  });
  it('works with one string and one undefined value', () => {
    expect(joinStrings('/', 'a', undefined)).toBe('a');
  });
  it('works with one undefined value and one string', () => {
    expect(joinStrings('/', undefined, 'a')).toBe('a');
  });
});

describe('isTabActive test', () => {
  describe('on missing __tag query param', () => {
    it('the default tab is active', () => {
      expect(isTabActive(new URL('http://localhost'), 'default')).toBe(true);
    });
    it('other tabs are not active', () => {
      expect(isTabActive(new URL('http://localhost'), 'foo')).toBe(false);
      expect(isTabActive(new URL('http://localhost'), 'bar')).toBe(false);
    });
  });
  describe('on __tag=foo query param', () => {
    it('the default tab is not active', () => {
      expect(isTabActive(new URL('http://localhost?__tab=foo'), 'default')).toBe(false);
    });
    it('the foo tab is active', () => {
      expect(isTabActive(new URL('http://localhost?__tab=foo'), 'foo')).toBe(true);
    });
  });
});

describe('withTab test', () => {
  it('the URl will not change', () => {
    const url = new URL('http://localhost/');
    withTab(url, 'foo');
    expect(url.toString()).toBe('http://localhost/');
  });

  it('the URl will contain the __tab query param', () => {
    const url = new URL('http://localhost/foo');
    expect(withTab(url, 'bar')).toBe('http://localhost/foo?__tab=bar');
  });

  it('the URl will replace an existing the __tab query param', () => {
    const url = new URL('http://localhost/foo?__tab=bar');
    expect(withTab(url, 'baz')).toBe('http://localhost/foo?__tab=baz');
  });
});

describe('moveDownAtIndex test', () => {
  describe('with one element', () => {
    it.each([0, 1, 2])('moving the element with index %i down does not change the array', (i) => {
      expect(moveDownAtIndex([1], i)).toStrictEqual([1]);
    });
  });
  describe('with two elements', () => {
    it('move first element down', () => {
      expect(moveDownAtIndex([1, 2], 0)).toStrictEqual([2, 1]);
    });
    it.each([1, 2])('moving the element with index %i down does not change the array', (i) => {
      expect(moveDownAtIndex([1, 2], i)).toStrictEqual([1, 2]);
    });
  });
  describe('with three elements', () => {
    it('move first element down', () => {
      expect(moveDownAtIndex([1, 2, 3], 0)).toStrictEqual([2, 1, 3]);
    });
    it('move second element down', () => {
      expect(moveDownAtIndex([1, 2, 3], 1)).toStrictEqual([1, 3, 2]);
    });
    it.each([2, 3])('moving the element with index %i down does not change the array', (i) => {
      expect(moveDownAtIndex([1, 2, 3], i)).toStrictEqual([1, 2, 3]);
    });
  });
});
