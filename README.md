[![Build and Publish](https://github.com/trett/rssreader/actions/workflows/build.yml/badge.svg)](https://github.com/trett/rssreader/actions/workflows/build.yml)

# Simple RSS Reader

Written using Spring Boot 3, Vue.js/Vuetify and Typescript

Authorization based on Google OAuth2 API

## Browser Support

Supports all modern browsers as well as adapted to mobile

## License

MIT

## Environments

`SERVER_URL` - API location URL

`CLIENT_ID` - ID using for identifying app in Google OAuth service

`CLIENT_SECRET` - Google app secret

`POSTGRES_USER` - DB user

`POSTGRES_PASSWORD` - DB password

`POSTGRES_DB` - DB name

### Local development

```bash
cd ./scripts
./build.sh && CLIENT_ID="<client_id>" CLIENT_SECRET="<client_secret>" docker-compose up -d
```

### Production deploy 

Use the `scripts/docker-compose.yml` as a starting point to prepare your installation.
