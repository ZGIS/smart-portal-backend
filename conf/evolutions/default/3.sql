-- users own collections schema

# --- !Ups

CREATE TABLE user_has_owc_feature_types_as_document (
  users_email varchar(255) REFERENCES users(email),
  owc_feature_types_as_document_id varchar(2047) REFERENCES owc_feature_types(id),
  collection_type varchar(255),
  visibility SMALLINT,
  UNIQUE (owc_feature_types_as_document_id)
);


# --- !Downs

Drop TABLE user_has_owc_feature_types_as_document;