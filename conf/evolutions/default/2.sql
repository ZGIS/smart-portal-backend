-- OwcDocuments schema

# --- !Ups

-- # trait OwcFeatureType, impl are OwcEntry (which contains Offerings), and OwcDocument (which contains OwcEntries)
CREATE TABLE owc_feature_types (
  id varchar(255) NOT NULL,
  feature_type varchar(255) NOT NULL,
  bbox varchar(255),
  PRIMARY KEY (id)
);
-- # has properties: foreign key owc_feature_type -> owc_properties n:n,
CREATE TABLE owc_feature_types_has_owc_properties (


);

-- # can have owc_feature_type (if feature_type is 'OwcDocument'): foreign key owc_feature_type -> owc_feature_type n:n,
CREATE TABLE owc_feature_types_as_document_has_owc_entries (

);

-- # can have owc_offerings (if feature_type is 'OwcEntry'): foreign key owc_feature_type -> owc_offering n:n,
CREATE TABLE owc_feature_types_as_entry_has_owc_offerings (

);

CREATE TABLE owc_authors (
  name varchar(255) NOT NULL,
  email varchar(255),
  uri varchar(255),
  PRIMARY KEY (name)
);
-- # belongs to owc_properties_has_owc_authors: owc_author -> owc_properties (authors) n:n,
-- # belongs to owc_properties_has_owc_authors_as_contributors: owc_author -> owc_properties (contributors) n:n,

CREATE TABLE owc_categories (
  scheme varchar(255) NOT NULL,
  term varchar(255) NOT NULL,
  label varchar(255),
  PRIMARY KEY (scheme, term)
);
-- # belongs to owc_properties_has_owc_category: foreign key owc_category -> owc_properties (categories)  n:n,

CREATE TABLE owc_links (
  rel varchar(255) NOT NULL,
  mime_type varchar(255),
  href varchar(255) NOT NULL,
  title varchar(255),
  PRIMARY KEY (rel, href)
);
-- # belongs to owc_properties_has_owc_links: foreign key owc_links -> owc_properties (links) n:n,

CREATE TABLE owc_properties (
  id varchar(255) NOT NULL,
  lang varchar(255) NOT NULL,
  title varchar(255) NOT NULL,
  subtitle varchar(255),
  updated TIMESTAMPTZ,
  generator varchar(255),
  creator varchar(255),
  publisher varchar(255),
  PRIMARY KEY (id)
);
-- # belongs to owc_feature_type_has_owc_properties: foreign key owc_properties -> owc_feature_type n:n,

-- # has authors: foreign key owc_properties -> owc_author n:n,
CREATE TABLE owc_properties_has_owc_authors (

);

-- # has contributors: foreign key owc_properties -> owc_author n:n,
CREATE TABLE owc_properties_has_owc_authors_as_contributors (

);

-- # has categories: foreign key owc_properties -> owc_category n:n,
CREATE TABLE owc_properties_has_owc_categories (

);

-- # has links: foreign key owc_properties -> owc_links n:n,
CREATE TABLE owc_properties_has_owc_links (

);

-- # trait OwcOffering, impl are WfsOffering, WmsOffering, etc .. type is based on 'code' property
CREATE TABLE owc_offerings (
  id varchar(255) NOT NULL,
  offering_type varchar(255) NOT NULL,
  code varchar(255) NOT NULL,
  content text,
  PRIMARY KEY (id)
);
-- # belongs to owc_feature_type_as_entry_has_owc_offerings: foreign key owc_offering -> owc_feature_type n:n,

-- # has owc_operation: foreign key owc_offering -> owc_operation n:n,
CREATE TABLE owc_offerings_has_owc_operations (

);

CREATE TABLE owc_operations (
  id varchar(255) NOT NULL,
  code varchar(255) NOT NULL,
  method varchar(255) NOT NULL,
  contentType varchar(255) NOT NULL,
  href varchar(255) NOT NULL,
  request_content_type varchar(255),
  request_post_data text,
  result text,
  PRIMARY KEY (id)
);
-- # belongs to owc_offering_has_owc_operation: foreign key owc_operation -> owc_offering n:n,

# --- !Downs

DROP TABLE owc_feature_types_as_document_has_owc_entries;
DROP TABLE owc_feature_types_as_entry_has_owc_offerings;
DROP TABLE owc_feature_types_has_owc_properties;
DROP TABLE owc_properties_has_owc_authors;
DROP TABLE owc_properties_has_owc_authors_as_contributors;
DROP TABLE owc_properties_has_owc_categories;
DROP TABLE owc_properties_has_owc_links;
DROP TABLE owc_offerings_has_owc_operations;

DROP TABLE owc_operations;
DROP TABLE owc_offerings;
DROP TABLE owc_properties;
DROP TABLE owc_links;
DROP TABLE owc_categories;
DROP TABLE owc_authors;
DROP TABLE owc_feature_types;
