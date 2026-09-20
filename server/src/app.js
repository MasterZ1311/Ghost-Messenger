import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { InMemoryPreKeyStore } from './store/InMemoryPreKeyStore.js';
import { PresenceManager } from './store/PresenceManager.js';
import { createPreKeyRouter } from './routes/prekeys.js';

/**
 * Allowed browser origins for CORS.
 * Android native clients are unaffected by CORS (no preflight).
 * Default: empty → no browser origins allowed (correct for a pure mobile API).
 * Override with: ALLOWED_ORIGINS=http://localhost:3000,https://admin.example.com
 */
function buildCorsOriginList() {
  const raw = process.env.ALLOWED_ORIGINS || '';
  if (!raw.trim()) return false; // false = reject all browser origins
  return raw.split(',').map(o => o.trim()).filter(Boolean);
}

/**
 * Creates and configures the Express application.
 * Returns { app, preKeyStore, presenceManager } for dependency injection in tests.
 */
export function createApp() {
  const app            = express();
  const preKeyStore    = new InMemoryPreKeyStore();
  const presenceManager = new PresenceManager();
  const allowedOrigins = buildCorsOriginList();

  // ---------------------------------------------------------------------------
  // Security Headers — Helmet
  // ---------------------------------------------------------------------------
  app.use(helmet({
    // Disallow framing entirely
    frameguard:                   { action: 'deny' },
    // No browser caching for API responses
    noSniff:                      true,
    // Prevent MIME sniffing
    xssFilter:                    true,
    // Remove X-Powered-By
    hidePoweredBy:                true,
    // Strict referrer policy — send no referrer headers
    referrerPolicy:               { policy: 'no-referrer' },
    // Strict Transport Security: 1 year, includeSubDomains
    hsts: {
      maxAge:            31_536_000,
      includeSubDomains: true,
      preload:           true
    },
    // Content Security Policy — API server, no browser resources served
    contentSecurityPolicy: {
      directives: {
        defaultSrc:  ["'none'"],
        scriptSrc:   ["'none'"],
        styleSrc:    ["'none'"],
        imgSrc:      ["'none'"],
        connectSrc:  ["'self'"],
        frameSrc:    ["'none'"],
        objectSrc:   ["'none'"],
        baseUri:     ["'none'"],
        formAction:  ["'none'"]
      }
    },
    // Disable cross-origin opener policy for API (not a document)
    crossOriginOpenerPolicy:    false,
    crossOriginResourcePolicy:  { policy: 'same-origin' }
  }));

  // ---------------------------------------------------------------------------
  // CORS — allowlist only, never wildcard
  // ---------------------------------------------------------------------------
  if (allowedOrigins === false) {
    // No browser origins permitted — correct for Android-only API
    app.use(cors({ origin: false }));
  } else {
    app.use(cors({
      origin:      allowedOrigins,
      methods:     ['GET', 'POST'],
      credentials: false,
      maxAge:      600 // 10-minute preflight cache
    }));
  }

  // ---------------------------------------------------------------------------
  // Body parsing — strict size limit
  // ---------------------------------------------------------------------------
  app.use(express.json({ limit: '512kb' }));

  // ---------------------------------------------------------------------------
  // Rate limiting
  // ---------------------------------------------------------------------------

  // General API: 200 requests per 15 minutes per IP
  const generalLimiter = rateLimit({
    windowMs:       15 * 60 * 1000,
    max:            200,
    standardHeaders: true,
    legacyHeaders:  false,
    message:        { success: false, error: 'Too many requests' }
  });

  // Challenge endpoint: 10 challenges per 15 minutes per IP (tighter)
  const challengeLimiter = rateLimit({
    windowMs:       15 * 60 * 1000,
    max:            10,
    standardHeaders: true,
    legacyHeaders:  false,
    message:        { success: false, error: 'Too many requests' }
  });

  app.use('/api/', generalLimiter);
  app.use('/api/prekeys/challenge', challengeLimiter);

  // ---------------------------------------------------------------------------
  // Routes
  // ---------------------------------------------------------------------------
  app.use('/api/prekeys', createPreKeyRouter(preKeyStore, presenceManager));

  // Root greeting — used by verify-deployment.js health probe
  app.get('/', (_req, res) => {
    res.json({
      name:    'Calypso Ephemeral Signaling Service',
      version: '1.0.0',
      status:  'active'
    });
  });

  // ---------------------------------------------------------------------------
  // 404 catch-all — generic, leaks nothing
  // ---------------------------------------------------------------------------
  app.use((_req, res) => {
    res.status(404).json({ success: false, error: 'Not found' });
  });

  // ---------------------------------------------------------------------------
  // Error handler — never expose stack traces or internal messages
  // ---------------------------------------------------------------------------
  // eslint-disable-next-line no-unused-vars
  app.use((err, _req, res, _next) => {
    res.status(500).json({ success: false, error: 'Internal server error' });
  });

  return { app, preKeyStore, presenceManager };
}
