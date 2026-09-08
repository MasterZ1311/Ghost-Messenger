import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { InMemoryPreKeyStore } from './store/InMemoryPreKeyStore.js';
import { PresenceManager } from './store/PresenceManager.js';
import { createPreKeyRouter } from './routes/prekeys.js';

/**
 * Creates and configures the Express application.
 */
export function createApp() {
  const app = express();
  const preKeyStore = new InMemoryPreKeyStore();
  const presenceManager = new PresenceManager();

  // Security headers & CORS
  app.use(helmet());
  app.use(cors({ origin: '*' }));
  app.use(express.json({ limit: '1mb' }));

  // Basic rate limiting: 500 requests per 15 minutes
  const limiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 500,
    standardHeaders: true,
    legacyHeaders: false
  });
  app.use('/api/', limiter);

  // Mount routes
  app.use('/api/prekeys', createPreKeyRouter(preKeyStore, presenceManager));

  // Health check root
  app.get('/', (req, res) => {
    res.json({
      name: 'Calypso Ephemeral Signaling Service',
      version: '1.0.0',
      status: 'active'
    });
  });

  return { app, preKeyStore, presenceManager };
}
