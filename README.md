# User Activity Log System

A capstone project demonstrating Apache Cassandra data modeling for a user activity log use case. The app stores and retrieves user activity events (login, page view, logout, etc.) with automatic TTL-based expiration.

---
## Getting Started
### Run

```bash
# 1. Start Cassandra
docker-compose up -d

# 2. Wait ~60 seconds for Cassandra to be ready, then:
mvn spring-boot:run
```
---

## Data Model

### Keyspace

```cql
CREATE KEYSPACE IF NOT EXISTS activity_log
WITH replication = {
  'class': 'NetworkTopologyStrategy',
  'datacenter1': 
}
AND durable_writes = true;
```

### Table

```cql
CREATE TABLE IF NOT EXISTS activity_log.user_activities (
  user_id       TEXT,
  activity_time UUID,
  activity_id   UUID,
  activity_type TEXT,
  event_ts      TIMESTAMP,
  metadata      TEXT,
  PRIMARY KEY (user_id, event_ts, activity_id)
)
WITH CLUSTERING ORDER BY (event_ts DESC, activity_id ASC)
AND default_time_to_live = 2592000;   -- 30 days
```

| Key component | Value | Reason |
|---------------|-------|--------|
| Partition key | `user_id` | Co-locates all events for one user on a single partition — every query scoped to a user is a single-partition read |
| Clustering key | `event_ts DESC` | Newest events are stored first on disk — `LIMIT N` reads the N newest rows without a full scan |
| Tiebreaker | `activity_id ASC` | Guarantees uniqueness when two events share the same millisecond |
| Default TTL | 30 days | Rows expire automatically — no application-level cleanup job needed |

---
## Implemented Queries

### 1. Insert an activity log

```java
service.log(userId, activityType, eventTs, metadata, ttlSeconds);
```

```cql
INSERT INTO activity_log.user_activities
  (user_id, activity_time, activity_id, activity_type, event_ts, metadata)
VALUES (?, ?, ?, ?, ?, ?)
USING TTL ?;
```

Per-row TTL overrides the table default, allowing short-lived demo rows alongside normal 30-day rows.

### 2. Get all activities for a user

```java
service.getAll("alice");
```

```cql
SELECT * FROM activity_log.user_activities
WHERE user_id = ?;
```

Returns all events newest-first (enforced by the clustering order).

### 3. Get the N most recent activities

```java
service.getRecent("alice", 2);
```

```cql
SELECT * FROM activity_log.user_activities
WHERE user_id = ?
LIMIT ?;
```

Efficient because the newest rows are already at the head of the partition on disk.

### 4. Get activities within a time range

```java
service.getInRange("alice", from, to);
```

```cql
SELECT * FROM activity_log.user_activities
WHERE user_id = ?
  AND event_ts >= ?
  AND event_ts <= ?;
```
Range queries on clustering keys are natively supported with no secondary index needed.
