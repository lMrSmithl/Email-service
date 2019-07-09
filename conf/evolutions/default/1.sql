# --- First database schema

# --- !Ups

set ignorecase true;

create table recipient (
  id                        bigint not null,
  name                      varchar(255) not null,
  email_address             varchar(255) not null,
  phone_number              varchar(10),
  constraint pk_recipient primary key (id))
;

create table email(
  id                        bigint not null auto_increment,
  title                     varchar(255) not null,
  from_day                  bigint,
  to_day                    bigint,
  from_hour                 bigint,
  to_hour                   bigint,
  msg                       varchar(255),
  sender_id                 bigint not null,
  recipient_id              bigint not null,
  status                    varchar(6),
  constraint pk_email primary key (id))
;

create sequence recipient_seq start with 1000;

create sequence email_seq start with 1000;

alter table email add constraint fk_email_sender_1 foreign key (sender_id) references recipient (id) on delete restrict on update restrict;
create index ix_email_sender_1 on email (sender_id);

alter table email add constraint fk_email_recipient_1 foreign key (recipient_id) references recipient (id) on delete restrict on update restrict;
create index ix_email_recipient_1 on email (recipient_id);


# --- !Downs

SET REFERENTIAL_INTEGRITY FALSE;

drop table if exists recipient;

drop table if exists email;

SET REFERENTIAL_INTEGRITY TRUE;

drop sequence if exists recipient_seq;

drop sequence if exists email_seq;

