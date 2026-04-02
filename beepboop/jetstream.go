package main

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"math/rand/v2"

	"firebase.google.com/go/v4/messaging"
	"github.com/bluesky-social/indigo/api/atproto"
	"github.com/bluesky-social/indigo/api/bsky"
	"github.com/bluesky-social/indigo/atproto/syntax"
	lexutil "github.com/bluesky-social/indigo/lex/util"
	"github.com/bluesky-social/jetstream/pkg/client"
	"github.com/bluesky-social/jetstream/pkg/models"
	"github.com/jellydator/ttlcache/v3"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/metric"
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

func newProfileCache(atc lexutil.LexClient) *ttlcache.Cache[string, *bsky.ActorDefs_ProfileViewDetailed] {
	loader := ttlcache.LoaderFunc[string, *bsky.ActorDefs_ProfileViewDetailed](
		func(c *ttlcache.Cache[string, *bsky.ActorDefs_ProfileViewDetailed], key string) *ttlcache.Item[string, *bsky.ActorDefs_ProfileViewDetailed] {
			profile, err := bsky.ActorGetProfile(context.Background(), atc, key)
			if err != nil {
				return nil
			}
			item := c.Set(key, profile, ttlcache.DefaultTTL)
			return item
		},
	)

	cache := ttlcache.New[string, *bsky.ActorDefs_ProfileViewDetailed](
		ttlcache.WithTTL[string, *bsky.ActorDefs_ProfileViewDetailed](30*time.Minute),
		ttlcache.WithLoader[string, *bsky.ActorDefs_ProfileViewDetailed](loader),
	)
	go cache.Start()
	return cache
}

func newRecordCache(atc lexutil.LexClient) *ttlcache.Cache[string, *bsky.FeedPost] {
	loader := ttlcache.LoaderFunc[string, *bsky.FeedPost](
		func(c *ttlcache.Cache[string, *bsky.FeedPost], key string) *ttlcache.Item[string, *bsky.FeedPost] {
			uri, err := syntax.ParseATURI(key)
			if err != nil {
				return nil
			}
			record, err := atproto.RepoGetRecord(context.Background(), atc, "", uri.Collection().String(), uri.Authority().String(), uri.RecordKey().String())
			if err != nil {
				return nil
			}
			post, ok := record.Value.Val.(*bsky.FeedPost)
			if !ok {
				return nil
			}
			item := c.Set(key, post, ttlcache.DefaultTTL)
			return item
		},
	)

	cache := ttlcache.New[string, *bsky.FeedPost](
		ttlcache.WithTTL[string, *bsky.FeedPost](10*time.Minute),
		ttlcache.WithLoader[string, *bsky.FeedPost](loader),
	)
	go cache.Start()
	return cache
}

func handleEvent(l *zap.SugaredLogger, atc lexutil.LexClient, msg messageSender, t *tokens, m *metrics) func(ctx context.Context, e *models.Event) error {
	eh := eventHandler{
		l:            l,
		atc:          atc,
		t:            t,
		m:            m,
		profileCache: newProfileCache(atc),
		recordCache:  newRecordCache(atc),
	}

	return func(ctx context.Context, e *models.Event) error {
		if e.Kind != models.EventKindCommit {
			m.eventsSkipped.Add(ctx, 1, attrReason("non_commit"))
			return nil
		}

		if e.Commit.Operation != models.CommitOperationCreate {
			m.eventsSkipped.Add(ctx, 1, attrReason("non_create"))
			return nil
		}

		m.eventsReceived.Add(ctx, 1, attrCollection(e.Commit.Collection))
		start := time.Now()

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

		m.eventDuration.Record(ctx, time.Since(start).Milliseconds(), attrCollection(e.Commit.Collection))

		for _, message := range messages {
			l.Infow("sending notification", "kind", message.Data["kind"], "destination", message.Data["authorDid"])

			_, err = msg.Send(ctx, message)
			if err != nil {
				l.Errorw("send notification", "error", err)
				m.notificationsFailed.Add(ctx, 1, attrKind(message.Data["kind"]))
			} else {
				m.notificationsSent.Add(ctx, 1, attrKind(message.Data["kind"]))
			}
		}

		return nil
	}
}

