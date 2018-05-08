/*
 * Copyright (C) 2011-2017 Interfaculty Department of Geoinformatics, University of
 * Salzburg (Z_GIS) & Institute of Geological and Nuclear Sciences Limited (GNS Science)
 * in the SMART Aquifer Characterisation (SAC) programme funded by the New Zealand
 * Ministry of Business, Innovation and Employment (MBIE) and Department of Geography,
 * University of Tartu, Estonia (UT) under the ETAG Mobilitas Pluss grant No. MOBJD233.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package models

package object rdf {

  val vocabBucketFolder = "sparql-categories-vocab"
  val awahou = "awahou.rdf"
  val categories = "categories_test.rdf"
  val glossary = "glossary.rdf"
  val ngmp = "ngmp.rdf"
  val papawai = "papawai_3.rdf"
  val researchpg = "research-pg.rdf"

  val ADMIN_JENA_UPDATE_URL = "https://admin.smart-project.info/kubectl/jena/reload"

  val rdfHeader =
    """<?xml version="1.0" encoding="UTF-8"?>
<rdf:RDF xmlns:categories="http://vocab.smart-project.info/categories#"
         xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
         xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
         xmlns:dc="http://purl.org/dc/elements/1.1/"
         xmlns:xs="http://www.w3.org/2001/XMLSchema">
         """

  val rdfSkosDcHeader: String =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:dc="http://purl.org/dc/elements/1.1/"
      |         xmlns:dcterms="http://purl.org/dc/terms/" xmlns:foaf="http://xmlns.com/foaf/0.1/"
      |         xmlns:gml="http://www.opengis.net/gml"
      |         xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#" xmlns:skos="http://www.w3.org/2004/02/skos/core#"
      |         xmlns:xs="http://www.w3.org/2001/XMLSchema">
      |""".stripMargin

  val rdfClassdef =
    """    <rdf:Description rdf:ID="SacCategory">
        <rdf:type rdf:resource="http://www.w3.org/2000/01/rdf-schema#Class"/>
        <rdfs:label xml:lang="en">category</rdfs:label>
        <rdfs:comment xml:lang="en">the basic category template</rdfs:comment>
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
    </rdf:Description>

    <rdfs:Class rdf:ID="MainCategory">
        <rdfs:subClassOf rdf:resource="#SacCategory"/>
        <rdfs:label xml:lang="en">main category</rdfs:label>
        <rdfs:comment xml:lang="en">a main category</rdfs:comment>
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
    </rdfs:Class>

    <rdfs:Class rdf:ID="ChildCategory">
        <rdfs:subClassOf rdf:resource="#MainCategory"/>
        <rdfs:label xml:lang="en">child category</rdfs:label>
        <rdfs:comment xml:lang="en">a child category</rdfs:comment>
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
    </rdfs:Class>

    <rdf:Property rdf:about="id" rdfs:label="id" rdfs:comment="id">
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
        <rdfs:domain rdf:resource="http://vocab.smart-project.info/categories#SacCategory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2000/01/rdf-schema#Literal"/>
    </rdf:Property>

    <rdf:Property rdf:about="hierarchy_number" rdfs:label="hierarchy_number" rdfs:comment="hierarchy_number">
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
        <rdfs:domain rdf:resource="http://vocab.smart-project.info/categories#SacCategory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2000/01/rdf-schema#Literal"/>
    </rdf:Property>

    <rdf:Property rdf:about="parent" rdfs:label="parent" rdfs:comment="parent">
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
        <rdfs:domain rdf:resource="http://vocab.smart-project.info/categories#SacCategory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2000/01/rdf-schema#Literal"/>
    </rdf:Property>

    <rdf:Property rdf:about="item_name" rdfs:label="item_name" rdfs:comment="item_name">
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
        <rdfs:domain rdf:resource="http://vocab.smart-project.info/categories#SacCategory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2000/01/rdf-schema#Literal"/>
    </rdf:Property>

    <rdf:Property rdf:about="description" rdfs:label="description" rdfs:comment="description">
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
        <rdfs:domain rdf:resource="http://vocab.smart-project.info/categories#SacCategory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2000/01/rdf-schema#Literal"/>
    </rdf:Property>

    <rdf:Property rdf:about="query_string" rdfs:label="query_string" rdfs:comment="query_string">
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
        <rdfs:domain rdf:resource="http://vocab.smart-project.info/categories#SacCategory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2000/01/rdf-schema#Literal"/>
    </rdf:Property>

    <rdf:Property rdf:about="query_string" rdfs:label="query_string" rdfs:comment="keyword_content">
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
        <rdfs:domain rdf:resource="http://vocab.smart-project.info/categories#SacCategory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2000/01/rdf-schema#Literal"/>
    </rdf:Property>

    <rdf:Property rdf:about="icon" rdfs:label="icon" rdfs:comment="icon">
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
        <rdfs:domain rdf:resource="http://vocab.smart-project.info/categories#SacCategory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2000/01/rdf-schema#Literal"/>
    </rdf:Property>

    <rdf:Property rdf:about="bg_icon" rdfs:label="bg_icon" rdfs:comment="bg_icon">
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
        <rdfs:domain rdf:resource="http://vocab.smart-project.info/categories#SacCategory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2000/01/rdf-schema#Literal"/>
    </rdf:Property>

    <!-- need to be a listable property, so that a class has several  -->
    <rdf:Property rdf:about="keyword_content" rdfs:label="keyword_content" rdfs:comment="keyword_content">
        <rdfs:isDefinedBy rdf:resource="http://vocab.smart-project.info/categories#"/>
        <rdfs:domain rdf:resource="http://vocab.smart-project.info/categories#SacCategory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2000/01/rdf-schema#Literal"/>
    </rdf:Property>
    """

  val rdfFooter =
    """
</rdf:RDF>
"""

}
