module.exports = {
  apps: [
    {
      name: 'platform',
      script: 'preview-server.js',
      cwd: '/home/user/webapp',
      env: { NODE_ENV: 'production', PORT: 3000 },
      watch: false,
      instances: 1,
      exec_mode: 'fork',
      autorestart: true
    }
  ]
}