func attrCollection(v string) metric.MeasurementOption {
	return metric.WithAttributes(attribute.String("collection", v))
}

func attrReason(v string) metric.MeasurementOption {
	return metric.WithAttributes(attribute.String("reason", v))
}

func attrKind(v string) metric.MeasurementOption {
	return metric.WithAttributes(attribute.String("kind", v))
}

func attrCache(v string) metric.MeasurementOption {
	return metric.WithAttributes(attribute.String("cache", v))
}

type eventHandler struct {
	l            *zap.SugaredLogger
	atc          lexutil.LexClient
	t            *tokens
	m            *metrics
	profileCache *ttlcache.Cache[string, *bsky.ActorDefs_ProfileViewDetailed]
	recordCache  *ttlcache.Cache[string, *bsky.FeedPost]
}

func (eh *eventHandler) getProfile(ctx context.Context, did string) (*bsky.ActorDefs_ProfileViewDetailed, error) {
	cached := eh.profileCache.Has(did)
	item := eh.profileCache.Get(did)
	if item == nil {
		eh.m.cacheMisses.Add(ctx, 1, attrCache("profile"))
		return nil, fmt.Errorf("profile not found: %s", did)
	}
	if cached {
		eh.m.cacheHits.Add(ctx, 1, attrCache("profile"))
	} else {
		eh.m.cacheMisses.Add(ctx, 1, attrCache("profile"))
	}
	return item.Value(), nil
}

func (eh *eventHandler) getRecord(ctx context.Context, uri string) (*bsky.FeedPost, error) {
	cached := eh.recordCache.Has(uri)
	item := eh.recordCache.Get(uri)
	if item == nil {
		eh.m.cacheMisses.Add(ctx, 1, attrCache("record"))
		return nil, fmt.Errorf("record not found: %s", uri)
	}
	if cached {
		eh.m.cacheHits.Add(ctx, 1, attrCache("record"))
	} else {
		eh.m.cacheMisses.Add(ctx, 1, attrCache("record"))
	}
	return item.Value(), nil
}

func profileNameAvatar(p *bsky.ActorDefs_ProfileViewDetailed) (name, avatar string) {
	if p.DisplayName == nil || *p.DisplayName == "" {
		name = p.Handle
	} else {
		name = *p.DisplayName
	}
	if p.Avatar != nil {
		avatar = *p.Avatar
	}
	return
}

