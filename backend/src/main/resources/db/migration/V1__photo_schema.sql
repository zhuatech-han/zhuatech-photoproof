-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

-- 自有源码仅供个人学习、研究与非商业交流；没有客户、照片或资金演示种子。

create table access_role (id bigint auto_increment primary key, name varchar(120) not null, scope varchar(20) not null, version bigint not null);

create table permission (id bigint auto_increment primary key, code varchar(60) not null unique, name varchar(120) not null);

create table nav_menu (id bigint auto_increment primary key, code varchar(60) not null unique, name varchar(120) not null, name_en varchar(120) not null, permission_code varchar(60) not null, position int not null, enabled boolean not null);

create table system_setting (id bigint auto_increment primary key, code varchar(60) not null unique, parameter_value varchar(6000) not null);

create table dictionary_entry (id bigint auto_increment primary key, type varchar(60) not null, code varchar(60) not null, name varchar(120) not null, name_en varchar(120) not null, enabled boolean not null, unique(type,code));

create table audit_event (id bigint auto_increment primary key, actor varchar(80) not null, action varchar(120) not null, object_id varchar(80) not null, department_id bigint not null, created_at timestamp(6) not null);

create table role_permission (role_id bigint not null,permission_code varchar(60) not null,primary key(role_id,permission_code),foreign key(role_id) references access_role(id),foreign key(permission_code) references permission(code));

create table department (
 id bigint auto_increment primary key,
 name varchar(120) not null,
 zone varchar(80) not null,
 currency varchar(3) not null,
 enabled boolean not null,
 version bigint not null
);

create table client (
 id bigint auto_increment primary key,
 department_id bigint not null,
 code varchar(60) not null,
 name varchar(120) not null,
 contact varchar(160) not null,
 created_by bigint not null,
 enabled boolean not null,
 version bigint not null,
 foreign key(department_id) references department(id),
 unique(department_id,code)
);

create table account (
 id bigint auto_increment primary key,
 username varchar(60) not null,
 display_name varchar(120) not null,
 password_hash varchar(100) not null,
 role_id bigint not null,
 department_id bigint not null,
 kind varchar(20) not null,
 client_id bigint null,
 enabled boolean not null,
 version bigint not null,
 foreign key(role_id) references access_role(id),
 foreign key(department_id) references department(id),
 foreign key(client_id) references client(id),
 unique(username)
);

create table photo_job (
 id bigint auto_increment primary key,
 department_id bigint not null,
 client_id bigint not null,
 photographer_id bigint not null,
 editor_id bigint not null,
 created_by bigint not null,
 title varchar(160) not null,
 shoot_type varchar(60) not null,
 shoot_date date not null,
 state varchar(30) not null,
 min_select int not null,
 included_count int not null,
 max_select int not null,
 base_amount decimal(16,2) not null,
 extra_price decimal(16,2) not null,
 amount_due decimal(16,2) not null,
 currency varchar(3) not null,
 round_number int not null,
 gallery_until timestamp(6) null,
 released_at timestamp(6) null,
 closed_at timestamp(6) null,
 created_at timestamp(6) not null,
 version bigint not null,
 foreign key(department_id) references department(id),
 foreign key(client_id) references client(id),
 foreign key(photographer_id) references account(id),
 foreign key(editor_id) references account(id),
 foreign key(created_by) references account(id)
);

create table media_asset (
 id bigint auto_increment primary key,
 job_id bigint not null,
 kind varchar(20) not null,
 photo_id bigint null,
 round_number int not null,
 revision_number int not null,
 slot_code varchar(180) not null,
 filename varchar(160) not null,
 file_key varchar(40) not null,
 content_type varchar(30) not null,
 sha256 varchar(64) not null,
 byte_size bigint not null,
 width int not null,
 height int not null,
 watermark varchar(40) not null,
 created_by bigint not null,
 created_at timestamp(6) not null,
 active boolean not null,
 foreign key(job_id) references photo_job(id),
 foreign key(photo_id) references media_asset(id),
 foreign key(created_by) references account(id),
 unique(job_id,kind,slot_code,round_number,revision_number)
);

create table photo_selection (
 id bigint auto_increment primary key,
 job_id bigint not null,
 round_number int not null,
 photo_id bigint not null,
 selected boolean not null,
 note varchar(1000) not null,
 actor varchar(80) not null,
 updated_at timestamp(6) not null,
 foreign key(job_id) references photo_job(id),
 foreign key(photo_id) references media_asset(id),
 unique(job_id,round_number,photo_id)
);

create table photo_review (
 id bigint auto_increment primary key,
 job_id bigint not null,
 media_id bigint not null,
 decision varchar(20) not null,
 note varchar(1000) not null,
 actor varchar(80) not null,
 created_at timestamp(6) not null,
 foreign key(job_id) references photo_job(id),
 foreign key(media_id) references media_asset(id)
);

create table cash_entry (
 id bigint auto_increment primary key,
 job_id bigint not null,
 department_id bigint not null,
 kind varchar(20) not null,
 source_id bigint null,
 amount decimal(16,2) not null,
 reference varchar(120) not null,
 note varchar(600) not null,
 actor varchar(80) not null,
 created_at timestamp(6) not null,
 foreign key(job_id) references photo_job(id),
 foreign key(department_id) references department(id),
 foreign key(source_id) references cash_entry(id)
);

create table share_grant (
 id bigint auto_increment primary key,
 job_id bigint not null,
 token_hash varchar(64) not null,
 pin_hash varchar(100) not null,
 expires_at timestamp(6) not null,
 created_by bigint not null,
 created_at timestamp(6) not null,
 enabled boolean not null,
 foreign key(job_id) references photo_job(id),
 foreign key(created_by) references account(id),
 unique(token_hash)
);

create table job_event (
 id bigint auto_increment primary key,
 job_id bigint not null,
 action varchar(60) not null,
 from_state varchar(30) not null,
 to_state varchar(30) not null,
 round_number int not null,
 note varchar(1000) not null,
 actor varchar(80) not null,
 created_at timestamp(6) not null,
 foreign key(job_id) references photo_job(id)
);

alter table client add foreign key(created_by) references account(id);

create index ix_job_department_state on photo_job(department_id,state);

create index ix_job_client on photo_job(client_id);

create index ix_media_job on media_asset(job_id,active);

create index ix_review_media on photo_review(media_id,id);

create index ix_cash_job on cash_entry(job_id,id);

create index ix_event_job on job_event(job_id,id);

create index ix_audit_department on audit_event(department_id,id);
