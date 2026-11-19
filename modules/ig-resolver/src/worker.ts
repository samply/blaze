import index from "../target/index.json" with { type: "json" };
import { handle } from "./handler.ts";
import type { Index } from "./index-types.ts";
import { type Env, originOf } from "./origin.ts";

export default {
  fetch: (request: Request, env: Env) =>
    handle(request, {
      index: index as Index,
      origin: originOf(request, env),
      fetchOrigin: fetch,
    }),
};
