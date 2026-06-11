package com.activitylog;

import com.activitylog.config.CassandraClient;
import com.activitylog.model.ActivityLog;
import com.activitylog.service.ActivityLogService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@SpringBootApplication
public class Main implements CommandLineRunner {

    private final ActivityLogService service;

    public Main(ActivityLogService service) {
        this.service = service;
    }

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(Main.class, args)));
    }

    @Override
    public void run(String... args) throws InterruptedException {
        service.truncate();
        Instant now = Instant.now();

        // Insertions
        service.log("alice", "login",      now.minus(5, ChronoUnit.HOURS), "ip=192.168.1.1", CassandraClient.TTL_30_DAYS);
        service.log("alice", "page_view",  now.minus(3, ChronoUnit.HOURS), "page=/dashboard", CassandraClient.TTL_30_DAYS);
        service.log("alice", "page_view",  now.minus(1, ChronoUnit.HOURS), "page=/settings",  CassandraClient.TTL_30_DAYS);
        service.log("alice", "logout",     now,                             "session=3600s",   CassandraClient.TTL_30_DAYS);
        service.log("bob",   "login",      now.minus(2, ChronoUnit.HOURS), "ip=10.0.0.1",     CassandraClient.TTL_30_DAYS);
        service.log("bob",   "page_view", now, "page=/home", 3_600);

        // All activities for a user
        print("All activities for alice", service.getAll("alice"));

        // Time range
        print("alice — last 2 hours", service.getInRange("alice", now.minus(2, ChronoUnit.HOURS), now));

        // Most recent N
        print("alice — 2 most recent", service.getRecent("alice", 2));

        // TTL demo
        System.out.println("=== TTL demo: inserting row with 5s TTL ===");
        service.log("alice", "temp_event", now, "note=expires-soon", 5);
        System.out.println("Sleeping 7s...");
        Thread.sleep(7_000);
        print("alice after TTL expiry", service.getAll("alice"));
    }

    private void print(String title, List<ActivityLog> logs) {
        System.out.println("=== " + title + " ===");
        if (logs.isEmpty()) { System.out.println("  (no rows)"); return; }
        for (ActivityLog l : logs)
            System.out.printf("  %-6s  %-12s  %-26s  %s%n",
                l.userId(), l.activityType(), l.eventTs(), l.metadata());
    }
}
