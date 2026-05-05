package main

import (
	"context"
	"encoding/json"
	"fmt"
	"testing"

	"firebase.google.com/go/v4/messaging"
	atproto "github.com/bluesky-social/indigo/api/atproto"
	"go.uber.org/zap"
	"github.com/bluesky-social/indigo/api/bsky"
	lexutil "github.com/bluesky-social/indigo/lex/util"
	"github.com/bluesky-social/jetstream/pkg/models"
	"github.com/empijei/tst"
	cid "github.com/ipfs/go-cid"
	mh "github.com/multiformats/go-multihash"
)

type mockSender struct {
	sent []*messaging.Message
	err  error
}

func (m *mockSender) Send(_ context.Context, msg *messaging.Message) (string, error) {
	m.sent = append(m.sent, msg)
	if m.err != nil {
		return "", m.err
	}
	return "mock-id", nil
}

type mockLexClient struct {
	profiles map[string]*bsky.ActorDefs_ProfileViewDetailed
	records  map[string]*bsky.FeedPost
}

func (m *mockLexClient) LexDo(_ context.Context, method, inputEncoding, endpoint string, params map[string]any, bodyData, out any) error {
	switch endpoint {
	case "app.bsky.actor.getProfile":
		actor := params["actor"].(string)
		p, ok := m.profiles[actor]
		if !ok {
			return fmt.Errorf("profile not found: %s", actor)
		}
		o := out.(*bsky.ActorDefs_ProfileViewDetailed)
		*o = *p
	case "com.atproto.repo.getRecord":
		repo := params["repo"].(string)
		rkey := params["rkey"].(string)
		key := repo + "/" + rkey
		post, ok := m.records[key]
		if !ok {
			return fmt.Errorf("record not found: %s", key)
		}
		o := out.(*atproto.RepoGetRecord_Output)
		o.Value = &lexutil.LexiconTypeDecoder{Val: post}
		o.Uri = fmt.Sprintf("at://%s/app.bsky.feed.post/%s", repo, rkey)
	default:
		return fmt.Errorf("unexpected endpoint: %s", endpoint)
	}
	return nil
}

func testMetrics(t *testing.T) *metrics {
	t.Helper()
	m, err := newMetrics()
	if err != nil {
		t.Fatal(err)
	}
	return m
}

func testEventHandler(t *testing.T, mock *mockLexClient, tk *tokens) *eventHandler {
	t.Helper()
	return &eventHandler{
		atc:          mock,
		t:            tk,
		m:            testMetrics(t),
		throttle:     newAdaptiveThrottle(),
		profileCache: newProfileCache(zap.NewNop().Sugar(), func() {}, mock),
		recordCache:  newRecordCache(zap.NewNop().Sugar(), func() {}, mock),
	}
}

func makeEvent(did, collection, rkey string, record any) *models.Event {
	data, _ := json.Marshal(record)
	return &models.Event{
		Did:  did,
		Kind: models.EventKindCommit,
		Commit: &models.Commit{
			Collection: collection,
			Operation:  models.CommitOperationCreate,
			RKey:       rkey,
			Record:     data,
		},
	}
}

func testTokens(t *testing.T, entries map[string]string) *tokens {
	t.Helper()
	dir := t.TempDir()
	tk := tst.Do(newTokens(dir, nil))(t)
	for did, token := range entries {
		tk.storeDID(did, token)
	}
	return &tk
}

