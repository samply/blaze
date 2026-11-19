// Writes the index of the guide and the Worker routes covering it.
//
// Usage: node src/build-index-cli.ts <canonical> <resources-dir> <rendering-dir> <out-dir>
//
// Fails if a file the index names is missing from the rendering, so the Worker
// never points at a file that isn't served.

import {
  existsSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  writeFileSync,
} from "node:fs";
import { join } from "node:path";
import {
  buildIndex,
  missingFiles,
  type Resource,
  routes,
} from "./build-index.ts";

function readResources(dir: string): Resource[] {
  return readdirSync(dir)
    .filter((file) => file.endsWith(".json"))
    .sort()
    .map(
      (file) => JSON.parse(readFileSync(join(dir, file), "utf8")) as Resource,
    );
}

const [canonical, resourcesDir, renderingDir, outDir] = process.argv.slice(2);
if (!canonical || !resourcesDir || !renderingDir || !outDir) {
  console.error(
    "Usage: build-index-cli.ts <canonical> <resources-dir> <rendering-dir> <out-dir>",
  );
  process.exit(2);
}

const index = buildIndex(canonical, readResources(resourcesDir));

const missing = missingFiles(index, (file) =>
  existsSync(join(renderingDir, file)),
);
if (missing.length > 0) {
  console.error(`Files missing from the rendering in ${renderingDir}:`);
  for (const file of missing) console.error(`  ${file}`);
  process.exit(1);
}

mkdirSync(outDir, { recursive: true });
writeFileSync(
  join(outDir, "index.json"),
  `${JSON.stringify(index, null, 2)}\n`,
);
writeFileSync(
  join(outDir, "routes.txt"),
  `${routes(canonical, index).join("\n")}\n`,
);

console.log(
  `Indexed ${Object.keys(index.resources).length} resources on these routes:`,
);
for (const route of routes(canonical, index)) console.log(`  ${route}`);
