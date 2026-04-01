package main

import (
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"go.uber.org/zap"
)

type exitFunc func(code int)

func shutdown(l *zap.SugaredLogger, timeout time.Duration, exit exitFunc, funcs ...func()) {
	signalCh := make(chan os.Signal, 1)
	signal.Notify(signalCh, syscall.SIGINT, syscall.SIGTERM)
	awaitShutdown(l, signalCh, timeout, exit, funcs...)
}

func awaitShutdown(l *zap.SugaredLogger, signalCh <-chan os.Signal, timeout time.Duration, exit exitFunc, funcs ...func()) {
	sig := <-signalCh
	log.Printf("Received signal: %v\n", sig)

	go func() {
		<-time.After(timeout)
		l.Errorw("force shutdown", "timeout", timeout)
		exit(42)
	}()

	for _, f := range funcs {
		f()
	}

	exit(0)
}