func TestSubject(t *testing.T) {
	tests := []struct {
		name       string
		event      *models.Event
		wantURI    string
		wantType   string
		wantErr    string
	}{
		{
			name: "like",
			event: makeEvent("did:plc:liker", "app.bsky.feed.like", "rkey1", bsky.FeedLike{
				Subject: &atproto.RepoStrongRef{
					Uri: "at://did:plc:author/app.bsky.feed.post/abc",
					Cid: "bafycid",
				},
				CreatedAt: "2026-01-01T00:00:00Z",
			}),
			wantURI:  "at://did:plc:author/app.bsky.feed.post/abc",
			wantType: "bsky.FeedLike",
		},
		{
			name: "repost",
			event: makeEvent("did:plc:reposter", "app.bsky.feed.repost", "rkey2", bsky.FeedRepost{
				Subject: &atproto.RepoStrongRef{
					Uri: "at://did:plc:author/app.bsky.feed.post/def",
					Cid: "bafycid2",
				},
				CreatedAt: "2026-01-01T00:00:00Z",
			}),
			wantURI:  "at://did:plc:author/app.bsky.feed.post/def",
			wantType: "bsky.FeedRepost",
		},
		{
			name: "follow",
			event: makeEvent("did:plc:follower", "app.bsky.graph.follow", "rkey3", bsky.GraphFollow{
				Subject:   "did:plc:followed",
				CreatedAt: "2026-01-01T00:00:00Z",
			}),
			wantURI:  "at://did:plc:follower/app.bsky.graph.follow/rkey3",
			wantType: "bsky.GraphFollow",
		},
		{
			name: "post",
			event: makeEvent("did:plc:poster", "app.bsky.feed.post", "rkey4", bsky.FeedPost{
				Text:      "hello world",
				CreatedAt: "2026-01-01T00:00:00Z",
			}),
			wantURI:  "at://did:plc:poster/app.bsky.feed.post/rkey4",
			wantType: "bsky.FeedPost",
		},
		{
			name: "invalid json",
			event: &models.Event{
				Did:  "did:plc:x",
				Kind: models.EventKindCommit,
				Commit: &models.Commit{
					Collection: "app.bsky.feed.like",
					Operation:  models.CommitOperationCreate,
					Record:     json.RawMessage(`{invalid`),
				},
			},
			wantErr: "unmarshal like",
		},
		{
			name: "unknown collection",
			event: makeEvent("did:plc:x", "app.bsky.unknown.type", "rkey5", struct{}{}),
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			subj, uri, err := subject(tt.event)

			if tt.wantErr != "" {
				tst.Err(tt.wantErr, err, t)
				return
			}

			tst.No(err, t)

			if tt.wantURI == "" {
				tst.Is("", string(uri), t)
				return
			}

			tst.Is(tt.wantURI, string(uri), t)

			gotType := fmt.Sprintf("%T", subj)
			tst.Is(tt.wantType, gotType, t)
		})
	}
}

func TestMediaForPost(t *testing.T) {
	tests := []struct {
		name string
		post *bsky.FeedPost
		did  string
		size string
		want string
	}{
		{
			name: "no embed",
			post: &bsky.FeedPost{Text: "hello"},
			did:  "did:plc:author",
			size: mediaSizeThumb,
			want: "",
		},
		{
			name: "embed without images",
			post: &bsky.FeedPost{
				Text:  "hello",
				Embed: &bsky.FeedPost_Embed{},
			},
			did:  "did:plc:author",
			size: mediaSizeThumb,
			want: "",
		},
		{
			name: "embed with empty images",
			post: &bsky.FeedPost{
				Text: "hello",
				Embed: &bsky.FeedPost_Embed{
					EmbedImages: &bsky.EmbedImages{Images: []*bsky.EmbedImages_Image{}},
				},
			},
			did:  "did:plc:author",
			size: mediaSizeThumb,
			want: "",
		},
		{
			name: "embed with image thumbnail",
			post: &bsky.FeedPost{
				Text: "hello",
				Embed: &bsky.FeedPost_Embed{
					EmbedImages: &bsky.EmbedImages{
						Images: []*bsky.EmbedImages_Image{
							{
								Alt:   "alt text",
								Image: &lexutil.LexBlob{Ref: lexutil.LexLink(testLexLink(t))},
							},
						},
					},
				},
			},
			did:  "did:plc:author",
			size: mediaSizeThumb,
			want: "https://cdn.bsky.app/img/feed_thumbnail/plain/did:plc:author/" + testLexLink(t).String() + "@webp",
		},
		{
			name: "full size",
			post: &bsky.FeedPost{
				Text: "hello",
				Embed: &bsky.FeedPost_Embed{
					EmbedImages: &bsky.EmbedImages{
						Images: []*bsky.EmbedImages_Image{
							{
								Image: &lexutil.LexBlob{Ref: lexutil.LexLink(testLexLink(t))},
							},
						},
					},
				},
			},
			did:  "did:plc:author",
			size: mediaSizeFull,
			want: "https://cdn.bsky.app/img/feed_fullsize/plain/did:plc:author/" + testLexLink(t).String() + "@webp",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := mediaForPost(tt.post, tt.did, tt.size)
			tst.Is(tt.want, got, t)
		})
	}
}

