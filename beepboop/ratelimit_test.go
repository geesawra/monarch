package main

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/empijei/tst"
)

func TestIPRateLimiter(t *testing.T) {
	tests := []struct {
		name        string
		setup       func(rl *ipRateLimiter)
		ip          string
		wantAllow   bool
		wantMapLen  int
	}{
		{
			name:       "creates new visitor on first request",
			ip:         "1.2.3.4",
			wantAllow:  true,
			wantMapLen: 1,
		},
		{
			name: "reuses existing visitor",
			setup: func(rl *ipRateLimiter) {
				rl.get("1.2.3.4")
			},
			ip:         "1.2.3.4",
			wantAllow:  true,
			wantMapLen: 1,
		},
		{
			name: "allows up to burst limit",
			setup: func(rl *ipRateLimiter) {
				for range 4 {
					rl.get("10.0.0.1").Allow()
				}
			},
			ip:         "10.0.0.1",
			wantAllow:  true,
			wantMapLen: 1,
		},
		{
			name: "blocks after burst exceeded",
			setup: func(rl *ipRateLimiter) {
				for range 5 {
					rl.get("10.0.0.1").Allow()
				}
			},
			ip:         "10.0.0.1",
			wantAllow:  false,
			wantMapLen: 1,
		},
		{
			name: "isolates different IPs",
			setup: func(rl *ipRateLimiter) {
				for range 5 {
					rl.get("10.0.0.1").Allow()
				}
			},
			ip:         "10.0.0.2",
			wantAllow:  true,
			wantMapLen: 2,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			rl := &ipRateLimiter{visitors: make(map[string]*visitor)}
			if tt.setup != nil {
				tt.setup(rl)
			}
			got := rl.get(tt.ip).Allow()
			tst.Is(tt.wantAllow, got, t)
			tst.Is(tt.wantMapLen, len(rl.visitors), t)
		})
	}
}

func TestIPRateLimiterCleanup(t *testing.T) {
	tests := []struct {
		name      string
		visitors  map[string]time.Duration
		wantAlive []string
		wantGone  []string
	}{
		{
			name: "removes stale entries",
			visitors: map[string]time.Duration{
				"stale-ip": -10 * time.Minute,
				"fresh-ip": 0,
			},
			wantAlive: []string{"fresh-ip"},
			wantGone:  []string{"stale-ip"},
		},
		{
			name: "keeps entries under threshold",
			visitors: map[string]time.Duration{
				"a": -4 * time.Minute,
				"b": -1 * time.Minute,
			},
			wantAlive: []string{"a", "b"},
		},
		{
			name: "removes all stale entries",
			visitors: map[string]time.Duration{
				"old1": -6 * time.Minute,
				"old2": -20 * time.Minute,
			},
			wantGone: []string{"old1", "old2"},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			rl := &ipRateLimiter{visitors: make(map[string]*visitor)}
			for ip, age := range tt.visitors {
				rl.get(ip)
				rl.visitors[ip].lastSeen = time.Now().Add(age)
			}

			rl.mu.Lock()
			for ip, v := range rl.visitors {
				if time.Since(v.lastSeen) > 5*time.Minute {
					delete(rl.visitors, ip)
				}
			}
			rl.mu.Unlock()

			for _, ip := range tt.wantAlive {
				_, ok := rl.visitors[ip]
				tst.Is(true, ok, t)
			}
			for _, ip := range tt.wantGone {
				_, ok := rl.visitors[ip]
				tst.Is(false, ok, t)
			}
		})
	}
}

func TestClientIP(t *testing.T) {
	tests := []struct {
		name          string
		xForwardedFor string
		remoteAddr    string
		wantIP        string
	}{
		{
			name:       "falls back to RemoteAddr",
			remoteAddr: "192.168.1.1:1234",
			wantIP:     "192.168.1.1",
		},
		{
			name:          "single X-Forwarded-For value",
			xForwardedFor: "1.2.3.4",
			remoteAddr:    "10.0.0.1:1234",
			wantIP:        "1.2.3.4",
		},
		{
			name:          "extracts first IP from chain",
			xForwardedFor: "1.2.3.4, 10.0.0.1, 172.17.0.1",
			remoteAddr:    "10.0.0.1:1234",
			wantIP:        "1.2.3.4",
		},
		{
			name:          "trims whitespace from first IP",
			xForwardedFor: " 5.6.7.8 , 10.0.0.1",
			remoteAddr:    "10.0.0.1:1234",
			wantIP:        "5.6.7.8",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			req := httptest.NewRequest(http.MethodGet, "/", nil)
			req.RemoteAddr = tt.remoteAddr
			if tt.xForwardedFor != "" {
				req.Header.Set("X-Forwarded-For", tt.xForwardedFor)
			}
			tst.Is(tt.wantIP, clientIP(req), t)
		})
	}
}

func TestRateLimitMiddleware(t *testing.T) {
	ok := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	})
	handler := rateLimitMiddleware(ok)

	tests := []struct {
		name            string
		reqCount        int
		xForwardedFor   string
		remoteAddr      string
		wantLastStatus  int
		wantRetryAfter  string
	}{
		{
			name:           "allows requests within burst",
			reqCount:       5,
			remoteAddr:     "192.168.1.1:1234",
			wantLastStatus: http.StatusOK,
		},
		{
			name:           "blocks after burst exceeded",
			reqCount:       6,
			remoteAddr:     "192.168.1.2:1234",
			wantLastStatus: http.StatusTooManyRequests,
			wantRetryAfter: "12",
		},
		{
			name:           "uses X-Forwarded-For when present",
			reqCount:       6,
			xForwardedFor:  "10.10.10.10",
			remoteAddr:     "192.168.1.3:1234",
			wantLastStatus: http.StatusTooManyRequests,
			wantRetryAfter: "12",
		},
		{
			name:           "single request passes through",
			reqCount:       1,
			remoteAddr:     "192.168.1.4:1234",
			wantLastStatus: http.StatusOK,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var rec *httptest.ResponseRecorder
			for range tt.reqCount {
				req := httptest.NewRequest(http.MethodPost, "/subscribe", nil)
				req.RemoteAddr = tt.remoteAddr
				if tt.xForwardedFor != "" {
					req.Header.Set("X-Forwarded-For", tt.xForwardedFor)
				}
				rec = httptest.NewRecorder()
				handler.ServeHTTP(rec, req)
			}
			tst.Is(tt.wantLastStatus, rec.Code, t)
			if tt.wantRetryAfter != "" {
				tst.Is(tt.wantRetryAfter, rec.Header().Get("Retry-After"), t)
			}
		})
	}
}
