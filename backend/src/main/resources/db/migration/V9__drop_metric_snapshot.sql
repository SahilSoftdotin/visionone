-- metric_snapshot was designed as a derived read model and nothing ever read it.
--
-- The Overview and Growth screens compute from source tables instead, which is what makes a late
-- or out-of-order event converge on the right number rather than drift. An unused table is still
-- a table to migrate, document and reason about, so it goes.
--
-- If a derived read model is ever needed, it comes back with the query that needs it.
DROP TABLE IF EXISTS metric_snapshot;
