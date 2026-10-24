import { defineParams } from '@sveltejs/kit/params';

function matchId(param: string) {
  return /^[A-Za-z0-9\-.]{1,64}$/.test(param);
}

function matchPageId(param: string) {
  return /^[A-Za-z0-9\-_]+$/.test(param);
}

function matchType(param: string) {
  return /^[A-Z]([A-Za-z0-9_]){0,254}$/.test(param);
}

function matchVid(param: string) {
  return /^\d+$/.test(param);
}

export const params = defineParams({
  id: (param) => (matchId(param) ? param : undefined),
  pageId: (param) => (matchPageId(param) ? param : undefined),
  type: (param) => (matchType(param) ? param : undefined),
  vid: (param) => (matchVid(param) ? param : undefined)
});
