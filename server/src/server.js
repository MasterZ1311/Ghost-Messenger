import http from 'http';
import { Server } from 'socket.io';
import dotenv from 'dotenv';
import { createApp } from './app.js';
import { setupSignalingHandlers } from './sockets/signalingHandler.js';

dotenv.config();

const PORT = process.env.PORT || 3000;
const { app, presenceManager } = createApp();

const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: '*',
    methods: ['GET', 'POST']
  },
  pingInterval: 10000,
  pingTimeout: 5000
});

setupSignalingHandlers(io, presenceManager);

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[Ghost Signaling] Ephemeral Signaling Server active on port ${PORT}`);
  console.log(`[Ghost Signaling] Local: http://localhost:${PORT}`);
  console.log(`[Ghost Signaling] Android Emulator: http://10.0.2.2:${PORT}`);
});

export { server, io };
