package main

import (
	"testing"

	"github.com/empijei/tst"
)

func TestTokens(t *testing.T) {
	tests := []struct {
		name       string
		store      []didToken
		wantTokens []string
		lookup     string
	}{
		{
			name:       "returns nil for unknown DID",
			store:      nil,
			lookup:     "did:plc:unknown",
			wantTokens: nil,
		},
		{
			name:       "returns stored token",
			store:      []didToken{{"did:plc:alice", "fcm-token-alice"}},
			lookup:     "did:plc:alice",
			wantTokens: []string{"fcm-token-alice"},
		},
		{
			name: "stores multiple tokens for same DID",
			store: []didToken{
				{"did:plc:bob", "token-1"},
				{"did:plc:bob", "token-2"},
			},
			lookup:     "did:plc:bob",
			wantTokens: []string{"token-1", "token-2"},
		},
		{
			name: "deduplicates same token",
			store: []didToken{
				{"did:plc:carol", "token-1"},
				{"did:plc:carol", "token-1"},
			},
			lookup:     "did:plc:carol",
			wantTokens: []string{"token-1"},
		},
		{
			name: "isolates different DIDs",
			store: []didToken{
				{"did:plc:carol", "carol-token"},
				{"did:plc:dave", "dave-token"},
			},
			lookup:     "did:plc:carol",
			wantTokens: []string{"carol-token"},
		},
		{
			name: "returns correct tokens among many",
			store: []didToken{
				{"did:plc:a", "token-a"},
				{"did:plc:b", "token-b1"},
				{"did:plc:b", "token-b2"},
				{"did:plc:c", "token-c"},
			},
			lookup:     "did:plc:b",
			wantTokens: []string{"token-b1", "token-b2"},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			dir := t.TempDir()
			tk := tst.Do(newTokens(dir, nil))(t)
			for _, dt := range tt.store {
				tk.storeDID(dt.did, dt.token)
			}
			tst.Is(tt.wantTokens, tk.tokensFor(tt.lookup), t)
		})
	}
}

type didToken struct {
	did   string
	token string
}

func TestRemoveToken(t *testing.T) {
	tests := []struct {
		name       string
		store      []didToken
		remove     string
		lookup     string
		wantTokens []string
	}{
		{
			name: "removes token when DID has multiple",
			store: []didToken{
				{"did:plc:alice", "token-1"},
				{"did:plc:alice", "token-2"},
			},
			remove:     "token-1",
			lookup:     "did:plc:alice",
			wantTokens: []string{"token-2"},
		},
		{
			name: "erases DID when last token removed",
			store: []didToken{
				{"did:plc:alice", "token-1"},
			},
			remove:     "token-1",
			lookup:     "did:plc:alice",
			wantTokens: nil,
		},
		{
			name:       "no-op when token does not exist",
			store:      []didToken{{"did:plc:alice", "token-1"}},
			remove:     "token-nonexistent",
			lookup:     "did:plc:alice",
			wantTokens: []string{"token-1"},
		},
		{
			name:       "no-op on empty store",
			store:      nil,
			remove:     "token-1",
			lookup:     "did:plc:alice",
			wantTokens: nil,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			dir := t.TempDir()
			tk := tst.Do(newTokens(dir, nil))(t)
			for _, dt := range tt.store {
				tk.storeDID(dt.did, dt.token)
			}
			tk.removeToken(tt.remove)
			tst.Is(tt.wantTokens, tk.tokensFor(tt.lookup), t)
		})
	}
}

func TestNewTokensInvalidPath(t *testing.T) {
	_, err := newTokens("/dev/null/impossible", nil)
	tst.Err("", err, t)
}
