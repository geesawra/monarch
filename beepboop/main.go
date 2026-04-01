package main

import (
	"context"
	"log"
	"log/slog"
	"runtime"

	firebase "firebase.google.com/go/v4"
	"github.com/bluesky-social/indigo/atproto/atclient"
	"github.com/bluesky-social/indigo/atproto/identity"
	"github.com/bluesky-social/indigo/atproto/syntax"
	"github.com/bluesky-social/jetstream/pkg/client"
	"github.com/bluesky-social/jetstream/pkg/client/schedulers/parallel"
	"github.com/sethvargo/go-envconfig"
	"go.uber.org/zap"
	"google.golang.org/api/option"
)

type Config struct {
	FirebaseKeyJSON string `env:"FIREBASE_KEY_JSON,required"`
	Handle          string `env:"HANDLE,required"`
	Password        string `env:"PASSWORD,required"`
}

func main() {
	ctx := context.Background()

	rl, _ := zap.NewDevelopment()
	l := rl.Sugar()

	var c Config
	if err := envconfig.Process(ctx, &c); err != nil {
		log.Fatal(err)
	}

	atc, err := atclient.LoginWithPassword(ctx, identity.DefaultDirectory(), syntax.AtIdentifier(c.Handle), c.Password, "", nil)
	if err != nil {
		log.Fatal(err)
	}

	t, err := newTokens("./tokens")
	if err != nil {
		log.Fatal(err)
	}

	opt := option.WithCredentialsFile(c.FirebaseKeyJSON)
	app, err := firebase.NewApp(context.Background(), nil, opt)
	if err != nil {
		log.Fatalf("error initializing app: %v\n", err)
	}

	msg, err := app.Messaging(ctx)
	if err != nil {
		log.Fatal("initialize firebase messaging:", err)
	}

	_ = msg
	sch := parallel.NewScheduler(
		runtime.NumCPU()*8,
		"processor",
		slog.Default(),
		handleEvent(l, atc, msg, &t),
	)

	jc, err := client.NewClient(
		jetstreamConfig(
			"app.bsky.feed.like",
			"app.bsky.feed.repost",
			"app.bsky.graph.follow",
			"app.bsky.feed.post",
		),
		slog.Default(),
		sch,
	)

	if err != nil {
		log.Fatal(err)
	}

	epShutdown := runEndpoints(ctx, ":9999", &t)

	go func() {
		if err := jc.ConnectAndRead(ctx, nil); err != nil {
			log.Fatal(err)
		}
	}()

	shutdown(epShutdown, sch.Shutdown)

}
