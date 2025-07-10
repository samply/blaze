export function toTitleCase(s: string): string {
  return s.substring(0, 1).toUpperCase() + s.substring(1);
}

export function startsWithUpperCase(s: string): boolean {
  const c = s.charAt(0);
  return c.toUpperCase() === c;
}

export function sortByProperty<T>(propName: keyof T) {
  return function (a: T, b: T) {
    if (a[propName] === undefined || a[propName] === null) return 1;
    if (b[propName] === undefined || b[propName] === null) return -1;
    if (a[propName] < b[propName]) return -1;
    if (a[propName] > b[propName]) return 1;
    return 0;
  };
}

export function sortByProperty2<T>(propName1: keyof T, propName2: keyof T) {
  const sort1 = sortByProperty(propName1);
  const sort2 = sortByProperty(propName2);
  return function (a: T, b: T) {
    const by1 = sort1(a, b);
    return by1 == 0 ? sort2(a, b) : by1;
  };
}

export function joinStrings(
  separator: string,
  ...values: (string | undefined)[]
): string | undefined {
  const vs = values.filter((v) => v !== undefined);
  return vs.length == 0 ? undefined : vs.join(separator);
}

export function isTabActive(url: URL, name: string): boolean {
  return (url.searchParams.get('__tab') ?? 'default') == name;
}

export function withTab(url: URL, name: string): string {
  const copy = new URL(url);
  copy.searchParams.set('__tab', name);
  return copy.toString();
}

export function removeAtIndex<T>(array: T[], i: number, defaultItem: T): T[] {
  const result = [...array.slice(0, i), ...array.slice(i + 1)];
  return result.length == 0 ? [defaultItem] : result;
}

export function insertAtIndex<T>(array: T[], i: number, item: T): T[] {
  return [...array.slice(0, i + 1), item, ...array.slice(i + 1)];
}

export function updateAtIndex<T>(array: T[], i: number, updater: (item: T) => T): T[] {
  return [...array.slice(0, i), updater(array[i]), ...array.slice(i + 1)];
}

export function moveUpAtIndex<T>(array: T[], i: number): T[] {
  return i <= 0 ? array : [...array.slice(0, i - 1), array[i], array[i - 1], ...array.slice(i + 1)];
}

export function moveDownAtIndex<T>(array: T[], i: number): T[] {
  if (i < array.length - 1) {
    return [...array.slice(0, i), array[i + 1], array[i], ...array.slice(i + 2)];
  }
  return array;
}

export const defaultCount = '20';

export function processParams(params: URLSearchParams): string {
  const newParams = new URLSearchParams();
  for (const [name, value] of params) {
    if (!name.endsWith(':inactive')) {
      newParams.append(name, value);
    }
  }
  // a default count of 20 is sufficient for the UI
  if (!newParams.has('_count')) {
    newParams.append('_count', defaultCount);
  }
  // we like to have summary representations by default
  if (!newParams.has('_summary')) {
    newParams.append('_summary', 'true');
  }
  return newParams.toString();
}