func testLexLink(t *testing.T) lexutil.LexLink {
	t.Helper()
	hash, err := mh.Sum([]byte("test"), mh.SHA2_256, -1)
	if err != nil {
		t.Fatal(err)
	}
	return lexutil.LexLink(cid.NewCidV1(cid.DagCBOR, hash))
}

func TestHandleLike(t *testing.T) {
	tests := []struct {
		name      string
		tokens    map[string]string
		event     *models.Event
		post      *bsky.FeedPost
		wantTitle string
		wantBody  string
		wantNil   bool
	}{
		{
			name:   "no token for author",
			tokens: map[string]string{},
			event: makeEvent("did:plc:liker", "app.bsky.feed.like", "rkey1", bsky.FeedLike{
				Subject:   &atproto.RepoStrongRef{Uri: "at://did:plc:author/app.bsky.feed.post/abc", Cid: "cid1"},
				CreatedAt: "2026-01-01T00:00:00Z",
			}),
			wantNil: true,
		},
		{
			name:   "like with display name",
			tokens: map[string]string{"did:plc:author": "fcm-token-1"},
			event: makeEvent("did:plc:liker", "app.bsky.feed.like", "rkey1", bsky.FeedLike{
				Subject:   &atproto.RepoStrongRef{Uri: "at://did:plc:author/app.bsky.feed.post/abc", Cid: "cid1"},
				CreatedAt: "2026-01-01T00:00:00Z",
			}),
			post:      &bsky.FeedPost{Text: "my cool post", CreatedAt: "2026-01-01T00:00:00Z"},
			wantTitle: "Liker User liked your post",
			wantBody:  "my cool post",
		},
		{
			name:   "like with handle fallback",
			tokens: map[string]string{"did:plc:author": "fcm-token-1"},
			event: makeEvent("did:plc:noname", "app.bsky.feed.like", "rkey1", bsky.FeedLike{
				Subject:   &atproto.RepoStrongRef{Uri: "at://did:plc:author/app.bsky.feed.post/abc", Cid: "cid1"},
				CreatedAt: "2026-01-01T00:00:00Z",
			}),
			post:      &bsky.FeedPost{Text: "another post", CreatedAt: "2026-01-01T00:00:00Z"},
			wantTitle: "noname.bsky.social liked your post",
			wantBody:  "another post",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			tk := testTokens(t, tt.tokens)

			mock := &mockLexClient{
				profiles: map[string]*bsky.ActorDefs_ProfileViewDetailed{
					"did:plc:liker": {
						Handle:      "liker.bsky.social",
						DisplayName: new("Liker User"),
						Avatar:      new("https://avatar.example/liker.jpg"),
					},
					"did:plc:noname": {
						Handle: "noname.bsky.social",
					},
				},
				records: map[string]*bsky.FeedPost{
					"did:plc:author/abc": tt.post,
				},
			}

			eh := testEventHandler(t, mock, tk)

			subj, uri, err := subject(tt.event)
			tst.No(err, t)

			like := subj.(bsky.FeedLike)
			msgs, err := eh.handleLike(context.Background(), tt.event, uri, &like)
			tst.No(err, t)

			if tt.wantNil {
				tst.Is(0, len(msgs), t)
				return
			}

			tst.Is(1, len(msgs), t)
			tst.Is(tt.wantTitle, msgs[0].Data["title"], t)
			tst.Is(tt.wantBody, msgs[0].Data["body"], t)
			tst.Is("high", msgs[0].Android.Priority, t)
		})
	}
}

