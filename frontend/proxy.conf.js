// Dev-server proxy. The backend defaults to :8080; if that port is taken, run the backend on another port and start the
// dev server with API_TARGET, e.g.  API_TARGET=http://localhost:8081 npm start
const target = process.env.API_TARGET || 'http://localhost:8080';

module.exports = {
  '/api': { target, secure: false },
  '/actuator': { target, secure: false },
};
