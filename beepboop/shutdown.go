package main

import (
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"go.uber.org/zap"
)

func shutdown(l *zap.SugaredLogger, timeout time.Duration, funcs ...func()) {
	signalCh := make(chan os.Signal, 1)
	signal.Notify(signalCh, syscall.SIGINT, syscall.SIGTERM)

	sig := <-signalCh
	log.Printf("Received signal: %v\n", sig)

	go func() {
		<-time.After(timeout)
		l.Errorw("force shutdown", "timemout", timeout)
		os.Exit(42)
	}()

	for _, f := range funcs {
		f()
	}

	os.Exit(0)
}