func TestHandleFollow(t *testing.T) {
	tests := []struct {
		name      string
		tokens    map[string]string
		follower  string
		followed  string
		profile   *bsky.ActorDefs_ProfileViewDetailed
		wantTitle string
		wantBody  string
		wantNil   bool
	}{
		{
			name:     "no token for followed user",
			tokens:   map[string]string{},
			follower: "did:plc:follower",
			followed: "did:plc:followed",
			wantNil:  true,
		},
		{
			name:     "follow with display name and bio",
			tokens:   map[string]string{"did:plc:followed": "fcm-token-2"},
			follower: "did:plc:follower",
			followed: "did:plc:followed",
			profile: &bsky.ActorDefs_ProfileViewDetailed{
				Handle:      "follower.bsky.social",
				DisplayName: new("Cool Follower"),
				Avatar:      new("https://avatar.example/follower.jpg"),
				Description: new("I like cats"),
			},
			wantTitle: "Cool Follower has followed you!",
			wantBody:  "I like cats",
		},
		{
			name:     "follow with handle fallback and no bio",
			tokens:   map[string]string{"did:plc:followed": "fcm-token-2"},
			follower: "did:plc:follower2",
			followed: "did:plc:followed",
			profile: &bsky.ActorDefs_ProfileViewDetailed{
				Handle: "anon.bsky.social",
			},
			wantTitle: "anon.bsky.social has followed you!",
			wantBody:  "",
		},
		{
			name:     "follow with empty display name",
			tokens:   map[string]string{"did:plc:followed": "fcm-token-2"},
			follower: "did:plc:follower3",
			followed: "did:plc:followed",
			profile: &bsky.ActorDefs_ProfileViewDetailed{
				Handle:      "empty.bsky.social",
				DisplayName: new(""),
				Description: new("bio here"),
			},
			wantTitle: "empty.bsky.social has followed you!",
			wantBody:  "bio here",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			tk := testTokens(t, tt.tokens)

			mock := &mockLexClient{
				profiles: map[string]*bsky.ActorDefs_ProfileViewDetailed{
					tt.follower: tt.profile,
				},
			}

			eh := testEventHandler(t, mock, tk)

			follow := &bsky.GraphFollow{
				Subject:   tt.followed,
				CreatedAt: "2026-01-01T00:00:00Z",
			}

			event := makeEvent(tt.follower, "app.bsky.graph.follow", "rkey1", follow)

			msgs, err := eh.handleFollow(context.Background(), event, follow)
			tst.No(err, t)

			if tt.wantNil {
				tst.Is(0, len(msgs), t)
				return
			}

			tst.Is(1, len(msgs), t)
			tst.Is(tt.wantTitle, msgs[0].Data["title"], t)
			tst.Is(tt.wantBody, msgs[0].Data["body"], t)
			tst.Is("app.bsky.graph.follow", msgs[0].Data["kind"], t)
		})
	}
}

func TestHandleRepost(t *testing.T) {
	tests := []struct {
		name      string
		tokens    map[string]string
		reposter  string
		post      *bsky.FeedPost
		wantTitle string
		wantBody  string
		wantNil   bool
	}{
		{
			name:     "no token for post author",
			tokens:   map[string]string{},
			reposter: "did:plc:reposter",
			wantNil:  true,
		},
		{
			name:     "repost notification",
			tokens:   map[string]string{"did:plc:author": "fcm-token-3"},
			reposter: "did:plc:reposter",
			post:     &bsky.FeedPost{Text: "reposted content", CreatedAt: "2026-01-01T00:00:00Z"},
			wantTitle: "Reposter reposted your post",
			wantBody:  "reposted content",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			tk := testTokens(t, tt.tokens)

			mock := &mockLexClient{
				profiles: map[string]*bsky.ActorDefs_ProfileViewDetailed{
					"did:plc:reposter": {
						Handle:      "reposter.bsky.social",
						DisplayName: new("Reposter"),
						Avatar:      new("https://avatar.example/reposter.jpg"),
					},
				},
				records: map[string]*bsky.FeedPost{
					"did:plc:author/postkey": tt.post,
				},
			}

			eh := testEventHandler(t, mock, tk)

			repost := &bsky.FeedRepost{
				Subject: &atproto.RepoStrongRef{
					Uri: "at://did:plc:author/app.bsky.feed.post/postkey",
					Cid: "cid1",
				},
				CreatedAt: "2026-01-01T00:00:00Z",
			}

			event := makeEvent(tt.reposter, "app.bsky.feed.repost", "rkey1", repost)
			subj, uri, err := subject(event)
			tst.No(err, t)

			rp := subj.(bsky.FeedRepost)
			msgs, err := eh.handleRepost(context.Background(), event, uri, &rp)
			tst.No(err, t)

			if tt.wantNil {
				var want []*messaging.Message
				tst.Is(want, msgs, t)
				return
			}

			tst.Is(1, len(msgs), t)
			tst.Is(tt.wantTitle, msgs[0].Data["title"], t)
			tst.Is(tt.wantBody, msgs[0].Data["body"], t)
		})
	}
}

