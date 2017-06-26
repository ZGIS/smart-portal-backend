# --- !Ups

CREATE TABLE users (
  email varchar(255) NOT NULL,
  accountsubject varchar(255) NOT NULL,
  firstname varchar(255) NOT NULL,
  lastname varchar(255) NOT NULL,
  password varchar(255) NOT NULL,
  laststatustoken varchar(255) NOT NULL,
  laststatuschange TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (accountsubject)
);

CREATE TABLE owc_contexts (
  id varchar(2047) NOT NULL,
  area_of_interest TEXT,
  spec_reference TEXT,
  context_metadata TEXT,
  language varchar(255) NOT NULL,
  title TEXT NOT NULL,
  subtitle TEXT,
  update_date TIMESTAMP WITH TIME ZONE,
  authors TEXT,
  publisher TEXT,
  creator_application TEXT,
  creator_display TEXT,
  rights TEXT,
  time_interval_of_interest TEXT,
  keyword TEXT,
  PRIMARY KEY (id)
);

CREATE TABLE owc_resources (
  id varchar(2047) NOT NULL,
  title TEXT NOT NULL,
  subtitle TEXT,
  update_date TIMESTAMP WITH TIME ZONE,
  authors TEXT,
  publisher TEXT,
  rights TEXT,
  geospatial_extent TEXT,
  temporal_extent TEXT,
  content_description TEXT,
  preview TEXT,
  content_by_ref TEXT,
  offering TEXT,
  active BOOLEAN,
  resource_metadata TEXT,
  keyword TEXT,
  min_scale_denominator DOUBLE PRECISION,
  max_scale_denominator DOUBLE PRECISION,
  folder TEXT,
  PRIMARY KEY (id)
);

CREATE TABLE owc_context_has_owc_resources (
  owc_context_id varchar(2047) REFERENCES owc_contexts(id),
  owc_resource_id varchar(2047) REFERENCES owc_resources(id)
);

CREATE TABLE user_has_owc_context_rights (
  users_accountsubject varchar(255) REFERENCES users(accountsubject),
  owc_context_id varchar(2047) REFERENCES owc_contexts(id),
  rights_relation_type SMALLINT,
  visibility SMALLINT
);

CREATE TABLE owc_authors (
  uuid varchar(255) NOT NULL,
  name varchar(255) NOT NULL,
  email varchar(2047),
  uri varchar(2047),
  PRIMARY KEY (uuid)
);

CREATE TABLE owc_categories (
  uuid varchar(255) NOT NULL,
  scheme varchar(2047),
  term varchar(255) NOT NULL,
  label TEXT,
  PRIMARY KEY (uuid)
);

CREATE TABLE owc_creator_applications (
  uuid varchar(255) NOT NULL,
  title TEXT,
  uri varchar(2047),
  version varchar(255),
  PRIMARY KEY (uuid)
);

CREATE TABLE owc_creator_displays (
  uuid varchar(255) NOT NULL,
  pixel_width INT,
  pixel_height INT,
  mm_per_pixel DOUBLE PRECISION,
  PRIMARY KEY (uuid)
);

CREATE TABLE owc_links (
  uuid varchar(255) NOT NULL,
  href varchar(2047) NOT NULL,
  mime_type varchar(255),
  lang varchar(255),
  title TEXT,
  length BIGINT,
  rel varchar(255) NOT NULL,
  PRIMARY KEY (uuid)
);

CREATE TABLE owc_offerings (
  uuid varchar(255),
  code varchar(2047) NOT NULL,
  operations TEXT,
  contents TEXT,
  styles TEXT,
  PRIMARY KEY (uuid)
);

CREATE TABLE owc_operations (
  uuid varchar(255),
  code varchar(2047) NOT NULL,
  method varchar(255) NOT NULL,
  mime_type varchar(255),
  request_url varchar(2047) NOT NULL,
  request_uuid varchar(255),
  result_uuid varchar(255),
  PRIMARY KEY (uuid)
);

CREATE TABLE owc_contents (
  uuid varchar(255),
  mime_type varchar(255) NOT NULL,
  url varchar(2047),
  title TEXT,
  content TEXT,
  PRIMARY KEY (uuid)
);

CREATE TABLE owc_stylesets (
  uuid varchar(255),
  name TEXT NOT NULL,
  title TEXT NOT NULL,
  abstrakt TEXT,
  is_default BOOLEAN,
  legend_url varchar(2047),
  content_uuid varchar(255),
  PRIMARY KEY (uuid)
);

# --- !Downs

DROP TABLE owc_context_has_owc_resources;

Drop TABLE user_has_owc_context_rights;

DROP TABLE owc_stylesets;
DROP TABLE owc_contents;
DROP TABLE owc_operations;
DROP TABLE owc_offerings;
DROP TABLE owc_links;
DROP TABLE owc_categories;
DROP TABLE owc_creator_displays;
DROP TABLE owc_creator_applications;
DROP TABLE owc_authors;
DROP TABLE owc_resources;
DROP TABLE owc_contexts;

DROP TABLE users;
