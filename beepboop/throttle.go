package main

import (
	"sync"
	"time"
)

type adaptiveThrottle struct {
	mu         sync.Mutex
	emaLatency float64
	alpha      float64
	minDelay   time.Duration
	maxDelay   time.Duration
}

func newAdaptiveThrottle() *adaptiveThrottle {
	return &adaptiveThrottle{
		alpha:    0.3,
		minDelay: 50 * time.Millisecond,
		maxDelay: 5 * time.Second,
	}
}

func (at *adaptiveThrottle) recordLatency(d time.Duration) {
	at.mu.Lock()
	defer at.mu.Unlock()
	ms := float64(d.Milliseconds())
	if at.emaLatency == 0 {
		at.emaLatency = ms
	} else {
		at.emaLatency = at.alpha*ms + (1-at.alpha)*at.emaLatency
	}
}

func (at *adaptiveThrottle) delay() time.Duration {
	at.mu.Lock()
	ema := at.emaLatency
	at.mu.Unlock()

	d := time.Duration(ema) * time.Millisecond
	if d < at.minDelay {
		return at.minDelay
	}
	if d > at.maxDelay {
		return at.maxDelay
	}
	return d
}

func (at *adaptiveThrottle) wait() {
	time.Sleep(at.delay())
}

func (at *adaptiveThrottle) currentEMA() float64 {
	at.mu.Lock()
	defer at.mu.Unlock()
	return at.emaLatency
}
