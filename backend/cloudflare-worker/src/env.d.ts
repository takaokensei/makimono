declare namespace Cloudflare {
  interface Env {
    DB: D1Database;
    GOOGLE_CLIENT_ID: string;
    GOOGLE_CLIENT_SECRET: string;
    TOKEN_ENC_KEY: string;
    HMAC_KEY: string;
  }
}
