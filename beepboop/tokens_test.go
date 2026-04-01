package main

import (
	"testing"

	"github.com/empijei/tst"
)

func TestTokens(t *testing.T) {
	tests := []struct {
		name      string
		store     map[string]string
		overwrite map[string]string
		lookup    string
		wantToken string
	}{
		{
			name:      "returns empty for unknown DID",
			store:     map[string]string{},
			lookup:    "did:plc:unknown",
			wantToken: "",
		},
		{
			name:      "returns stored token",
			store:     map[string]string{"did:plc:alice": "fcm-token-alice"},
			lookup:    "did:plc:alice",
			wantToken: "fcm-token-alice",
		},
		{
			name:      "overwrites existing token",
			store:     map[string]string{"did:plc:bob": "old-token"},
			overwrite: map[string]string{"did:plc:bob": "new-token"},
			lookup:    "did:plc:bob",
			wantToken: "new-token",
		},
		{
			name: "isolates different DIDs",
			store: map[string]string{
				"did:plc:carol": "carol-token",
				"did:plc:dave":  "dave-token",
			},
			lookup:    "did:plc:carol",
			wantToken: "carol-token",
		},
		{
			name: "returns correct token among many",
			store: map[string]string{
				"did:plc:a": "token-a",
				"did:plc:b": "token-b",
				"did:plc:c": "token-c",
			},
			lookup:    "did:plc:b",
			wantToken: "token-b",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			tk := testTokens(t, tt.store)
			for did, token := range tt.overwrite {
				tk.storeDID(did, token)
			}
			tst.Is(tt.wantToken, tk.tokenFor(tt.lookup), t)
		})
	}
}

func TestNewTokensInvalidPath(t *testing.T) {
	_, err := newTokens("/dev/null/impossible")
	tst.Err("", err, t)
}
