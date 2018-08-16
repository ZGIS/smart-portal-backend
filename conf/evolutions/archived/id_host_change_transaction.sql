BEGIN TRANSACTION;

-- Indexes:
--     "owc_contexts_pkey" PRIMARY KEY, btree (id)
-- Referenced by:
--     TABLE "owc_context_has_owc_resources" CONSTRAINT "owc_context_has_owc_resources_owc_context_id_fkey" FOREIGN KEY (owc_context_id) REFERENCES owc_contexts(id)
--     TABLE "user_has_owc_context_rights" CONSTRAINT "user_has_owc_context_rights_owc_context_id_fkey" FOREIGN KEY (owc_context_id) REFERENCES owc_contexts(id)
--     TABLE "usergroups_has_owc_context_rights" CONSTRAINT "usergroups_has_owc_context_rights_owc_context_id_fkey" FOREIGN KEY (owc_context_id) REFERENCES owc_contexts(id)

-- Firstly, remove FOREIGN KEY attribute constraints
ALTER TABLE owc_context_has_owc_resources DROP CONSTRAINT owc_context_has_owc_resources_owc_context_id_fkey;
ALTER TABLE user_has_owc_context_rights DROP CONSTRAINT user_has_owc_context_rights_owc_context_id_fkey;
ALTER TABLE usergroups_has_owc_context_rights DROP CONSTRAINT usergroups_has_owc_context_rights_owc_context_id_fkey;

-- Indexes:
--     "owc_resources_pkey" PRIMARY KEY, btree (id)
-- Referenced by:
--     TABLE "owc_context_has_owc_resources" CONSTRAINT "owc_context_has_owc_resources_owc_resource_id_fkey" FOREIGN KEY (owc_resource_id) REFERENCES owc_resources(id)
ALTER TABLE owc_context_has_owc_resources DROP CONSTRAINT owc_context_has_owc_resources_owc_resource_id_fkey;

-- UPDATE owc_contexts set id = 'https://nz-groundwater-hub.org' where id = '';

UPDATE owc_contexts set id = 'https://nz-groundwater-hub.org/context/user/eebfb68a-ce04-4452-a253-6e2d4547b6c3' where id = 'https://portal.smart-project.info/context/user/eebfb68a-ce04-4452-a253-6e2d4547b6c3';
UPDATE owc_contexts set id = 'https://nz-groundwater-hub.org/context/user/2cd60bec-6d0b-43f1-b37e-6dae95798309' where id = 'https://portal.smart-project.info/context/user/2cd60bec-6d0b-43f1-b37e-6dae95798309';
UPDATE owc_contexts set id = 'https://nz-groundwater-hub.org/context/document/e54930dc-f7c5-4be7-bfa3-a9d5c7332f6a' where id = 'https://portal.smart-project.info/context/document/e54930dc-f7c5-4be7-bfa3-a9d5c7332f6a';
UPDATE owc_contexts set id = 'https://nz-groundwater-hub.org/context/document/b1cb7528-976e-4ea9-ab49-7c6ad1fa9996' where id = 'https://portal.smart-project.info/context/document/b1cb7528-976e-4ea9-ab49-7c6ad1fa9996';
UPDATE owc_contexts set id = 'https://nz-groundwater-hub.org/context/document/3cce89b9-3584-4e36-befe-ef1a47a03eb8' where id = 'https://portal.smart-project.info/context/document/3cce89b9-3584-4e36-befe-ef1a47a03eb8';



-- UPDATE owc_resources  set id = 'https://nz-groundwater-hub.org/context/resource' where id = '';