func TestHandleReply(t *testing.T) {
	tests := []struct {
		name      string
		tokens    map[string]string
		poster    string
		post      *bsky.FeedPost
		wantTitle string
		wantBody  string
		wantKind  string
		wantNil   bool
	}{
		{
			name:   "reply to registered user",
			tokens: map[string]string{"did:plc:parent": "fcm-token-4"},
			poster: "did:plc:replier",
			post: &bsky.FeedPost{
				Text:      "nice post!",
				CreatedAt: "2026-01-01T00:00:00Z",
				Reply: &bsky.FeedPost_ReplyRef{
					Parent: &atproto.RepoStrongRef{
						Uri: "at://did:plc:parent/app.bsky.feed.post/parentkey",
						Cid: "parentcid",
					},
					Root: &atproto.RepoStrongRef{
						Uri: "at://did:plc:parent/app.bsky.feed.post/parentkey",
						Cid: "parentcid",
					},
				},
			},
			wantTitle: "Replier replied to your post",
			wantBody:  "nice post!",
			wantKind:  "app.bsky.feed.reply",
		},
		{
			name:   "mention registered user",
			tokens: map[string]string{"did:plc:mentioned": "fcm-token-5"},
			poster: "did:plc:mentioner",
			post: &bsky.FeedPost{
				Text:      "hey @mentioned check this",
				CreatedAt: "2026-01-01T00:00:00Z",
				Facets: []*bsky.RichtextFacet{
					{
						Features: []*bsky.RichtextFacet_Features_Elem{
							{
								RichtextFacet_Mention: &bsky.RichtextFacet_Mention{
									Did: "did:plc:mentioned",
								},
							},
						},
						Index: &bsky.RichtextFacet_ByteSlice{ByteStart: 4, ByteEnd: 14},
					},
				},
			},
			wantTitle: "Mentioner mentioned you",
			wantBody:  "hey @mentioned check this",
			wantKind:  "app.bsky.feed.mention",
		},
		{
			name:   "quote via EmbedRecord",
			tokens: map[string]string{"did:plc:quoted": "fcm-token-6"},
			poster: "did:plc:quoter",
			post: &bsky.FeedPost{
				Text:      "look at this",
				CreatedAt: "2026-01-01T00:00:00Z",
				Embed: &bsky.FeedPost_Embed{
					EmbedRecord: &bsky.EmbedRecord{
						Record: &atproto.RepoStrongRef{
							Uri: "at://did:plc:quoted/app.bsky.feed.post/origpost",
							Cid: "cidorig",
						},
					},
				},
			},
			wantTitle: "Quoter quoted your post",
			wantBody:  "look at this",
			wantKind:  "app.bsky.feed.quote",
		},
		{
			name:   "quote via EmbedRecordWithMedia",
			tokens: map[string]string{"did:plc:quoted": "fcm-token-7"},
			poster: "did:plc:quoter",
			post: &bsky.FeedPost{
				Text:      "check this out",
				CreatedAt: "2026-01-01T00:00:00Z",
				Embed: &bsky.FeedPost_Embed{
					EmbedRecordWithMedia: &bsky.EmbedRecordWithMedia{
						Record: &bsky.EmbedRecord{
							Record: &atproto.RepoStrongRef{
								Uri: "at://did:plc:quoted/app.bsky.feed.post/origpost",
								Cid: "cidorig",
							},
						},
					},
				},
			},
			wantTitle: "Quoter quoted your post",
			wantBody:  "check this out",
			wantKind:  "app.bsky.feed.quote",
		},
		{
			name:   "no registered users involved",
			tokens: map[string]string{},
			poster: "did:plc:poster",
			post: &bsky.FeedPost{
				Text:      "just a random post",
				CreatedAt: "2026-01-01T00:00:00Z",
			},
			wantNil: true,
		},
		{
			name:   "quote without registered quoted author does not fetch record",
			tokens: map[string]string{},
			poster: "did:plc:quoter",
			post: &bsky.FeedPost{
				Text:      "quoting nobody relevant",
				CreatedAt: "2026-01-01T00:00:00Z",
				Embed: &bsky.FeedPost_Embed{
					EmbedRecord: &bsky.EmbedRecord{
						Record: &atproto.RepoStrongRef{
							Uri: "at://did:plc:unregistered/app.bsky.feed.post/nomock",
							Cid: "cidx",
						},
					},
				},
			},
			wantNil: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			tk := testTokens(t, tt.tokens)

			mock := &mockLexClient{
				profiles: map[string]*bsky.ActorDefs_ProfileViewDetailed{
					"did:plc:replier": {
						Handle:      "replier.bsky.social",
						DisplayName: new("Replier"),
						Avatar:      new("https://avatar.example/replier.jpg"),
					},
					"did:plc:mentioner": {
						Handle:      "mentioner.bsky.social",
						DisplayName: new("Mentioner"),
						Avatar:      new("https://avatar.example/mentioner.jpg"),
					},
					"did:plc:quoter": {
						Handle:      "quoter.bsky.social",
						DisplayName: new("Quoter"),
						Avatar:      new("https://avatar.example/quoter.jpg"),
					},
				},
				records: map[string]*bsky.FeedPost{
					"did:plc:quoted/origpost": {Text: "original post", CreatedAt: "2026-01-01T00:00:00Z"},
				},
			}

			eh := testEventHandler(t, mock, tk)

			event := makeEvent(tt.poster, "app.bsky.feed.post", "rkey1", tt.post)
			_, uri, err := subject(event)
			tst.No(err, t)

			msgs, err := eh.handleReply(context.Background(), event, uri, tt.post)
			tst.No(err, t)

			if tt.wantNil {
				tst.Is(0, len(msgs), t)
				return
			}

			tst.Is(1, len(msgs), t)
			tst.Is(tt.wantTitle, msgs[0].Data["title"], t)
			tst.Is(tt.wantBody, msgs[0].Data["body"], t)
			tst.Is(tt.wantKind, msgs[0].Data["kind"], t)
		})
	}
}

