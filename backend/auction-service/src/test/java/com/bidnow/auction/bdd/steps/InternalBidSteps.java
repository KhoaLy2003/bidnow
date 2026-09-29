package com.bidnow.auction.bdd.steps;

import com.bidnow.auction.service.AdminAuctionService;
import com.bidnow.auction.service.AuctionClosureService;
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.bdd.client.BddRestClient;
import com.bidnow.bdd.context.ScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@RequiredArgsConstructor
public class InternalBidSteps {

    private static final String BASE = "/api/v1/internal/auctions/";
    /** current_winner_id seeded on the close-race auctions. */
    private static final String INITIAL_WINNER = "550e8400-e29b-41d4-a716-446655440002";

    private static final String BDD_ADMIN_ID = "550e8400-e29b-41d4-a716-000000000001";

    private final BddRestClient client;
    private final ScenarioContext ctx;
    private final JdbcTemplate jdbcTemplate;
    private final AuctionClosureService closureService;
    private final AdminAuctionService adminAuctionService;
    private final TransactionTemplate transactionTemplate;

    /** One bid attempt of a close-race round. */
    private record BidResult(String bidderId, BigDecimal amount, int status) {
    }

    /** HTTP status codes of the concurrent bids fired by the last concurrency step (glue is scenario-scoped). */
    private final List<Integer> concurrentStatuses = new ArrayList<>();
    private final Map<String, List<BidResult>> raceRounds = new LinkedHashMap<>();
    private ExecutorService lockHolder;
    private Future<?> lockHolderResult;
    private long lastBidMillis;

    @When("bidding-service requests the bid context for auction {string}")
    public void requestBidContext(String auctionId) {
        ctx.setLastResponse(client.given().get(BASE + auctionId + "/bid-context"));
    }

