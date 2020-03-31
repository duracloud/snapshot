alter table snapshot_content_item modify content_id varchar(2000);
alter table snapshot modify name varchar(2000);
alter table snapshot_content_item add index sci_content_id_PK (content_id);
alter table snapshot_content_item add index snapshot_ci_id_PK (snapshot_id);

alter table snapshot_content_item modify metadata mediumtext;
alter table snapshot modify description mediumtext;
alter table snapshot modify status_text mediumtext;
alter table snapshot_history modify history mediumtext;
alter table restoration modify status_text mediumtext;