func TestHandleEvent(t *testing.T) {
	mock := &mockLexClient{
		profiles: map[string]*bsky.ActorDefs_ProfileViewDetailed{
			"did:plc:liker": {
				Handle:      "liker.bsky.social",
				DisplayName: new("Liker"),
			},
			"did:plc:reposter": {
				Handle:      "reposter.bsky.social",
				DisplayName: new("Reposter"),
			},
			"did:plc:follower": {
				Handle:      "follower.bsky.social",
				DisplayName: new("Follower"),
				Description: new("hello"),
			},
			"did:plc:replier": {
				Handle:      "replier.bsky.social",
				DisplayName: new("Replier"),
			},
		},
		records: map[string]*bsky.FeedPost{
			"did:plc:me/postkey": {Text: "my post", CreatedAt: "2026-01-01T00:00:00Z"},
		},
	}

	tests := []struct {
		name            string
		event           *models.Event
		tokens          map[string]string
		wantSent        int
		wantTitle       string
		wantErr         string
		wantSendErr     error
		wantTokenGone   bool
		wantTokenLookup string
	}{
		{
			name: "skips non-commit events",
			event: &models.Event{
				Kind: models.EventKindIdentity,
			},
			tokens: map[string]string{},
		},
		{
			name: "skips non-create operations",
			event: &models.Event{
				Kind: models.EventKindCommit,
				Commit: &models.Commit{
					Operation:  models.CommitOperationUpdate,
					Collection: "app.bsky.feed.like",
				},
			},
			tokens: map[string]string{},
		},
		{
			name: "routes like and sends notification",
			event: makeEvent("did:plc:liker", "app.bsky.feed.like", "rk1", bsky.FeedLike{
				Subject:   &atproto.RepoStrongRef{Uri: "at://did:plc:me/app.bsky.feed.post/postkey", Cid: "cid1"},
				CreatedAt: "2026-01-01T00:00:00Z",
			}),
			tokens:    map[string]string{"did:plc:me": "tok1"},
			wantSent:  1,
			wantTitle: "Liker liked your post",
		},
		{
			name: "routes repost and sends notification",
			event: makeEvent("did:plc:reposter", "app.bsky.feed.repost", "rk2", bsky.FeedRepost{
				Subject:   &atproto.RepoStrongRef{Uri: "at://did:plc:me/app.bsky.feed.post/postkey", Cid: "cid1"},
				CreatedAt: "2026-01-01T00:00:00Z",
			}),
			tokens:    map[string]string{"did:plc:me": "tok2"},
			wantSent:  1,
			wantTitle: "Reposter reposted your post",
		},
		{
			name: "routes follow and sends notification",
			event: makeEvent("did:plc:follower", "app.bsky.graph.follow", "rk3", bsky.GraphFollow{
				Subject:   "did:plc:me",
				CreatedAt: "2026-01-01T00:00:00Z",
			}),
			tokens:    map[string]string{"did:plc:me": "tok3"},
			wantSent:  1,
			wantTitle: "Follower has followed you!",
		},
		{
			name: "routes reply and sends notification",
			event: makeEvent("did:plc:replier", "app.bsky.feed.post", "rk4", bsky.FeedPost{
				Text:      "nice!",
				CreatedAt: "2026-01-01T00:00:00Z",
				Reply: &bsky.FeedPost_ReplyRef{
					Parent: &atproto.RepoStrongRef{Uri: "at://did:plc:me/app.bsky.feed.post/postkey", Cid: "cid1"},
					Root:   &atproto.RepoStrongRef{Uri: "at://did:plc:me/app.bsky.feed.post/postkey", Cid: "cid1"},
				},
			}),
			tokens:    map[string]string{"did:plc:me": "tok4"},
			wantSent:  1,
			wantTitle: "Replier replied to your post",
		},
		{
			name: "no notification when no token registered",
			event: makeEvent("did:plc:liker", "app.bsky.feed.like", "rk5", bsky.FeedLike{
				Subject:   &atproto.RepoStrongRef{Uri: "at://did:plc:me/app.bsky.feed.post/postkey", Cid: "cid1"},
				CreatedAt: "2026-01-01T00:00:00Z",
			}),
			tokens: map[string]string{},
		},
		{
			name:    "returns error for unknown collection",
			event:   makeEvent("did:plc:x", "app.bsky.unknown.thing", "rk6", struct{}{}),
			tokens:  map[string]string{},
			wantErr: "unknown collection",
		},
		{
			name: "post skipped before unmarshal when no registered DID in record",
			event: &models.Event{
				Did:  "did:plc:poster",
				Kind: models.EventKindCommit,
				Commit: &models.Commit{
					Collection: "app.bsky.feed.post",
					Operation:  models.CommitOperationCreate,
					RKey:       "rkey-malformed",
					Record:     []byte(`{this is not valid json at all`),
				},
			},
			tokens: map[string]string{"did:plc:someone-else": "tok-else"},
		},
		{
			name: "removes token on entity not found error",
			event: makeEvent("did:plc:liker", "app.bsky.feed.like", "rk7", bsky.FeedLike{
				Subject:   &atproto.RepoStrongRef{Uri: "at://did:plc:me/app.bsky.feed.post/postkey", Cid: "cid1"},
				CreatedAt: "2026-01-01T00:00:00Z",
			}),
			tokens:          map[string]string{"did:plc:me": "stale-tok"},
			wantSent:        1,
			wantTitle:       "Liker liked your post",
			wantSendErr:     fmt.Errorf("Requested entity was not found."),
			wantTokenGone:   true,
			wantTokenLookup: "did:plc:me",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			l, _ := zap.NewDevelopment()
			sender := &mockSender{err: tt.wantSendErr}
			tk := testTokens(t, tt.tokens)

			handler := handleEvent(l.Sugar(), mock, sender, tk, testMetrics(t), func() {})
			err := handler(context.Background(), tt.event)

			if tt.wantErr != "" {
				tst.Err(tt.wantErr, err, t)
				return
			}

			tst.No(err, t)
			tst.Is(tt.wantSent, len(sender.sent), t)
			if tt.wantSent > 0 {
				tst.Is(tt.wantTitle, sender.sent[0].Data["title"], t)
			}

			if tt.wantTokenGone {
				tst.Is([]string(nil), tk.tokensFor(tt.wantTokenLookup), t)
			}
		})
	}
}
