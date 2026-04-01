package main

import (
	"os"
	"sync/atomic"
	"syscall"
	"testing"
	"time"

	"github.com/empijei/tst"
	"go.uber.org/zap"
)

func TestShutdown(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name         string
		timeout      time.Duration
		funcs        []func()
		wantOrder    []int
		wantExitCode int
	}{
		{
			name:         "calls done after funcs complete",
			timeout:      time.Second,
			wantExitCode: 0,
		},
		{
			name:         "calls funcs in order",
			timeout:      time.Second,
			wantOrder:    []int{1, 2, 3},
			wantExitCode: 0,
		},
		{
			name:    "force exits on timeout",
			timeout: 5 * time.Millisecond,
			funcs: []func(){
				func() { time.Sleep(50 * time.Millisecond) },
			},
			wantExitCode: 42,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			l, _ := zap.NewDevelopment()
			var exitCode atomic.Int32
			exitCode.Store(-1)
			var order []int

			funcs := tt.funcs
			if tt.wantOrder != nil {
				funcs = make([]func(), len(tt.wantOrder))
				for i, v := range tt.wantOrder {
					funcs[i] = func() { order = append(order, v) }
				}
			}

			exit := func(code int) {
				exitCode.Store(int32(code))
			}

			sigCh := make(chan os.Signal, 1)
			go awaitShutdown(l.Sugar(), sigCh, tt.timeout, exit, funcs...)

			sigCh <- syscall.SIGTERM
			time.Sleep(20 * time.Millisecond)

			tst.Is(int32(tt.wantExitCode), exitCode.Load(), t)
			if tt.wantOrder != nil {
				tst.Is(tt.wantOrder, order, t)
			}
		})
	}
}