UPDATE owc_resources  set id = 'https://nz-groundwater-hub.org/context/resource/ef501fba-b206-4ff1-95d8-6fadcb362402' where id = 'https://portal.smart-project.info/context/resource/ef501fba-b206-4ff1-95d8-6fadcb362402';
UPDATE owc_resources  set id = 'https://nz-groundwater-hub.org/context/resource/09d6ff76-87c2-4ad4-9fe5-b16d926eb751' where id = 'https://portal.smart-project.info/context/resource/09d6ff76-87c2-4ad4-9fe5-b16d926eb751';
UPDATE owc_resources  set id = 'https://nz-groundwater-hub.org/context/resource/7dc8c263-e05f-4706-a18b-99ec2001b49f' where id = 'https://portal.smart-project.info/context/resource/7dc8c263-e05f-4706-a18b-99ec2001b49f';
UPDATE owc_resources  set id = 'https://nz-groundwater-hub.org/context/resource/c04c7e0b-c67b-45be-bd34-b5b40f6e6bcf' where id = 'https://portal.smart-project.info/context/resource/c04c7e0b-c67b-45be-bd34-b5b40f6e6bcf';
UPDATE owc_resources  set id = 'https://nz-groundwater-hub.org/context/resource/54e9a9a3-767c-43dd-a19f-04233f534314' where id = 'https://portal.smart-project.info/context/resource/54e9a9a3-767c-43dd-a19f-04233f534314';
UPDATE owc_resources  set id = 'https://nz-groundwater-hub.org/context/resource/9f213c32-2c52-47c8-8a2c-2081e1a4a914' where id = 'https://portal.smart-project.info/context/resource/9f213c32-2c52-47c8-8a2c-2081e1a4a914';
UPDATE owc_resources  set id = 'https://nz-groundwater-hub.org/context/resource/968a97f0-d538-4d68-a02e-328bc0482f8e' where id = 'https://portal.smart-project.info/context/resource/968a97f0-d538-4d68-a02e-328bc0482f8e';

-- UPDATE user_has_owc_context_rights set owc_context_id = 'https://nz-groundwater-hub.org' where owc_context_id = '';

UPDATE user_has_owc_context_rights set owc_context_id = 'https://nz-groundwater-hub.org/context/user/eebfb68a-ce04-4452-a253-6e2d4547b6c3' where owc_context_id = 'https://portal.smart-project.info/context/user/eebfb68a-ce04-4452-a253-6e2d4547b6c3';
UPDATE user_has_owc_context_rights set owc_context_id = 'https://nz-groundwater-hub.org/context/user/2cd60bec-6d0b-43f1-b37e-6dae95798309' where owc_context_id = 'https://portal.smart-project.info/context/user/2cd60bec-6d0b-43f1-b37e-6dae95798309';
UPDATE user_has_owc_context_rights set owc_context_id = 'https://nz-groundwater-hub.org/context/document/e54930dc-f7c5-4be7-bfa3-a9d5c7332f6a' where owc_context_id = 'https://portal.smart-project.info/context/document/e54930dc-f7c5-4be7-bfa3-a9d5c7332f6a';
UPDATE user_has_owc_context_rights set owc_context_id = 'https://nz-groundwater-hub.org/context/document/b1cb7528-976e-4ea9-ab49-7c6ad1fa9996' where owc_context_id = 'https://portal.smart-project.info/context/document/b1cb7528-976e-4ea9-ab49-7c6ad1fa9996';
UPDATE user_has_owc_context_rights set owc_context_id = 'https://nz-groundwater-hub.org/context/document/3cce89b9-3584-4e36-befe-ef1a47a03eb8' where owc_context_id = 'https://portal.smart-project.info/context/document/3cce89b9-3584-4e36-befe-ef1a47a03eb8';

-- UPDATE owc_context_has_owc_resources set owc_context_id  = 'https://nz-groundwater-hub.org' where owc_context_id = '' ;
-- UPDATE owc_context_has_owc_resources set owc_resource_id  = 'https://nz-groundwater-hub.org' where owc_resource_id = '' ;

UPDATE owc_context_has_owc_resources set owc_context_id  = 'https://nz-groundwater-hub.org/context/document/e54930dc-f7c5-4be7-bfa3-a9d5c7332f6a' where owc_context_id = 'https://portal.smart-project.info/context/document/e54930dc-f7c5-4be7-bfa3-a9d5c7332f6a';

UPDATE owc_context_has_owc_resources set owc_resource_id  = 'https://nz-groundwater-hub.org/context/resource/ef501fba-b206-4ff1-95d8-6fadcb362402' where owc_resource_id = 'https://portal.smart-project.info/context/resource/ef501fba-b206-4ff1-95d8-6fadcb362402';
UPDATE owc_context_has_owc_resources set owc_resource_id  = 'https://nz-groundwater-hub.org/context/resource/09d6ff76-87c2-4ad4-9fe5-b16d926eb751' where owc_resource_id = 'https://portal.smart-project.info/context/resource/09d6ff76-87c2-4ad4-9fe5-b16d926eb751';
UPDATE owc_context_has_owc_resources set owc_resource_id  = 'https://nz-groundwater-hub.org/context/resource/7dc8c263-e05f-4706-a18b-99ec2001b49f' where owc_resource_id = 'https://portal.smart-project.info/context/resource/7dc8c263-e05f-4706-a18b-99ec2001b49f';
UPDATE owc_context_has_owc_resources set owc_resource_id  = 'https://nz-groundwater-hub.org/context/resource/c04c7e0b-c67b-45be-bd34-b5b40f6e6bcf' where owc_resource_id = 'https://portal.smart-project.info/context/resource/c04c7e0b-c67b-45be-bd34-b5b40f6e6bcf';

