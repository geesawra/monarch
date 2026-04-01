package main

import (
	"errors"
	"fmt"
	"os"

	"github.com/peterbourgon/diskv/v3"
)

type tokens struct {
	d *diskv.Diskv
}

func newTokens(path string) (tokens, error) {
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

	return tokens{d: d}, nil
}

func (t *tokens) storeDID(did, fcmToken string) {
	t.d.WriteString(did, fcmToken)
}

func (t *tokens) tokenFor(did string) string {
	return t.d.ReadString(did)
}
