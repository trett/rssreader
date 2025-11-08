[![Build and Publish](https://github.com/trett/rssreader/actions/workflows/build.yml/badge.svg)](https://github.com/trett/rssreader/actions/workflows/build.yml)

# Simple RSS Reader

Written using Scala and ScalaJs/Laminar with UI5 components

Authorization based on Google OAuth2 API

## Summary

The application can generate a summary of all unread feeds.

## Browser Support

Supports all modern browsers as well as adapted to mobile

## License

MIT

## Environments

`SERVER_URL` - API location URL

`CORS_URL` - Cors accepted paths

`CLIENT_ID` - ID using for identifying app in Google OAuth service

`CLIENT_SECRET` - Google app secret

`DATASOURCE_USER` - DB user

`DATASOURCE_PASS` - DB password

`DATASOURCE_URL` - DB url

`GOOGLE_API_KEY` - Google AI studio token

### Local development

```bash
sbt buildImages
```

### Production deploy 

Use the `scripts/local-docker/docker-compose.yml` as a starting point to prepare your installation.
