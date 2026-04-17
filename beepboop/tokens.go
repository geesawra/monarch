package main

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"os"
	"strings"
	"sync"

	"github.com/peterbourgon/diskv/v3"
	"go.opentelemetry.io/otel/metric"
)

type tokens struct {
	mu               sync.RWMutex
	d                *diskv.Diskv
	set              map[string]struct{}
	tokensRegistered metric.Int64UpDownCounter
}

func newTokens(path string, tokensRegistered metric.Int64UpDownCounter) (tokens, error) {
	ps, err := os.Stat(path)
	if err != nil && !errors.Is(err, os.ErrNotExist) {
		return tokens{}, fmt.Errorf("open tokens path: %w", err)
	}

	if ps != nil && !ps.IsDir() {
		return tokens{}, fmt.Errorf("%s is a file", path)
	}

	flatTransform := func(s string) []string { return []string{} }

	d := diskv.New(diskv.Options{
		BasePath:     path,
		Transform:    flatTransform,
		CacheSizeMax: 1024 * 1024,
	})

	set := map[string]struct{}{}
	cancel := make(chan struct{})
	var count int64
	for did := range d.Keys(cancel) {
		set[did] = struct{}{}
		count++
	}

	if tokensRegistered != nil {
		tokensRegistered.Add(context.Background(), count)
	}

	return tokens{d: d, set: set, tokensRegistered: tokensRegistered}, nil
}

func (t *tokens) storeDID(did, fcmToken string) {
	t.mu.Lock()
	defer t.mu.Unlock()

	isNew := !t.d.Has(did)

	existing := t.d.ReadString(did)
	if existing != "" {
		for tok := range strings.SplitSeq(existing, "\n") {
			if tok == fcmToken {
				return
			}
		}
		t.d.WriteString(did, existing+"\n"+fcmToken)
	} else {
		t.d.WriteString(did, fcmToken)
	}

	if isNew {
		t.set[did] = struct{}{}
		if t.tokensRegistered != nil {
			t.tokensRegistered.Add(context.Background(), 1)
		}
	}
}

func (t *tokens) tokensFor(did string) []string {
	t.mu.RLock()
	defer t.mu.RUnlock()

	if _, ok := t.set[did]; !ok {
		return nil
	}

	v := t.d.ReadString(did)
	if v == "" {
		return nil
	}
	return strings.Split(v, "\n")
}

func (t *tokens) anyRegisteredIn(b []byte) bool {
	t.mu.RLock()
	defer t.mu.RUnlock()
	for did := range t.set {
		if bytes.Contains(b, []byte(did)) {
			return true
		}
	}
	return false
}

func (t *tokens) removeToken(fcmToken string) {
	t.mu.Lock()
	defer t.mu.Unlock()

	cancel := make(chan struct{})
	defer close(cancel)

	for did := range t.d.Keys(cancel) {
		existing := t.d.ReadString(did)
		if existing == "" {
			continue
		}

		var remaining []string
		found := false
		for tok := range strings.SplitSeq(existing, "\n") {
			if tok == fcmToken {
				found = true
				continue
			}
			remaining = append(remaining, tok)
		}

		if !found {
			continue
		}

		if len(remaining) == 0 {
			t.d.Erase(did)
			delete(t.set, did)
			if t.tokensRegistered != nil {
				t.tokensRegistered.Add(context.Background(), -1)
			}
		} else {
			t.d.WriteString(did, strings.Join(remaining, "\n"))
		}
		return
	}
}
