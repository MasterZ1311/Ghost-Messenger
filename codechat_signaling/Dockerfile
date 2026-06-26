# ─── Build/runtime image ──────────────────────────────────────────────────────
FROM node:18-slim

# Create a non-root user for security.
RUN groupadd --system --gid 1001 codechat \
  && useradd --system --uid 1001 --gid codechat codechat

WORKDIR /app

# Install production dependencies only.
COPY package*.json ./
RUN npm ci --omit=dev

# Copy application source.
COPY src/ ./src/

# Drop privileges.
USER codechat

ENV NODE_ENV=production
EXPOSE 3000

# Container-level healthcheck against the liveness endpoint.
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD node -e "require('http').get('http://127.0.0.1:'+(process.env.PORT||3000)+'/health',r=>process.exit(r.statusCode===200?0:1)).on('error',()=>process.exit(1))"

CMD ["node", "src/index.js"]
