package com.fitnesstracker.journal.nutrition;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fitnesstracker.journal.auth.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Food log, weigh-ins and nutrition goals. Everything is scoped to the signed-in account. */
@RestController
@RequestMapping("/api")
public class NutritionController {

    private final FoodRepository food;
    private final WeightRepository weights;
    private final GoalRepository goals;
    private final Clock clock;

    public NutritionController(FoodRepository food, WeightRepository weights, GoalRepository goals, Clock clock) {
        this.food = food;
        this.weights = weights;
        this.goals = goals;
        this.clock = clock;
    }

    // ---------- food ----------

    @GetMapping("/food")
    public List<FoodEntry> day(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return food.findByUserIdAndDate(CurrentUser.id(), date, Sort.by("createdAt"));
    }

    @PostMapping("/food")
    @ResponseStatus(HttpStatus.CREATED)
    public FoodEntry add(@Valid @RequestBody FoodEntry entry) {
        return food.save(entry.owned(null, CurrentUser.id(), now()));
    }

    @PutMapping("/food/{id}")
    public FoodEntry update(@PathVariable String id, @Valid @RequestBody FoodEntry entry) {
        FoodEntry existing = mine(id);
        return food.save(entry.owned(id, existing.userId(), existing.createdAt()));
    }

    @DeleteMapping("/food/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String id) {
        food.delete(mine(id));
    }

    /** Foods logged in the last 60 days, most recent first, one per name: for adding again in one tap. */
    @GetMapping("/food/recent")
    public List<FoodEntry> recent() {
        LocalDate today = LocalDate.now(clock);
        var latest = new LinkedHashMap<String, FoodEntry>();
        food.findByUserIdAndDateBetween(CurrentUser.id(), today.minusDays(60), today).stream()
                .sorted(Comparator.comparing(FoodEntry::date).thenComparing(FoodEntry::createdAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .forEach(e -> latest.putIfAbsent(e.name().toLowerCase(), e));
        return latest.values().stream().limit(40).toList();
    }

    private FoodEntry mine(String id) {
        return food.findByIdAndUserId(id, CurrentUser.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No food entry with id " + id));
    }

    // ---------- weight ----------

    public record WeightBody(@NotNull @DecimalMin("60") @DecimalMax("700") Double weightLb) {
    }

    @PutMapping("/weights/{date}")
    public WeightEntry weigh(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody WeightBody body) {
        String user = CurrentUser.id();
        String id = weights.findByUserIdAndDate(user, date).map(WeightEntry::id).orElse(null);
        return weights.save(new WeightEntry(id, user, date, Math.round(body.weightLb() * 10) / 10.0));
    }

    @DeleteMapping("/weights/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unweigh(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        weights.deleteByUserIdAndDate(CurrentUser.id(), date);
    }

    // ---------- goal and progress ----------

    @GetMapping("/nutrition")
    public Progress.Report progress(@RequestParam(defaultValue = "56") int days) {
        String user = CurrentUser.id();
        LocalDate today = LocalDate.now(clock);
        int d = Math.min(Math.max(days, 7), 365);
        return Progress.compute(food.findByUserIdAndDateBetween(user, today.minusDays(d), today),
                weights.findByUserIdOrderByDateAsc(user), goals.findById(user).orElse(null), today, d);
    }

    @PutMapping("/nutrition/goal")
    public NutritionGoal setGoal(@Valid @RequestBody NutritionGoal goal) {
        String user = CurrentUser.id();
        // Keep the original start date unless the direction changes.
        LocalDate start = goals.findById(user)
                .filter(g -> g.direction() == goal.direction() && g.startDate() != null)
                .map(NutritionGoal::startDate)
                .orElse(LocalDate.now(clock));
        return goals.save(new NutritionGoal(user, goal.direction(), goal.targetWeightLb(), goal.weeklyRateLb(),
                goal.calories(), goal.proteinG(), goal.carbsG(), goal.fatG(), start));
    }

    /** Works out suggested targets; doesn't save anything, so any signed-in user (demo too) can try it. */
    @PostMapping("/nutrition/suggest")
    public GoalCalculator.Suggestion suggest(@Valid @RequestBody GoalCalculator.Profile profile) {
        return GoalCalculator.suggest(profile);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