UPDATE owc_context_has_owc_resources set owc_context_id  = 'https://nz-groundwater-hub.org/context/document/b1cb7528-976e-4ea9-ab49-7c6ad1fa9996' where owc_context_id = 'https://portal.smart-project.info/context/document/b1cb7528-976e-4ea9-ab49-7c6ad1fa9996';

UPDATE owc_context_has_owc_resources set owc_resource_id  = 'https://nz-groundwater-hub.org/context/resource/54e9a9a3-767c-43dd-a19f-04233f534314' where owc_resource_id = 'https://portal.smart-project.info/context/resource/54e9a9a3-767c-43dd-a19f-04233f534314';
UPDATE owc_context_has_owc_resources set owc_resource_id  = 'https://nz-groundwater-hub.org/context/resource/9f213c32-2c52-47c8-8a2c-2081e1a4a914' where owc_resource_id = 'https://portal.smart-project.info/context/resource/9f213c32-2c52-47c8-8a2c-2081e1a4a914';

UPDATE owc_context_has_owc_resources set owc_context_id  = 'https://nz-groundwater-hub.org/context/document/3cce89b9-3584-4e36-befe-ef1a47a03eb8' where owc_context_id = 'https://portal.smart-project.info/context/document/3cce89b9-3584-4e36-befe-ef1a47a03eb8';

UPDATE owc_context_has_owc_resources set owc_resource_id  = 'https://nz-groundwater-hub.org/context/resource/968a97f0-d538-4d68-a02e-328bc0482f8e' where owc_resource_id = 'https://portal.smart-project.info/context/resource/968a97f0-d538-4d68-a02e-328bc0482f8e';

-- UPDATE usergroups_has_owc_context_rights set owc_context_id = '' where ;
UPDATE usergroups_has_owc_context_rights set owc_context_id = 'https://nz-groundwater-hub.org/context/document/b1cb7528-976e-4ea9-ab49-7c6ad1fa9996' where usergroups_uuid = 'f040832c-2aa7-49bf-9fe6-33c4328bab9f';

-- Indexes:
--     "owc_contexts_pkey" PRIMARY KEY, btree (id)
-- Referenced by:
--     TABLE "owc_context_has_owc_resources" CONSTRAINT "owc_context_has_owc_resources_owc_context_id_fkey" FOREIGN KEY (owc_context_id) REFERENCES owc_contexts(id)
--     TABLE "user_has_owc_context_rights" CONSTRAINT "user_has_owc_context_rights_owc_context_id_fkey" FOREIGN KEY (owc_context_id) REFERENCES owc_contexts(id)
--     TABLE "usergroups_has_owc_context_rights" CONSTRAINT "usergroups_has_owc_context_rights_owc_context_id_fkey" FOREIGN KEY (owc_context_id) REFERENCES owc_contexts(id)

ALTER TABLE owc_context_has_owc_resources ADD CONSTRAINT owc_context_has_owc_resources_owc_context_id_fkey FOREIGN KEY (owc_context_id) REFERENCES owc_contexts(id) ON DELETE CASCADE;
ALTER TABLE user_has_owc_context_rights ADD CONSTRAINT user_has_owc_context_rights_owc_context_id_fkey FOREIGN KEY (owc_context_id) REFERENCES owc_contexts(id) ON DELETE CASCADE;
ALTER TABLE usergroups_has_owc_context_rights ADD CONSTRAINT usergroups_has_owc_context_rights_owc_context_id_fkey FOREIGN KEY (owc_context_id) REFERENCES owc_contexts(id) ON DELETE CASCADE;

-- Indexes:
--     "owc_resources_pkey" PRIMARY KEY, btree (id)
-- Referenced by:
--     TABLE "owc_context_has_owc_resources" CONSTRAINT "owc_context_has_owc_resources_owc_resource_id_fkey" FOREIGN KEY (owc_resource_id) REFERENCES owc_resources(id)

ALTER TABLE owc_context_has_owc_resources ADD CONSTRAINT owc_context_has_owc_resources_owc_resource_id_fkey FOREIGN KEY (owc_resource_id) REFERENCES owc_resources(id) ON DELETE CASCADE;

COMMIT;