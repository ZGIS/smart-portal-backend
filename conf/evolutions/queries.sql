-- get all OwcEntries
SELECT *
FROM PUBLIC."owc_feature_types" AS ftyp
  INNER JOIN PUBLIC."owc_feature_types_has_owc_properties" AS ftyp2prop
    ON ftyp."id" = ftyp2prop."owc_feature_types_id"
WHERE ftyp."feature_type" = 'OwcEntry';

-- get all OWC properties
SELECT *
FROM PUBLIC."owc_properties" AS prop;


SELECT *
FROM PUBLIC.owc_operations AS oper;

SELECT *
FROM PUBLIC."owc_feature_types" AS ftyp
  INNER JOIN PUBLIC."owc_feature_types_as_entry_has_owc_offerings" AS ftyp2offe
    ON "ftyp"."id" = "ftyp2offe"."owc_feature_types_as_entry_id";

SELECT ftyp.*
FROM owc_feature_types ftyp
  INNER JOIN user_has_owc_feature_types_as_document user2ftyp
    ON ftyp.id = user2ftyp.owc_feature_types_as_document_id
WHERE feature_type = 'OwcDocument'
      AND user2ftyp.users_email = 'st_reichel@gmx.net'
      AND user2ftyp.collection_type = 'DEFAULT';

SELECT DISTINCT
 -- prop.*,
  oper.*
FROM PUBLIC."owc_feature_types" AS ftyp
  /*get all OwcEntries under default collection*/
  INNER JOIN PUBLIC."owc_feature_types_as_document_has_owc_entries" AS ftyp2entr
    ON "ftyp"."id" = "ftyp2entr"."owc_feature_types_as_entry_id"
  /*get all Properties for that OwcEntry*/
  INNER JOIN PUBLIC."owc_feature_types_has_owc_properties" AS ftyp2prop
    ON "ftyp"."id" = "ftyp2prop"."owc_feature_types_id"
  INNER JOIN PUBLIC."owc_properties" AS prop
    ON "ftyp2prop"."owc_properties_uuid" = "prop"."uuid"
  /*get all offerings in that Entry*/
  INNER JOIN PUBLIC."owc_feature_types_as_entry_has_owc_offerings" AS ftyp2offe
    ON "ftyp"."id" = "ftyp2offe"."owc_feature_types_as_entry_id"
  INNER JOIN PUBLIC."owc_offerings" AS offe
    ON "ftyp2offe"."owc_offerings_uuid" = "offe"."uuid"
  /*find all operations to that offering*/
  INNER JOIN PUBLIC."owc_offerings_has_owc_operations" AS offe2oper
    ON "offe"."uuid" = "offe2oper"."owc_offerings_uuid"
  INNER JOIN PUBLIC."owc_operations" AS oper
    ON "offe2oper"."owc_operations_uuid" = "oper"."uuid"
WHERE 1 = 1
      // default collection ID
      AND ftyp2entr."owc_feature_types_as_document_id" =
          'http://portal.smart-project.info/context/user/ae65f09e-a707-4f82-ad49-d1cabc3ba7ab'
      AND offe."offering_type" = 'HttpLinkOffering'
      AND oper."code" = 'GetFile';

SELECT DISTINCT
  oper.*
FROM PUBLIC."owc_operations" as oper

WHERE 1 = 1
      // default collection ID
  AND prop."uuid" in ('c516583b-0106-4007-b172-bd9325f13b58', '60e58b80-1513-4184-9eef-21e5ddd32cdc')
  AND offe."offering_type" = 'HttpLinkOffering'
  AND oper."code" = 'GetFile';