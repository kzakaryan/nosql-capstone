package com.activitylog.service;

import com.activitylog.model.ActivityLog;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.cql.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ActivityLogService {

    private static final DefaultConsistencyLevel CL = DefaultConsistencyLevel.QUORUM;

    private final CqlSession        session;
    private final PreparedStatement psInsert;
    private final PreparedStatement psAll;
    private final PreparedStatement psRecent;
    private final PreparedStatement psRange;

    public ActivityLogService(CqlSession session) {
        this.session  = session;
        this.psInsert = session.prepare(
            "INSERT INTO activity_log.user_activities " +
            "(user_id, activity_time, activity_id, activity_type, event_ts, metadata) " +
            "VALUES (?, ?, ?, ?, ?, ?) USING TTL ?");
        this.psAll    = session.prepare(
            "SELECT * FROM activity_log.user_activities WHERE user_id = ?");
        this.psRecent = session.prepare(
            "SELECT * FROM activity_log.user_activities WHERE user_id = ? LIMIT ?");
        this.psRange  = session.prepare(
            "SELECT * FROM activity_log.user_activities WHERE user_id = ? AND event_ts >= ? AND event_ts <= ?");
    }

    public void truncate() {
        session.execute("TRUNCATE activity_log.user_activities");
    }

    public void log(String userId, String activityType, Instant eventTs, String metadata, int ttlSeconds) {
        session.execute(psInsert.bind()
            .setString(0, userId)
            .setUuid(1, UUID.randomUUID())
            .setUuid(2, UUID.randomUUID())
            .setString(3, activityType)
            .setInstant(4, eventTs)
            .setString(5, metadata)
            .setInt(6, ttlSeconds)
            .setConsistencyLevel(CL));
    }

    public List<ActivityLog> getAll(String userId) {
        return toList(session.execute(
            psAll.bind().setString(0, userId).setConsistencyLevel(CL)));
    }

    public List<ActivityLog> getRecent(String userId, int limit) {
        return toList(session.execute(
            psRecent.bind().setString(0, userId).setInt(1, limit).setConsistencyLevel(CL)));
    }

    public List<ActivityLog> getInRange(String userId, Instant from, Instant to) {
        return toList(session.execute(
            psRange.bind().setString(0, userId).setInstant(1, from).setInstant(2, to).setConsistencyLevel(CL)));
    }

    private List<ActivityLog> toList(ResultSet rs) {
        List<ActivityLog> result = new ArrayList<>();
        for (Row row : rs)
            result.add(new ActivityLog(
                row.getString("user_id"),
                row.getUuid("activity_time"),
                row.getUuid("activity_id"),
                row.getString("activity_type"),
                row.getInstant("event_ts"),
                row.getString("metadata")));
        return result;
    }
}
