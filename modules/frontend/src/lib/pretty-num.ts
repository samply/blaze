import prettyBytes from 'pretty-bytes';

export type Options = {
  readonly maximumFractionDigits?: number;
};

// TODO: ideally this should not use pretty-bytes and remove the unit. instead we should implement it ourselves
export default function prettyNum(number: number, options?: Options): string {
  const s = prettyBytes(number, options);
  return s.substring(0, s.length - 1);
}
