-- A one-line summary on a content item, so the client knows what they are approving.
--
-- Approve and request-changes are the only writes CLIENT_OWNER has in Phase 1. Asking someone to
-- approve a row that shows nothing but a title is asking them to rubber-stamp it, and an approval
-- nobody read is worth less than no approval at all.
ALTER TABLE content_item ADD COLUMN summary varchar(500);
