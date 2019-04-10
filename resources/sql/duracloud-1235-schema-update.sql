alter table snapshot_content_item modify content_id varchar(2000);
alter table snapshot modify name varchar(2000);
alter table snapshot_content_item add index sci_content_id_PK (content_id);
alter table snapshot_content_item add index snapshot_ci_id_PK (snapshot_id);