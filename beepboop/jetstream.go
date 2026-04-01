package main

import (
	"context"
	"encoding/json"
	"fmt"

	"math/rand/v2"

	"firebase.google.com/go/v4/messaging"
	"github.com/bluesky-social/indigo/api/atproto"
	"github.com/bluesky-social/indigo/api/bsky"
	"github.com/bluesky-social/indigo/atproto/syntax"
	lexutil "github.com/bluesky-social/indigo/lex/util"
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

type messageSender interface {
	Send(ctx context.Context, message *messaging.Message) (string, error)
}

func handleEvent(l *zap.SugaredLogger, atc lexutil.LexClient, msg messageSender, t *tokens) func(ctx context.Context, e *models.Event) error {
	eh := eventHandler{
		l:   l,
		atc: atc,
		t:   t,
	}

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

		messages, err := func() ([]*messaging.Message, error) {
			switch e.Commit.Collection {
			case "app.bsky.feed.like":
				lk, ok := subj.(bsky.FeedLike)
				if !ok {
					return nil, fmt.Errorf("subject should be like, but is %T", lk)
				}

				return eh.handleLike(ctx, e, uri, &lk)
			case "app.bsky.feed.repost":
				rp, ok := subj.(bsky.FeedRepost)
				if !ok {
					return nil, fmt.Errorf("subject should be repost, but is %T", rp)
				}
				return eh.handleRepost(ctx, e, uri, &rp)
			case "app.bsky.graph.follow":
				fl, ok := subj.(bsky.GraphFollow)
				if !ok {
					return nil, fmt.Errorf("subject should be follow, but is %T", fl)
				}
				return eh.handleFollow(ctx, e, &fl)
			case "app.bsky.feed.post":
				pp, ok := subj.(bsky.FeedPost)
				if !ok {
					return nil, fmt.Errorf("subject should be post, but is %T", pp)
				}
				return eh.handleReply(ctx, e, uri, &pp)
			default:
				return nil, fmt.Errorf("unknown collection %s", e.Commit.Collection)
			}
		}()
		if err != nil {
			return fmt.Errorf("create message from event: %w", err)
		}

		for _, message := range messages {
			l.Infow("sending notification", "kind", message.Data["kind"], "destination", message.Data["authorDid"])

			_, err = msg.Send(ctx, message)
			if err != nil {
				l.Errorw("send notification", "error", err)
			}
		}

		return nil
	}
}

type eventHandler struct {
	l   *zap.SugaredLogger
	atc lexutil.LexClient
	t   *tokens
}

func (eh *eventHandler) handleLike(
	ctx context.Context,
	e *models.Event,
	subject syntax.ATURI,
	like *bsky.FeedLike,
) ([]*messaging.Message, error) {
	token := eh.t.tokenFor(subject.Authority().String())
	if token == "" {
		return nil, nil
	}

	record, err := atproto.RepoGetRecord(ctx, eh.atc, like.Subject.Cid, subject.Collection().String(), subject.Authority().String(), subject.RecordKey().String())
	if err != nil {
		return nil, fmt.Errorf("get like subject: %w", err)
	}

	post, ok := record.Value.Val.(*bsky.FeedPost)
	if !ok {
		return nil, fmt.Errorf("fetch liked post not of type FeedPost but %T", record.Value.Val)
	}

	authorName, authorAvatar, err := actorNameAvatar(ctx, eh.atc, e.Did)
	if err != nil {
		return nil, fmt.Errorf("fetch like author: %w", err)
	}

	m := &messaging.Message{
		Data: map[string]string{
			"authorDid":  e.Did,
			"uri":        string(subject),
			"title":      authorName + " liked your post",
			"body":       post.Text,
			"image":      authorAvatar,
			"embedImage": mediaForPost(post, subject.Authority().String(), mediaSizeThumb),
			"kind":       e.Commit.Collection,
		},
		Android: &messaging.AndroidConfig{
			Priority: "high",
		},
		Token: token,
	}
	return []*messaging.Message{m}, nil
}

