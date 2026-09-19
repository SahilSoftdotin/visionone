package com.visiondigitallab.visionone.growth.internal;

import com.visiondigitallab.visionone.common.Money;
import com.visiondigitallab.visionone.growth.api.GrowthFinance;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GrowthFinanceService implements GrowthFinance {

    private final JdbcClient jdbc;

    public GrowthFinanceService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Investment> investmentFor(UUID organizationId, LocalDate periodMonth) {
        return jdbc.sql("""
                select gp.planned_total_minor,
                       coalesce(sum(ba.actual_minor), 0) as actual_minor,
                       gp.currency
                from growth_plan gp
                left join budget_allocation ba on ba.growth_plan_id = gp.id
                where gp.organization_id = :orgId and gp.period_month = :month
                group by gp.planned_total_minor, gp.currency
                """)
                .param("orgId", organizationId)
                .param("month", java.sql.Date.valueOf(periodMonth))
                .query((rs, rowNum) -> new Investment(
                        Money.of(rs.getLong("planned_total_minor"), rs.getString("currency")),
                        Money.of(rs.getLong("actual_minor"), rs.getString("currency"))))
                .optional();
    }

    @Override
    public List<ChannelSpend> spendByChannel(UUID organizationId, LocalDate periodMonth) {
        return jdbc.sql("""
                select cs.code, cs.display_name,
                       coalesce(ba.planned_minor, 0) as planned_minor,
                       coalesce(ba.actual_minor, 0)  as actual_minor,
                       coalesce(gp.currency, o.currency) as currency
                from channel_source cs
                join organization o on o.id = cs.organization_id
                left join growth_plan gp
                       on gp.organization_id = cs.organization_id and gp.period_month = :month
                left join budget_allocation ba
                       on ba.growth_plan_id = gp.id and ba.channel_source_id = cs.id
                where cs.organization_id = :orgId and cs.active
                order by cs.sort_order
                """)
                .param("orgId", organizationId)
                .param("month", java.sql.Date.valueOf(periodMonth))
                .query((rs, rowNum) -> new ChannelSpend(
                        rs.getString("code"),
                        rs.getString("display_name"),
                        Money.of(rs.getLong("planned_minor"), rs.getString("currency")),
                        Money.of(rs.getLong("actual_minor"), rs.getString("currency"))))
                .list();
    }
}
