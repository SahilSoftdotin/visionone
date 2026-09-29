package com.visiondigitallab.visionone.content.internal;

import com.visiondigitallab.visionone.content.api.ContentPublication;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ContentPublicationService implements ContentPublication {

    private final JdbcClient jdbc;

    public ContentPublicationService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PublishedContent> publishedIn(UUID organizationId, Instant from, Instant to) {
        return jdbc.sql("""
                select title, content_type, published_at, published_url
                from content_item
                where organization_id = :orgId
                  and status = 'PUBLISHED'
                  and published_at >= :from and published_at < :to
                order by published_at desc
                """)
                .param("orgId", organizationId)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query((rs, rowNum) -> new PublishedContent(
                        rs.getString("title"),
                        rs.getString("content_type"),
                        rs.getTimestamp("published_at").toInstant().toString(),
                        rs.getString("published_url")))
                .list();
    }
}