func (eh *eventHandler) handleReply(
	ctx context.Context,
	e *models.Event,
	subject syntax.ATURI,
	post *bsky.FeedPost,
) ([]*messaging.Message, error) {
	type replyData struct {
		kind              string
		quotedPostContent string
		quotedPostImage   string
	}
	var mentionedDIDs = map[string]replyData{}

	for _, f := range post.Facets { // This handles mentions
		if f == nil {
			continue
		}

		for _, ff := range f.Features {
			if ff.RichtextFacet_Mention == nil {
				continue
			}

			mention := ff.RichtextFacet_Mention

			if t := eh.t.tokenFor(mention.Did); t != "" {
				mentionedDIDs[t] = replyData{kind: "app.bsky.feed.mention"}
			}
		}
	}

	if post.Reply != nil {
		p := post.Reply.Parent
		puri, err := syntax.ParseATURI(p.Uri)
		if err != nil {
			return nil, fmt.Errorf("parse parent uri %q: %w", p.Uri, err)
		}

		if t := eh.t.tokenFor(puri.Authority().String()); t != "" {
			mentionedDIDs[t] = replyData{kind: "app.bsky.feed.reply"}
		}
	}

	if post.Embed != nil {
		var recordURI, recordCID string

		switch {
		case post.Embed.EmbedRecord != nil:
			recordURI = post.Embed.EmbedRecord.Record.Uri
			recordCID = post.Embed.EmbedRecord.Record.Cid
		case post.Embed.EmbedRecordWithMedia != nil:
			recordURI = post.Embed.EmbedRecordWithMedia.Record.Record.Uri
			recordCID = post.Embed.EmbedRecordWithMedia.Record.Record.Cid
		}

		if recordURI != "" {
			ruri, err := syntax.ParseATURI(recordURI)
			if err != nil {
				return nil, fmt.Errorf("parse quoted post uri %q: %w", recordURI, err)
			}

			record, err := atproto.RepoGetRecord(ctx, eh.atc, recordCID, ruri.Collection().String(), ruri.Authority().String(), ruri.RecordKey().String())
			if err != nil {
				return nil, fmt.Errorf("get quoted post: %w", err)
			}

			qpost, ok := record.Value.Val.(*bsky.FeedPost)
			if !ok {
				return nil, fmt.Errorf("fetch quoted post not of type FeedPost but %T", record.Value.Val)
			}

			if t := eh.t.tokenFor(ruri.Authority().String()); t != "" {
				mentionedDIDs[t] = replyData{
					kind:              "app.bsky.feed.quote",
					quotedPostContent: qpost.Text,
					quotedPostImage:   mediaForPost(qpost, ruri.Authority().String(), mediaSizeThumb),
				}
			}
		}
	}

	if len(mentionedDIDs) == 0 {
		return nil, nil // nothing we're interested in
	}

	authorName, authorAvatar, err := actorNameAvatar(ctx, eh.atc, e.Did)
	if err != nil {
		return nil, fmt.Errorf("fetch like author: %w", err)
	}

	var ret []*messaging.Message

	for t, rd := range mentionedDIDs {
		var titleSuffix string

		switch rd.kind {
		case "app.bsky.feed.mention":
			titleSuffix = "mentioned you"
		case "app.bsky.feed.reply":
			titleSuffix = "replied to your post"
		case "app.bsky.feed.quote":
			titleSuffix = "quoted your post"
		}

		m := &messaging.Message{
			Data: map[string]string{
				"authorDid":        e.Did,
				"uri":              string(subject),
				"title":            authorName + " " + titleSuffix,
				"body":             post.Text,
				"image":            authorAvatar,
				"embedImage":       mediaForPost(post, e.Did, mediaSizeThumb),
				"kind":             rd.kind,
				"quotedText":       rd.quotedPostContent,
				"quotedEmbedImage": rd.quotedPostImage,
			},
			Android: &messaging.AndroidConfig{
				Priority: "high",
			},
			Token: t,
		}

		ret = append(ret, m)
	}

	return ret, nil
}

func (eh *eventHandler) handleFollow(
	ctx context.Context,
	e *models.Event,
	follow *bsky.GraphFollow,
) ([]*messaging.Message, error) {
	token := eh.t.tokenFor(follow.Subject)
	if token == "" {
		return nil, nil
	}

	profile, err := bsky.ActorGetProfile(ctx, eh.atc, e.Did)
	if err != nil {
		return nil, fmt.Errorf("fetch follower profile: %w", err)
	}

	var bio string
	if profile.Description != nil {
		bio = *profile.Description
	}

	var avatar string
	if profile.Avatar != nil {
		avatar = *profile.Avatar
	}

	m := &messaging.Message{
		Data: map[string]string{
			"authorDid": e.Did,
			"title": func() string {
				if profile.DisplayName == nil || *profile.DisplayName == "" {
					return profile.Handle
				}

				return *profile.DisplayName
			}() + " has followed you!",
			"body":  bio,
			"image": avatar,
			"kind":  e.Commit.Collection,
		},
		Android: &messaging.AndroidConfig{
			Priority: "high",
		},
		Token: token,
	}

	return []*messaging.Message{m}, nil
}

