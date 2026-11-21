import { z } from "zod";

const EnvSchema = z.object({
  PUBLIC_BASE_URL: z.string().url(),
  OAUTH_ISSUER: z.string().url(),
  OAUTH_AUDIENCE: z.string().min(1),
  OAUTH_CLIENT_ID: z.string().min(1),
  OAUTH_CLIENT_SECRET: z.string().optional(),
  RELAY_SHARED_SECRET: z.string().min(32),
  RELAY_URL: z.string().url(),
  HOST: z.string().default("0.0.0.0"),
  PORT: z.coerce.number().int().positive().default(3000),
  NODE_ENV: z.enum(["development", "production", "test"]).default("development")
});

export const config = EnvSchema.parse(process.env);

if (config.NODE_ENV === "production" && !config.PUBLIC_BASE_URL.startsWith("https://")) {
  throw new Error("PUBLIC_BASE_URL must use HTTPS in production.");
}
