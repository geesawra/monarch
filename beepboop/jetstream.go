package main

import (
	"context"
	"encoding/json"
	"fmt"

	// "log"
	"math/rand/v2"

	"firebase.google.com/go/v4/messaging"
	"github.com/bluesky-social/indigo/api/atproto"
	"github.com/bluesky-social/indigo/api/bsky"
	"github.com/bluesky-social/indigo/atproto/atclient"
	"github.com/bluesky-social/indigo/atproto/syntax"
	"github.com/bluesky-social/jetstream/pkg/client"
	"github.com/bluesky-social/jetstream/pkg/models"
	"go.uber.org/zap"
)

func jurl() string {
	hosts := []string{
		"jetstream1.us-east.bsky.network",
		"jetstream2.us-east.bsky.network",
		"jetstream1.us-west.bsky.network",
		"jetstream2.us-west.bsky.network",
	}

	selected := rand.UintN(uint(len(hosts)))

	return fmt.Sprintf("wss://%s/subscribe", hosts[selected])
}

func jetstreamConfig(lexicons ...string) *client.ClientConfig {
	return &client.ClientConfig{
		Compress:          true,
		WebsocketURL:      jurl(),
		WantedDids:        []string{},
		WantedCollections: lexicons,
		MaxSize:           0,
		ExtraHeaders: map[string]string{
			"User-Agent": "jetstream-client/v0.0.1",
		},
	}
}

func handleEvent(l *zap.SugaredLogger, atc *atclient.APIClient, msg *messaging.Client, t *tokens) func(ctx context.Context, e *models.Event) error {
	return func(ctx context.Context, e *models.Event) error {
		if e.Kind != models.EventKindCommit {
			return nil
		}

		if e.Commit.Operation != models.CommitOperationCreate {
			return nil
		}

		subj, uri, err := subject(e)
		if err != nil {
			return fmt.Errorf("read event subject: %w", err)
		}

		token := t.tokenFor(uri.Authority().String())
		if token == "" {
			return nil
		}

		l.Debugln("token for", uri.Authority().String(), "found")

		message, err := func() (*messaging.Message, error) {
			switch e.Commit.Collection {
			case "app.bsky.feed.like":
				lk, ok := subj.(bsky.FeedLike)
				if !ok {
					return nil, fmt.Errorf("subject should be like, but is %T", l)
				}

				return handleLike(ctx, l, token, e, atc, uri, &lk)
			case "app.bsky.feed.repost":
				return nil, nil
			case "app.bsky.graph.follow":
				return nil, nil
			case "app.bsky.feed.post":
				return nil, nil
			default:
				return nil, fmt.Errorf("unknown collection %s", e.Commit.Collection)
			}
		}()
		if err != nil {
			return fmt.Errorf("create message from event: %w", err)
		}

		_, err = msg.Send(ctx, message)
		if err != nil {
			return fmt.Errorf("send notification: %w", err)
		}

		return nil
	}
}

func handleLike(
	ctx context.Context,
	_ *zap.SugaredLogger,
	token string,
	e *models.Event,
	atc *atclient.APIClient,
	subject syntax.ATURI,
	like *bsky.FeedLike,
) (*messaging.Message, error) {
	record, err := atproto.RepoGetRecord(ctx, atc, like.Subject.Cid, subject.Collection().String(), subject.Authority().String(), subject.RecordKey().String())
	if err != nil {
		return nil, fmt.Errorf("get like subject: %w", err)
	}

	likeAuthor, err := bsky.ActorGetProfile(ctx, atc, e.Did)
	if err != nil {
		return nil, fmt.Errorf("fetch like author: %w", err)
	}

	var (
		authorName   string
		authorAvatar string
	)

	if likeAuthor.DisplayName == nil {
		authorName = likeAuthor.Handle
	} else {
		authorName = *likeAuthor.DisplayName
	}

	if likeAuthor.Avatar != nil {
		authorAvatar = *likeAuthor.Avatar
	}

	post, ok := record.Value.Val.(*bsky.FeedPost)
	if !ok {
		return nil, fmt.Errorf("fetch liked post not of type FeedPost but %T", record.Value.Val)
	}

	m := &messaging.Message{
		Data: map[string]string{
			"authorDid":  e.Did,
			"title":      authorName + " liked your post",
			"body":       post.Text,
			"image":      authorAvatar,
			"embedImage": mediaForPost(post, likeAuthor.Did, mediaSizeThumb),
		},
		Android: &messaging.AndroidConfig{
			Priority: "high",
		},
		Token: token,
	}
	return m, nil
}

func subject(e *models.Event) (any, syntax.ATURI, error) {
	switch e.Commit.Collection {
	case "app.bsky.feed.like":
		var feedLike bsky.FeedLike
		if err := json.Unmarshal(e.Commit.Record, &feedLike); err != nil {
			return "", "", fmt.Errorf("unmarshal like %q: %w", string(e.Commit.Record), err)
		}

		uri, err := syntax.ParseATURI(feedLike.Subject.Uri)
		if err != nil {
			return "", "", fmt.Errorf("parse like subject %q: %w", feedLike.Subject.Uri, err)
		}

		return feedLike, uri, nil
	case "app.bsky.feed.repost":
		return "", "", nil
	case "app.bsky.graph.follow":
		return "", "", nil
	case "app.bsky.feed.post":
		return "", "", nil
	default:
		return "", "", nil
	}
}

const (
	mediaSizeThumb = "feed_thumbnail"
	mediaSizeFull  = "feed_fullsize"
)

func mediaForPost(p *bsky.FeedPost, authorDid, size string) string {
	if p.Embed == nil {
		return ""
	}

	if p.Embed.EmbedImages == nil {
		return ""
	}

	if len(p.Embed.EmbedImages.Images) == 0 {
		return ""
	}

	img := p.Embed.EmbedImages.Images[0]

	return fmt.Sprintf("https://cdn.bsky.app/img/%s/plain/%s/%s@webp", size, authorDid, img.Image.Ref.String())
}
