[![Build and Publish](https://github.com/trett/rssreader/actions/workflows/build.yml/badge.svg)](https://github.com/trett/rssreader/actions/workflows/build.yml)

# Simple RSS Reader

Written using Spring Boot 2, Vue.js/Vuetify and Typescript

Authorization based on Google OAuth2 API

## Browser Support

Supports all modern browsers as well as adapted to mobile

## License

MIT

## Environments

`SERVER_URL` - API location URL

`REDIRECT_URI` - URI using as redirect from Google OAuth service

`CLIENT_ID` - ID using for identifying app in Google OAuth service

## Build example

### Local development

```bash
cd ./scripts
CLIENT_ID="<client_id>" ./build.sh
docker-compose up -d
```

### Production build

```bash
SERVER_URL="<site_url>" REDIRECT_URI="<redirect_uri>" CLIENT_ID="<client_id>" ./build.sh
```
