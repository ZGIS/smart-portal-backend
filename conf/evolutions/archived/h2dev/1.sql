-- Users schema

# --- !Ups

CREATE TABLE users (
  email varchar(255) NOT NULL,
  accountsubject varchar(255) NOT NULL,
  firstname varchar(255) NOT NULL,
  lastname varchar(255) NOT NULL,
  password varchar(255) NOT NULL,
  laststatustoken varchar(255) NOT NULL,
  laststatuschange TIMESTAMP NOT NULL,
    PRIMARY KEY (email)
);

# --- !Downs

DROP TABLE users;