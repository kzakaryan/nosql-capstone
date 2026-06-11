package com.activitylog.config;

import com.datastax.oss.driver.api.core.CqlSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;

@Configuration
public class CassandraClient {

    public static final int TTL_30_DAYS = 2_592_000;

    @Value("${cassandra.host}")
    private String host;

    @Value("${cassandra.port}")
    private int port;

    @Value("${cassandra.datacenter}")
    private String datacenter;

    @Bean(destroyMethod = "close")
    public CqlSession cqlSession() {
        CqlSession session = CqlSession.builder()
                .addContactPoint(new InetSocketAddress(host, port))
                .withLocalDatacenter(datacenter)
                .build();
        initSchema(session);
        return session;
    }

    private void initSchema(CqlSession session) {
        session.execute(
            "CREATE KEYSPACE IF NOT EXISTS activity_log " +
            "WITH replication = {'class':'NetworkTopologyStrategy','datacenter1':1} " +
            "AND durable_writes = true"
        );
        session.execute(
            "CREATE TABLE IF NOT EXISTS activity_log.user_activities (" +
            "  user_id       TEXT," +
            "  activity_time UUID," +
            "  activity_id   UUID," +
            "  activity_type TEXT," +
            "  event_ts      TIMESTAMP," +
            "  metadata      TEXT," +
            "  PRIMARY KEY (user_id, event_ts, activity_id)" +
            ") WITH CLUSTERING ORDER BY (event_ts DESC, activity_id ASC)" +
            "  AND default_time_to_live = " + TTL_30_DAYS
        );
    }
}
