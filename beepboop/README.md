# beepboop

Push notification service for Monarch. Connects to the Bluesky Jetstream firehose and sends Firebase Cloud Messaging notifications for likes, reposts, replies, quotes, mentions, and follows.

## Requirements

- Go 1.26+
- A Firebase project with Cloud Messaging enabled
- A Firebase service account key (JSON file)
- A Bluesky account (app password recommended)

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `FIREBASE_KEY_JSON` | Yes | Path to the Firebase service account JSON key file |
| `HANDLE` | Yes | Bluesky handle (e.g. `user.bsky.social`) |
| `PASSWORD` | Yes | Bluesky app password |
| `LOG_OUTPUT` | No | `stdout` (default), `loki`, or `both` |
| `LOKI_URL` | No | Loki push URL (required when LOG_OUTPUT is `loki` or `both`) |
| `LOKI_USERNAME` | No | Loki basic auth username (Grafana Cloud user ID) |
| `LOKI_PASSWORD` | No | Loki basic auth password (Grafana Cloud API key) |

## Running

```bash
export FIREBASE_KEY_JSON=/path/to/firebase-key.json
export HANDLE=your.handle.bsky.social
export PASSWORD=your-app-password

go run .
```

## Docker

```bash
docker build -t beepboop .
docker run \
  -e FIREBASE_KEY_JSON=/keys/firebase-key.json \
  -e HANDLE=your.handle.bsky.social \
  -e PASSWORD=your-app-password \
  -v /path/to/firebase-key.json:/keys/firebase-key.json:ro \
  -v beepboop-tokens:/app/tokens \
  -p 9999:9999 \
  beepboop
```

## How it works

beepboop subscribes to the Bluesky Jetstream firehose filtering for `app.bsky.feed.like`, `app.bsky.feed.repost`, `app.bsky.graph.follow`, and `app.bsky.feed.post` events. When an event targets a user with a registered FCM token, it sends a push notification via Firebase.

An HTTP endpoint listens on `:9999` for device registration. Clients POST to `/subscribe` with:

```json
{
  "did": "did:plc:...",
  "token": "fcm-device-token"
}
```

FCM tokens are persisted to disk in the `./tokens` directory.
