# --- !Ups

CREATE TABLE usergroups (
  uuid varchar(255) NOT NULL,
  name varchar(255) NOT NULL,
  shortinfo varchar(255) NOT NULL,
  laststatustoken varchar(255) NOT NULL,
  laststatuschange TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (uuid)
);

CREATE TABLE usergroups_has_users (
  usergroups_uuid varchar(255) REFERENCES usergroups(uuid),
  users_accountsubject varchar(255) REFERENCES users(accountsubject),
  userlevel SMALLINT NOT NULL
);

CREATE TABLE usergroups_has_owc_context_rights (
  usergroups_uuid varchar(255) REFERENCES usergroups(uuid),
  owc_context_id varchar(2047) REFERENCES owc_contexts(id),
  visibility SMALLINT NOT NULL
);

# --- !Downs

DROP TABLE usergroups_has_owc_context_rights;
DROP TABLE usergroups_has_users;
DROP TABLE usergroups;
