const express = require('express');
const http = require('http');
const { Server } = require('socket.io');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: '*', // Customize for production
    methods: ['GET', 'POST']
  }
});

// Map to store connected users: UserCode -> SocketID
const activeUsers = new Map();

io.on('connection', (socket) => {
  console.log(`Socket connected: ${socket.id}`);

  // Alice joins with her derived User Code
  socket.on('join', (userCode) => {
    activeUsers.set(userCode, socket.id);
    socket.userCode = userCode;
    console.log(`User registered: ${userCode}`);
  });

  // Relay WebRTC signaling data: SDP, ICE candidates, etc.
  socket.on('signal', ({ toCode, fromCode, signalData }) => {
    const targetSocketId = activeUsers.get(toCode);
    if (targetSocketId) {
      console.log(`Relaying signal from ${fromCode} to ${toCode}`);
      io.to(targetSocketId).emit('signal', {
        fromCode,
        signalData
      });
    } else {
      console.log(`Target ${toCode} not connected. Storing transient handshake not supported.`);
      // In a more advanced version, we could queue this for a few seconds
      socket.emit('error_message', { code: 404, message: 'Recipient offline' });
    }
  });

  socket.on('disconnect', () => {
    if (socket.userCode) {
      activeUsers.delete(socket.userCode);
      console.log(`User disconnected: ${socket.userCode}`);
    }
  });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`CodeChat Signaling Server running on port ${PORT}`);
});
