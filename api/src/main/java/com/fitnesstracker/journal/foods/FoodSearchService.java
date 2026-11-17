package com.fitnesstracker.journal.foods;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Searches USDA (whole foods) and Open Food Facts (packaged foods) together, and looks up
 * barcodes. Results are cached in memory for a day, since both services ask for gentle use.
 */
@Service
public class FoodSearchService {

    private static final Logger log = LoggerFactory.getLogger(FoodSearchService.class);
    static final Duration TTL = Duration.ofHours(24);
    static final int CACHE_SIZE = 500;

    private final UsdaClient usda;
    private final OpenFoodFactsClient off;
    private final Clock clock;
    private final Map<String, Cached<?>> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached<?>> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    record Cached<T>(T value, Instant at) {
    }

    public FoodSearchService(UsdaClient usda, OpenFoodFactsClient off, Clock clock) {
        this.usda = usda;
        this.off = off;
        this.clock = clock;
    }

    public record SearchResult(List<FoodResult> foods, List<String> unavailable) {
    }

    public SearchResult search(String query) {
        String q = query.trim().replaceAll("\\s+", " ");
        return cached("q:" + q.toLowerCase(Locale.ROOT), () -> {
            var fromUsda = CompletableFuture.supplyAsync(() -> safely("USDA", () -> usda.search(q)));
            var fromOff = CompletableFuture.supplyAsync(() -> safely("Open Food Facts", () -> off.search(q)));
            List<String> down = new ArrayList<>();
            List<FoodResult> a = fromUsda.join();
            List<FoodResult> b = fromOff.join();
            if (a == null) {
                down.add("USDA");
            }
            if (b == null) {
                down.add("Open Food Facts");
            }
            return new SearchResult(merge(a, b), down);
        }, r -> r.unavailable().isEmpty());
    }

    public Optional<FoodResult> barcode(String code) {
        return cached("b:" + code, () -> {
            Optional<FoodResult> hit = Optional.ofNullable(safely("Open Food Facts", () -> off.barcode(code).orElse(null)));
            if (hit.isEmpty()) {
                hit = Optional.ofNullable(safely("USDA", () -> usda.barcode(code).orElse(null)));
            }
            return hit;
        }, r -> true);
    }

    /** Whole foods first, then packaged ones; drop repeats with the same name and brand. */
    static List<FoodResult> merge(List<FoodResult> generic, List<FoodResult> packaged) {
        List<FoodResult> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (List<FoodResult> list : List.of(generic == null ? List.<FoodResult>of() : generic,
                packaged == null ? List.<FoodResult>of() : packaged)) {
            for (FoodResult f : list) {
                String key = (f.name() + "|" + (f.brand() == null ? "" : f.brand())).toLowerCase(Locale.ROOT);
                if (seen.add(key)) {
                    out.add(f);
                }
            }
        }
        return out.size() > 25 ? out.subList(0, 25) : out;
    }

    private static <T> T safely(String name, Supplier<T> f) {
        try {
            return f.get();
        } catch (RuntimeException e) {
            log.warn("{} lookup failed: {}", name, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized <T> T cached(String key, Supplier<T> load, java.util.function.Predicate<T> keep) {
        Cached<?> hit = cache.get(key);
        if (hit != null && hit.at().plus(TTL).isAfter(clock.instant())) {
            return (T) hit.value();
        }
        T value = load.get();
        if (keep.test(value)) {
            cache.put(key, new Cached<>(value, clock.instant()));
        }
        return value;
    }
}