func (eh *eventHandler) handleRepost(
	ctx context.Context,
	e *models.Event,
	subject syntax.ATURI,
	repost *bsky.FeedRepost,
) ([]*messaging.Message, error) {
	ruri, err := syntax.ParseATURI(repost.Subject.Uri)
	if err != nil {
		return nil, fmt.Errorf("parse reposted post uri %q: %w", repost.Subject.Uri, err)
	}

	record, err := atproto.RepoGetRecord(ctx, eh.atc, repost.Subject.Cid, ruri.Collection().String(), ruri.Authority().String(), ruri.RecordKey().String())
	if err != nil {
		return nil, fmt.Errorf("get reposted post: %w", err)
	}

	authorName, authorAvatar, err := actorNameAvatar(ctx, eh.atc, e.Did)
	if err != nil {
		return nil, fmt.Errorf("fetch repost author: %w", err)
	}

	rpost, ok := record.Value.Val.(*bsky.FeedPost)
	if !ok {
		return nil, fmt.Errorf("fetch quoted post not of type FeedPost but %T", record.Value.Val)
	}

	if t := eh.t.tokenFor(ruri.Authority().String()); t != "" {
		return []*messaging.Message{
			{
				Data: map[string]string{
					"authorDid":  e.Did,
					"uri":        string(subject),
					"title":      authorName + " reposted your post",
					"body":       rpost.Text,
					"image":      authorAvatar,
					"embedImage": mediaForPost(rpost, ruri.Authority().String(), mediaSizeThumb),
					"kind":       e.Commit.Collection,
				},
				Android: &messaging.AndroidConfig{
					Priority: "high",
				},
				Token: t,
			},
		}, nil
	}

	return nil, nil
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
		var feedRepost bsky.FeedRepost
		if err := json.Unmarshal(e.Commit.Record, &feedRepost); err != nil {
			return "", "", fmt.Errorf("unmarshal repost %q: %w", string(e.Commit.Record), err)
		}

		uri, err := syntax.ParseATURI(feedRepost.Subject.Uri)
		if err != nil {
			return "", "", fmt.Errorf("parse repost subject %q: %w", feedRepost.Subject.Uri, err)
		}

		return feedRepost, uri, nil
	case "app.bsky.graph.follow":
		var graphFollow bsky.GraphFollow
		if err := json.Unmarshal(e.Commit.Record, &graphFollow); err != nil {
			return "", "", fmt.Errorf("unmarshal follow %q: %w", string(e.Commit.Record), err)
		}

		rawURI := fmt.Sprintf("at://%s/%s/%s", e.Did, e.Commit.Collection, e.Commit.RKey)
		uri, err := syntax.ParseATURI(rawURI)
		if err != nil {
			return "", "", fmt.Errorf("parse follow subject %q: %w", rawURI, err)
		}

		return graphFollow, uri, nil
	case "app.bsky.feed.post":
		var feedPost bsky.FeedPost
		if err := json.Unmarshal(e.Commit.Record, &feedPost); err != nil {
			return "", "", fmt.Errorf("unmarshal post %q: %w", string(e.Commit.Record), err)
		}

		rawURI := fmt.Sprintf("at://%s/%s/%s", e.Did, e.Commit.Collection, e.Commit.RKey)
		uri, err := syntax.ParseATURI(rawURI)
		if err != nil {
			return "", "", fmt.Errorf("parse post subject %q: %w", rawURI, err)
		}

		return feedPost, uri, nil
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

func actorNameAvatar(ctx context.Context, atc lexutil.LexClient, did string) (name, avatar string, err error) {
	profile, err := bsky.ActorGetProfile(ctx, atc, did)
	if err != nil {
		return "", "", err
	}

	var (
		authorName   string
		authorAvatar string
	)

	if profile.DisplayName == nil {
		authorName = profile.Handle
	} else if *profile.DisplayName == "" {
		authorName = profile.Handle
	} else {
		authorName = *profile.DisplayName
	}

	if profile.Avatar != nil {
		authorAvatar = *profile.Avatar
	}

	return authorName, authorAvatar, nil
}
