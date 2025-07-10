export function match(param) {
  return /[A-Za-z0-9\-.]{1,64}/.test(param);
}
