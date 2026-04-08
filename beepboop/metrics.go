package main

import (
	"go.opentelemetry.io/otel/exporters/prometheus"
	"go.opentelemetry.io/otel/metric"
	sdkmetric "go.opentelemetry.io/otel/sdk/metric"
)

type metrics struct {
	eventsReceived      metric.Int64Counter
	eventsSkipped       metric.Int64Counter
	notificationsSent   metric.Int64Counter
	notificationsFailed metric.Int64Counter
	cacheHits           metric.Int64Counter
	cacheMisses         metric.Int64Counter
	eventDuration       metric.Int64Histogram
	tokensRegistered    metric.Int64UpDownCounter
	throttleDelayMs     metric.Float64Gauge
}

func newMetrics() (*metrics, error) {
	exporter, err := prometheus.New()
	if err != nil {
		return nil, err
	}

	provider := sdkmetric.NewMeterProvider(sdkmetric.WithReader(exporter))
	meter := provider.Meter("beepboop")

	m := &metrics{}

	m.eventsReceived, err = meter.Int64Counter("beepboop.events.received",
		metric.WithDescription("Total events received from jetstream"))
	if err != nil {
		return nil, err
	}

	m.eventsSkipped, err = meter.Int64Counter("beepboop.events.skipped",
		metric.WithDescription("Events skipped without processing"))
	if err != nil {
		return nil, err
	}

	m.notificationsSent, err = meter.Int64Counter("beepboop.notifications.sent",
		metric.WithDescription("Successful FCM notifications sent"))
	if err != nil {
		return nil, err
	}

	m.notificationsFailed, err = meter.Int64Counter("beepboop.notifications.failed",
		metric.WithDescription("Failed FCM notification attempts"))
	if err != nil {
		return nil, err
	}

	m.cacheHits, err = meter.Int64Counter("beepboop.cache.hits",
		metric.WithDescription("Cache hit count"))
	if err != nil {
		return nil, err
	}

	m.cacheMisses, err = meter.Int64Counter("beepboop.cache.misses",
		metric.WithDescription("Cache miss count"))
	if err != nil {
		return nil, err
	}

	m.eventDuration, err = meter.Int64Histogram("beepboop.event.duration_ms",
		metric.WithDescription("Time to handle an event in milliseconds"))
	if err != nil {
		return nil, err
	}

	m.tokensRegistered, err = meter.Int64UpDownCounter("beepboop.tokens.registered",
		metric.WithDescription("Current number of registered FCM tokens"))
	if err != nil {
		return nil, err
	}

	m.throttleDelayMs, err = meter.Float64Gauge("beepboop.throttle.delay_ms",
		metric.WithDescription("Current adaptive throttle delay in milliseconds"))
	if err != nil {
		return nil, err
	}

	return m, nil
}
