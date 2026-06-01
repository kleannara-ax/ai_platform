module.exports = {
  apps: [
    {
      name: 'platform',
      script: '/home/user/webapp/start-app.sh',
      interpreter: 'bash',
      cwd: '/home/user/webapp',
      env: { NODE_ENV: 'production' },
      watch: false,
      instances: 1,
      exec_mode: 'fork',
      autorestart: false
    }
  ]
}
