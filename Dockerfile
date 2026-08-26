FROM node:20-alpine

WORKDIR /app

# Copy dependency manifests
COPY server/package*.json ./

# Install production dependencies
RUN npm ci --only=production

# Copy application source
COPY server/src/ ./src/

# Expose default port (Railway automatically injects $PORT at runtime)
EXPOSE 3000

ENV NODE_ENV=production
ENV PORT=3000

CMD ["npm", "start"]
