import { defineConfig } from "vite";
import { fileURLToPath } from "node:url";

// Mill links the Scala.js app into out/; Vite serves it and resolves its npm imports.
//   terminal 1: ./mill -w products.demo.fastLinkJS
//   terminal 2: npm run dev
// For a production bundle, link with fullLinkJS and set SCALAJS_OUTPUT accordingly.
const repoRoot = fileURLToPath(new URL("../..", import.meta.url));

export default defineConfig({
  resolve: {
    alias: {
      "scalajs:main.js": process.env.SCALAJS_OUTPUT ??
        `${repoRoot}/out/products/demo/fastLinkJS.dest/main.js`,
    },
  },
  server: {
    fs: { allow: [repoRoot] },
  },
});