    @When("bidder {string} bids {string} on auction {string}")
    public void bid(String bidderId, String amount, String auctionId) {
        long began = System.nanoTime();
        ctx.setLastResponse(client.given()
                .body(bidBody(bidderId, amount))
                .post(BASE + auctionId + "/bids"));
        lastBidMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began);
    }

    @When("{int} bidders concurrently bid {string} on auction {string}")
    public void concurrentSamePriceBids(int bidders, String amount, String auctionId) throws Exception {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < bidders; i++) {
            String bidderId = UUID.randomUUID().toString();
            tasks.add(() -> client.given().body(bidBody(bidderId, amount))
                    .post(BASE + auctionId + "/bids").statusCode());
        }
        concurrentStatuses.addAll(runConcurrently(tasks));
    }

    @When("the close race runs over auctions {string} with {int} bidders each")
    public void closeRaces(String auctionIds, int bidders) throws Exception {
        for (String auctionId : auctionIds.split(",")) {
            raceRounds.put(auctionId.trim(), raceOnce(auctionId.trim(), bidders));
        }
    }

    @Given("the row lock on auction {string} is held by another transaction for {int} ms")
    public void holdRowLock(String auctionId, int millis) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        lockHolder = Executors.newSingleThreadExecutor();
        lockHolderResult = lockHolder.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForList("SELECT id FROM auction_items WHERE id = ?::uuid FOR UPDATE", auctionId);
            locked.countDown();
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).as("background transaction acquired the row lock").isTrue();
    }

    @Then("the bid response should be a server error returned within {int} ms")
    public void serverErrorWithin(int maxMillis) throws Exception {
        try {
            assertThat(ctx.getLastResponse().statusCode()).as("response status").isGreaterThanOrEqualTo(500);
            assertThat(lastBidMillis).as("bid response time in ms").isLessThan(maxMillis);
        } finally {
            releaseLockHolder();
        }
    }

    @Then("exactly {int} of the concurrent bids should succeed")
    public void exactlyNSucceed(int expected) {
        assertThat(concurrentStatuses.stream().filter(s -> s == 200).count())
                .as("statuses: %s", concurrentStatuses).isEqualTo(expected);
    }

    @Then("the remaining concurrent bids should be rejected with status {int}")
    public void remainingRejected(int status) {
        assertThat(concurrentStatuses.stream().filter(s -> s != 200))
                .as("statuses: %s", concurrentStatuses).allMatch(s -> s == status);
    }

    @Then("auction {string} should have {int} total bids and current price {string}")
    public void auctionState(String auctionId, int totalBids, String price) {
        Map<String, Object> row = row(auctionId);
        assertThat(((Number) row.get("total_bids")).intValue()).isEqualTo(totalBids);
        assertThat((BigDecimal) row.get("current_price")).isEqualByComparingTo(price);
    }

    @Then("every raced auction should be COMPLETED with the highest accepted bidder as winner")
    public void racedAuctionsCompletedWithHighestBidder() {
        raceRounds.forEach((auctionId, results) -> {
            Map<String, Object> row = row(auctionId);
            assertThat(row.get("status")).as("status of %s", auctionId).isEqualTo("COMPLETED");
            BidResult best = results.stream().filter(r -> r.status() == 200)
                    .max(Comparator.comparing(BidResult::amount)).orElse(null);
            if (best == null) {
                assertThat(String.valueOf(row.get("winner_id"))).as("winner of %s", auctionId)
                        .isEqualTo(INITIAL_WINNER);
                assertThat((BigDecimal) row.get("current_price")).isEqualByComparingTo("100.00");
            } else {
                assertThat(String.valueOf(row.get("winner_id"))).as("winner of %s, results: %s", auctionId, results)
                        .isEqualTo(best.bidderId());
                assertThat((BigDecimal) row.get("current_price")).isEqualByComparingTo(best.amount());
            }
        });
    }

    @Then("every raced auction total bids should equal 1 plus its accepted bids and rejections should be 400 or 409")
    public void racedTotalBidsMatchAccepted() {
        raceRounds.forEach((auctionId, results) -> {
            long accepted = results.stream().filter(r -> r.status() == 200).count();
            assertThat(results.stream().filter(r -> r.status() != 200).map(BidResult::status))
                    .as("a rejected bid must be 400 or 409, results: %s", results)
                    .allMatch(st -> st == 409 || st == 400);
            assertThat(((Number) row(auctionId).get("total_bids")).intValue())
                    .as("total_bids of %s", auctionId).isEqualTo(1 + (int) accepted);
        });
    }

    @Then("at least one raced bid should have been accepted")
    public void someRacedBidAccepted() {
        assertThat(raceRounds.values().stream().flatMap(List::stream).filter(r -> r.status() == 200).count())
                .as("bids must overlap the closure, results: %s", raceRounds).isPositive();
    }

    @Given("auction {string} ends in {int} seconds")
    public void auctionEndsIn(String auctionId, int seconds) {
        jdbcTemplate.update("UPDATE auction_items SET end_time = NOW() + (? * INTERVAL '1 second') WHERE id = ?::uuid",
                seconds, auctionId);
    }

    @Then("auction {string} should end about {int} seconds from now")
    public void auctionEndsAbout(String auctionId, int seconds) {
        Long remaining = jdbcTemplate.queryForObject(
                "SELECT EXTRACT(EPOCH FROM (end_time - NOW()))::bigint FROM auction_items WHERE id = ?::uuid",
                Long.class, auctionId);
        assertThat(remaining).isBetween((long) seconds - 5, (long) seconds + 1);
    }

    @Then("auction {string} should have {int} extension(s) recorded")
    public void extensionsRecorded(String auctionId, int count) {
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auction_extensions WHERE auction_id = ?::uuid", Integer.class, auctionId);
        Integer counter = jdbcTemplate.queryForObject(
                "SELECT extension_count FROM auction_items WHERE id = ?::uuid", Integer.class, auctionId);
        assertThat(rows).isEqualTo(count);
        assertThat(counter).isEqualTo(count);
    }

    @When("{int} seconds pass")
    public void secondsPass(int seconds) throws InterruptedException {
        Thread.sleep(seconds * 1000L);
    }

    @When("the closure job runs for auction {string}")
    public void closureRuns(String auctionId) {
        closureService.close(UUID.fromString(auctionId));
    }

    @Then("auction {string} should still be ACTIVE")
    public void stillActive(String auctionId) {
        assertThat(row(auctionId).get("status")).isEqualTo("ACTIVE");
    }

    private List<BidResult> raceOnce(String auctionId, int bidders) throws Exception {
        UUID id = UUID.fromString(auctionId);
        BigDecimal start = currentPrice(auctionId);
        List<Callable<BidResult>> tasks = new ArrayList<>();
        for (int i = 1; i <= bidders; i++) {
            String bidderId = UUID.randomUUID().toString();
            BigDecimal amount = start.add(BigDecimal.valueOf(i));
            tasks.add(() -> new BidResult(bidderId, amount, client.given()
                    .body(bidBody(bidderId, amount.toPlainString()))
                    .post(BASE + auctionId + "/bids").statusCode()));
        }
        tasks.add(() -> {
            Thread.sleep(30); // let bids overlap the closure instead of all losing the race to it
            try {
                adminAuctionService.forceCloseAuction(UUID.fromString(BDD_ADMIN_ID), id, null);
            } catch (BadRequestException e) {
                // auction no longer ACTIVE: keep race assertions about bids only
            }
            return null; // marker for the closure task, excluded from bid results
        });
        List<BidResult> results = new ArrayList<>();
        runConcurrently(tasks).stream().filter(Objects::nonNull).forEach(results::add);
        return results;
    }

    private void releaseLockHolder() throws Exception {
        if (lockHolder != null) {
            lockHolderResult.get(30, TimeUnit.SECONDS);
            lockHolder.shutdownNow();
            lockHolder = null;
        }
    }

    private static Map<String, Object> bidBody(String bidderId, String amount) {
        return Map.of("bidId", UUID.randomUUID().toString(), "bidderId", bidderId, "amount", new BigDecimal(amount));
    }

    private Map<String, Object> row(String auctionId) {
        return jdbcTemplate.queryForMap(
                "SELECT status, winner_id, current_winner_id, total_bids, current_price FROM auction_items WHERE id = ?::uuid",
                auctionId);
    }

    private BigDecimal currentPrice(String auctionId) {
        return (BigDecimal) row(auctionId).get("current_price");
    }

    /** Releases all tasks at once through a latch to maximize overlap, then waits for every result. */
    private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
