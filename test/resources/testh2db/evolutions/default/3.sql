# --- !Ups

CREATE TABLE sessions (
  token varchar(255) NOT NULL,
  useragent varchar(255) NOT NULL,
  email varchar(255) NOT NULL,
  accountsubject varchar(255) NOT NULL,
  laststatustoken varchar(255) NOT NULL,
  laststatuschange TIMESTAMP NOT NULL,
  PRIMARY KEY (token)
);

CREATE TABLE consentlogging (
  id bigint auto_increment NOT NULL ,
  timestamp TIMESTAMP NOT NULL,
  ipaddress varchar(255),
  useragent varchar(255),
  email varchar(255),
  link TEXT,
  referer TEXT,
  PRIMARY KEY (id)
);

CREATE TABLE userfiles (
  uuid varchar(255) NOT NULL,
  users_accountsubject varchar(255) REFERENCES users(accountsubject),
  originalfilename TEXT NOT NULL,
  linkreference TEXT NOT NULL,
  laststatustoken varchar(255) NOT NULL,
  laststatuschange TIMESTAMP NOT NULL,
  PRIMARY KEY (uuid)
);

CREATE TABLE usermetarecords (
  uuid varchar(255) NOT NULL,
  users_accountsubject varchar(255) REFERENCES users(accountsubject),
  originaluuid TEXT NOT NULL,
  cswreference TEXT NOT NULL,
  laststatustoken varchar(255) NOT NULL,
  laststatuschange TIMESTAMP NOT NULL,
  PRIMARY KEY (uuid)
);

# --- !Downs

DROP TABLE usermetarecords;
DROP TABLE userfiles;
DROP TABLE consentlogging;
DROP TABLE sessions;