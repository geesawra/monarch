package main

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"time"

	"github.com/bluesky-social/indigo/atproto/syntax"
	"github.com/empijei/srpc"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

type subscriptionRequest struct {
	DID      string `json:"did"`
	FCMToken string `json:"token"`
}

func (sr subscriptionRequest) Validate() error {
	_, err := syntax.ParseAtIdentifier(sr.DID)
	if err != nil {
		return fmt.Errorf("invalid DID: %w", err)
	}

	if sr.FCMToken == "" {
		return fmt.Errorf("invalid fcm token")
	}

	return nil
}

func runEndpoints(ctx context.Context, bind string, t *tokens) func() {
	mux := http.NewServeMux()
	ep := srpc.NewEndpoint(http.MethodPost, "/subscribe", srpc.NewCodecJSON[struct{}](), srpc.NewCodecJSON[subscriptionRequest]())

	ep.Register(mux, func(ctx context.Context, req subscriptionRequest) (struct{}, error) {
		t.storeDID(req.DID, req.FCMToken)
		return struct{}{}, nil
	})

	del := srpc.NewEndpoint(http.MethodDelete, "/subscribe", srpc.NewCodecJSON[struct{}](), srpc.NewCodecJSON[subscriptionRequest]())
	del.Register(mux, func(ctx context.Context, req subscriptionRequest) (struct{}, error) {
		t.removeToken(req.FCMToken)
		return struct{}{}, nil
	})

	mux.Handle("/metrics", promhttp.Handler())

	srv := &http.Server{
		Addr:    bind,
		Handler: rateLimitMiddleware(mux),
	}

	go func() {
		log.Printf("Server listening on %s\n", srv.Addr)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("listen: %s\n", err)
		}
	}()
	return func() {
		ctx, cancel := context.WithTimeout(ctx, 30*time.Second)
		defer cancel()

		if err := srv.Shutdown(ctx); err != nil {
			log.Println("endpoints shutdown:", err)
		}
	}
}
