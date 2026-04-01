package main

import (
	"context"
	"errors"
	"fmt"
	"os"

	"github.com/peterbourgon/diskv/v3"
	"go.opentelemetry.io/otel/metric"
)

type tokens struct {
	d                *diskv.Diskv
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

	if tokensRegistered != nil {
		cancel := make(chan struct{})
		var count int64
		for range d.Keys(cancel) {
			count++
		}
		tokensRegistered.Add(context.Background(), count)
	}

	return tokens{d: d, tokensRegistered: tokensRegistered}, nil
}

func (t *tokens) storeDID(did, fcmToken string) {
	isNew := !t.d.Has(did)
	t.d.WriteString(did, fcmToken)
	if isNew && t.tokensRegistered != nil {
		t.tokensRegistered.Add(context.Background(), 1)
	}
}

func (t *tokens) tokenFor(did string) string {
	return t.d.ReadString(did)
}
