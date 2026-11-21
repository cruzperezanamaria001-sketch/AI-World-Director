import express from "express";
import { config } from "./config.js";

const app = express();
app.disable("x-powered-by");
app.use(express.json({ limit: "32kb" }));

app.get("/health", (_req, res) => {
  res.status(200).json({
    ok: true,
    service: "ai-world-director-mcp",
    status: "starting",
    mcp: "not_configured",
    oauth: "not_configured",
    relay: "not_connected"
  });
});

// OAuth discovery and token validation must be implemented against the
// selected OAuth authorization server before exposing the MCP endpoint.
// Do not replace this with a static bearer token or accept unverified JWTs.
app.all("/mcp", (_req, res) => {
  res.status(503).json({
    error: "mcp_not_configured",
    message: "Configure OAuth 2.1 and the MCP Streamable HTTP transport before enabling this endpoint."
  });
});

app.listen(config.PORT, config.HOST, () => {
  console.log(`AI World Director MCP scaffold listening on ${config.HOST}:${config.PORT}`);
  console.log(`Public URL: ${config.PUBLIC_BASE_URL}/mcp`);
});
