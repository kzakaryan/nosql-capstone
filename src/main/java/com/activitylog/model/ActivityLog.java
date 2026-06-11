package com.activitylog.model;

import java.time.Instant;
import java.util.UUID;

public record ActivityLog(
    String  userId,
    UUID    activityTime,
    UUID    activityId,
    String  activityType,
    Instant eventTs,
    String  metadata
) {}
