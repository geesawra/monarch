package main

import (
	"context"
	"log"
	"log/slog"
	"os"
	"runtime"
	"time"

	firebase "firebase.google.com/go/v4"
	"github.com/bluesky-social/indigo/atproto/atclient"
	"github.com/bluesky-social/indigo/atproto/identity"
	"github.com/bluesky-social/indigo/atproto/syntax"
	"github.com/bluesky-social/jetstream/pkg/client"
	"github.com/bluesky-social/jetstream/pkg/client/schedulers/parallel"
	"github.com/sethvargo/go-envconfig"
	zaploki "github.com/th1cha/zap-loki"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
	"google.golang.org/api/option"
)

type Config struct {
	FirebaseKeyJSON string `env:"FIREBASE_KEY_JSON,required"`
	Handle          string `env:"HANDLE,required"`
	Password        string `env:"PASSWORD,required"`
	LogOutput       string `env:"LOG_OUTPUT,default=stdout"`
	LokiURL         string `env:"LOKI_URL"`
	LokiUsername    string `env:"LOKI_USERNAME"`
	LokiPassword    string `env:"LOKI_PASSWORD"`
}

func newLogger(ctx context.Context, c Config) (*zap.SugaredLogger, func()) {
	cfg := zap.Config{
		Level:       zap.NewAtomicLevelAt(zapcore.InfoLevel),
		Development: false,
		Encoding:    "json",
		EncoderConfig: zapcore.EncoderConfig{
			TimeKey:        "ts",
			LevelKey:       "level",
			NameKey:        "logger",
			CallerKey:      "caller",
			MessageKey:     "msg",
			StacktraceKey:  "stacktrace",
			LineEnding:     zapcore.DefaultLineEnding,
			EncodeLevel:    zapcore.LowercaseLevelEncoder,
			EncodeTime:     zapcore.ISO8601TimeEncoder,
			EncodeDuration: zapcore.SecondsDurationEncoder,
			EncodeCaller:   zapcore.ShortCallerEncoder,
		},
	}

	noop := func() {}

	switch c.LogOutput {
	case "loki":
		cfg.OutputPaths = []string{}
	case "both":
		cfg.OutputPaths = []string{"stdout"}
	default:
		cfg.OutputPaths = []string{"stdout"}
		l, _ := cfg.Build()
		return l.Sugar(), noop
	}

	if c.LokiURL == "" {
		log.Fatal("LOKI_URL is required when LOG_OUTPUT is loki or both")
	}

	lp := zaploki.New(ctx, zaploki.Config{
		Url:          c.LokiURL,
		BatchMaxSize: 1000,
		BatchMaxWait: 5 * time.Second,
		Labels:       map[string]string{"app": "beepboop"},
		Auth: &zaploki.BasicAuthenticator{
			Username: c.LokiUsername,
			Password: c.LokiPassword,
		},
	})

	l, err := lp.WithCreateLogger(cfg)
	if err != nil {
		log.Fatal("create logger:", err)
	}

	return l.Sugar(), lp.Stop
}

func main() {
	ctx := context.Background()

	var c Config
	if err := envconfig.Process(ctx, &c); err != nil {
		log.Fatal(err)
	}

	l, stopLogger := newLogger(ctx, c)

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

	m, err := newMetrics()
	if err != nil {
		log.Fatal("initialize metrics:", err)
	}

	_ = msg
	sch := parallel.NewScheduler(
		runtime.NumCPU()*8,
		"processor",
		slog.Default(),
		handleEvent(l, atc, msg, &t, m),
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

	shutdown(l, 15*time.Second, os.Exit, epShutdown, sch.Shutdown, stopLogger)

}
