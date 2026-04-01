package main

import (
	"log"
	"os"
	"os/signal"
	"syscall"
)

func shutdown(funcs ...func()) {
	signalCh := make(chan os.Signal, 1)
	signal.Notify(signalCh, syscall.SIGINT, syscall.SIGTERM)

	sig := <-signalCh
	log.Printf("Received signal: %v\n", sig)

	for _, f := range funcs {
		f()
	}

	os.Exit(0)
}
