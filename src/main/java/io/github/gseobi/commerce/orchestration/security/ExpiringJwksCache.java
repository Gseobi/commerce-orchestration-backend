package io.github.gseobi.commerce.orchestration.security;

import java.time.Duration;
import java.util.concurrent.Callable;
import org.springframework.cache.concurrent.ConcurrentMapCache;

final class ExpiringJwksCache extends ConcurrentMapCache {
    private final long ttlNanos;
    private volatile long expiresAt;

    ExpiringJwksCache(Duration ttl) {
        super("oidc-jwks", false);
        ttlNanos = ttl.toNanos();
    }

    @Override
    public synchronized ValueWrapper get(Object key) {
        expire();
        return super.get(key);
    }

    @Override
    public synchronized <T> T get(Object key, Class<T> type) {
        expire();
        return super.get(key, type);
    }

    @Override
    public synchronized <T> T get(Object key, Callable<T> loader) {
        expire();
        return super.get(key, () -> {
            T value = loader.call();
            expiresAt = System.nanoTime() + ttlNanos;
            return value;
        });
    }

    @Override
    public synchronized void put(Object key, Object value) {
        super.put(key, value);
        expiresAt = System.nanoTime() + ttlNanos;
    }

    private void expire() {
        if (System.nanoTime() - expiresAt >= 0) {
            clear();
        }
    }
}