func (eh *eventHandler) handleLike(
	ctx context.Context,
	e *models.Event,
	subject syntax.ATURI,
	like *bsky.FeedLike,
) ([]*messaging.Message, error) {
	tokens := eh.t.tokensFor(subject.Authority().String())
	if len(tokens) == 0 {
		eh.m.eventsSkipped.Add(ctx, 1, attrReason("no_token"))
		return nil, nil
	}

	post, err := eh.getRecord(ctx, like.Subject.Uri)
	if err != nil {
		return nil, fmt.Errorf("get like subject: %w", err)
	}

	profile, err := eh.getProfile(ctx, e.Did)
	if err != nil {
		return nil, fmt.Errorf("fetch like author: %w", err)
	}

	authorName, authorAvatar := profileNameAvatar(profile)

	var ret []*messaging.Message
	for _, token := range tokens {
		ret = append(ret, &messaging.Message{
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
		})
	}
	return ret, nil
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
	var notifyDIDs = map[string]replyData{}

	for _, f := range post.Facets {
		if f == nil {
			continue
		}

		for _, ff := range f.Features {
			if ff.RichtextFacet_Mention == nil {
				continue
			}

			mention := ff.RichtextFacet_Mention

			if tokens := eh.t.tokensFor(mention.Did); len(tokens) > 0 {
				notifyDIDs[mention.Did] = replyData{kind: "app.bsky.feed.mention"}
			}
		}
	}

	if post.Reply != nil {
		p := post.Reply.Parent
		puri, err := syntax.ParseATURI(p.Uri)
		if err != nil {
			return nil, fmt.Errorf("parse parent uri %q: %w", p.Uri, err)
		}

		did := puri.Authority().String()
		if tokens := eh.t.tokensFor(did); len(tokens) > 0 {
			notifyDIDs[did] = replyData{kind: "app.bsky.feed.reply"}
		}
	}

	if post.Embed != nil {
		var recordURI string

		switch {
		case post.Embed.EmbedRecord != nil:
			recordURI = post.Embed.EmbedRecord.Record.Uri
		case post.Embed.EmbedRecordWithMedia != nil:
			recordURI = post.Embed.EmbedRecordWithMedia.Record.Record.Uri
		}

		if recordURI != "" {
			ruri, err := syntax.ParseATURI(recordURI)
			if err != nil {
				return nil, fmt.Errorf("parse quoted post uri %q: %w", recordURI, err)
			}

			qpost, err := eh.getRecord(ctx, recordURI)
			if err != nil {
				return nil, fmt.Errorf("get quoted post: %w", err)
			}

			did := ruri.Authority().String()
			if tokens := eh.t.tokensFor(did); len(tokens) > 0 {
				notifyDIDs[did] = replyData{
					kind:              "app.bsky.feed.quote",
					quotedPostContent: qpost.Text,
					quotedPostImage:   mediaForPost(qpost, did, mediaSizeThumb),
				}
			}
		}
	}

	if len(notifyDIDs) == 0 {
		eh.m.eventsSkipped.Add(ctx, 1, attrReason("no_token"))
		return nil, nil
	}

	profile, err := eh.getProfile(ctx, e.Did)
	if err != nil {
		return nil, fmt.Errorf("fetch reply author: %w", err)
	}

	authorName, authorAvatar := profileNameAvatar(profile)

	var ret []*messaging.Message

	for did, rd := range notifyDIDs {
		var titleSuffix string

		switch rd.kind {
		case "app.bsky.feed.mention":
			titleSuffix = "mentioned you"
		case "app.bsky.feed.reply":
			titleSuffix = "replied to your post"
		case "app.bsky.feed.quote":
			titleSuffix = "quoted your post"
		}

		for _, token := range eh.t.tokensFor(did) {
			ret = append(ret, &messaging.Message{
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
				Token: token,
			})
		}
	}

	return ret, nil
}

func (eh *eventHandler) handleFollow(
	ctx context.Context,
	e *models.Event,
	follow *bsky.GraphFollow,
) ([]*messaging.Message, error) {
	tokens := eh.t.tokensFor(follow.Subject)
	if len(tokens) == 0 {
		eh.m.eventsSkipped.Add(ctx, 1, attrReason("no_token"))
		return nil, nil
	}

	profile, err := eh.getProfile(ctx, e.Did)
	if err != nil {
		return nil, fmt.Errorf("fetch follower profile: %w", err)
	}

	authorName, authorAvatar := profileNameAvatar(profile)

	var bio string
	if profile.Description != nil {
		bio = *profile.Description
	}

	var ret []*messaging.Message
	for _, token := range tokens {
		ret = append(ret, &messaging.Message{
			Data: map[string]string{
				"authorDid": e.Did,
				"title":     authorName + " has followed you!",
				"body":      bio,
				"image":     authorAvatar,
				"kind":      e.Commit.Collection,
			},
			Android: &messaging.AndroidConfig{
				Priority: "high",
			},
			Token: token,
		})
	}

	return ret, nil
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

	tokens := eh.t.tokensFor(ruri.Authority().String())
	if len(tokens) == 0 {
		eh.m.eventsSkipped.Add(ctx, 1, attrReason("no_token"))
		return nil, nil
	}

	rpost, err := eh.getRecord(ctx, repost.Subject.Uri)
	if err != nil {
		return nil, fmt.Errorf("get reposted post: %w", err)
	}

	profile, err := eh.getProfile(ctx, e.Did)
	if err != nil {
		return nil, fmt.Errorf("fetch repost author: %w", err)
	}

	authorName, authorAvatar := profileNameAvatar(profile)

	var ret []*messaging.Message
	for _, token := range tokens {
		ret = append(ret, &messaging.Message{
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
			Token: token,
		})
	}
	return ret, nil
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

