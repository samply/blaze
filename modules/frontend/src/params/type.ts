export function match(param) {
  return /[A-Z]([A-Za-z0-9_]){0,254}/.test(param);
}
