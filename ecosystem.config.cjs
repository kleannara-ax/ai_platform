module.exports = {
  apps: [
    {
      name: 'platform',
      script: 'java',
      args: '-jar app/build/libs/platform-1.0.0.jar',
      cwd: '/home/user/webapp',
      env: {
        DB_USERNAME: 'platform_user',
        DB_PASSWORD: 'platform_pass123!',
        JWT_SECRET: 'c2FmZXR5LXBsYXRmb3JtLWp3dC1zZWNyZXQta2V5LWJhc2U2NC1lbmNvZGVkLTI1Ni1iaXQtc2VjdXJlLXZhbHVlLTIwMjU=',
        SERVER_PORT: '8080'
      },
      watch: false,
      instances: 1,
      exec_mode: 'fork',
      autorestart: true
    }
  ]
}
