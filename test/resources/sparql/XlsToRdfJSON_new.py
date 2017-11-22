#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import xlrd
from string import Template
import re
import time
from datetime import date

today = date.today()

excel_file = 'GW portal list of icons new structure 20170830.xlsx'
# excel_file = 'GW portal list of icons new structure 20170317.xlsx'
# excel_file = 'GW portal list of icons November 2016.xlsx'

workbook = xlrd.open_workbook(excel_file)

# workbook = xlrd.open_workbook('articlestats.xls', encoding='cp1252')
# workbook = xlrd.open_workbook('articlestats.xls', on_demand = True)

worksheet_name = 'science domain categories'

worksheet = workbook.sheet_by_name(worksheet_name)
# worksheet = workbook.sheet_by_name('Alt. Level 2')

synonyms_sheets = workbook.sheet_by_name('synonyms')


# worksheet = workbook.sheet_by_index(0)
# workbook.nsheets
# workbook.sheet_names()

# Number	Item	Contact person	Query string	def keyword tag	bg-icon file	icon file	Research programme (if relevant)	Text
class CategoryHolder:
    def __init__(self, hierarchy_number, id):
        self.hierarchy_number = hierarchy_number
        self.id = id
        self.parent = 'main'
        self.item_name = ''
        self.description = ''
        self.keyword_content = []
        self.query_string = ''
        self.icon = ''
        self.bg_icon = ''

    def toString(self):
        return """{}, {}, {}, {}, {}, {}, {}, {}, {}""".format(self.hierarchy_number,
                                                               self.id,
                                                               self.parent,
                                                               self.item_name,
                                                               self.description,
                                                               self.keyword_content,
                                                               self.query_string,
                                                               self.icon,
                                                               self.bg_icon
                                                               )

    def toJson(self):
        return '{ \"hierarchy_number\": \"%s\", \n' \
               '\"id\": \"%s\", \n' \
               '\"parent\": \"%s\", \n' \
               '\"item_name\": \"%s\", \n' \
               '\"description\": \"%s\", \n' \
               '\"keyword_content\": [%s], \n' \
               '\"query_string\": \"%s\", \n' \
               '\"icon\": \"%s\", \n' \
               '\"bg_icon\": \"%s\" }' % (self.hierarchy_number,
                                          self.id,
                                          self.parent,
                                          self.item_name,
                                          self.description,
                                          ', '.join(['\"' + x + '\"' for x in self.keyword_content]),
                                          self.query_string,
                                          self.icon,
                                          self.bg_icon)

    def toRdf(self, isChild):
        formatter = """<rdf:Description rdf:about="http://vocab.smart-project.info/categories.rdfs#%s"
                     categories:id="%s"
                     categories:hierarchy_number="%s"
                     categories:parent="%s"
                     categories:item_name="%s"
                     categories:description="%s"
                     categories:keyword_content="%s"
                     categories:query_string="%s"
                     categories:icon="%s"
                     categories:bg_icon="%s">

        <rdf:type rdf:resource="http://vocab.smart-project.info/categories.rdfs#%s"/>
    </rdf:Description>"""

        emptyIcon = ''
        if self.icon:
            emptyIcon = self.icon

        emptyBgIcon = ''
        if self.bg_icon:
            emptyBgIcon = self.bg_icon

        emptyKeywords = ''
        if len(self.keyword_content) > 0:
            emptyKeywords = ', '.join([x for x in self.keyword_content])

        emptyQuery = ''
        if self.query_string:
            emptyQuery = self.query_string

        categoryClass = 'MainCategory'
        if isChild:
            categoryClass = 'ChildCategory'

        return formatter  % (self.id,
                             self.id,
                             self.hierarchy_number,
                             self.parent,
                             self.item_name,
                             self.description,
                             emptyKeywords,
                             emptyQuery,
                             emptyIcon,
                             emptyBgIcon,
                             categoryClass)


cat_list = []
parent_list = []
parent = 'main'
regMatcher = re.compile('item_name \(([\w\s,.-]+)\)', re.IGNORECASE)

def find_synonyms_for_keywords(keywords_list):
    retVal = []
    for keyword in keywords_list:
        try:
            for row in range(1, 8):
                if synonyms_sheets.cell(row, 0).value == xlrd.empty_cell.value or synonyms_sheets.cell(row, 1).value == xlrd.empty_cell.value:
                    pass
                else:
                    if str(synonyms_sheets.cell(row, 0).value) == keyword:
                        synonyms_list = [x.strip() for x in synonyms_sheets.cell(row, 1).value.split(',')]
                        retVal = retVal + [x.lower() for x in synonyms_list]
                    else:
                        pass
        except KeyError:
            print('synonym key error')
        except ValueError:
            print('synonym value error')
        except:
            print('synonym error')
    print(str('keywords: ' + ' '.join(retVal)))
    return retVal

for row in range(0, 80):
    if worksheet.cell(row, 0).value == xlrd.empty_cell.value:
        pass
    else:
        hierarchy_number = str(worksheet.cell(row, 0).value)
        item_name = str(worksheet.cell(row, 1).value)
        print(' row(+1) ' + str(row) + ' hierarchy_number ' + str(hierarchy_number))
        print(' row(+1) ' + str(row) + ' item_name ' + str(item_name))

        if hierarchy_number == 'hierarchy_number':
            if item_name == 'item_name':
                parent = 'main'
            else:
                matcher = regMatcher.search(item_name)
                if matcher != None:
                    plist = regMatcher.findall(item_name)
                    parent = ''.join(plist)
            pass
        else:
            print(parent)
            try:
                cat = CategoryHolder(hierarchy_number, row)
                cat.parent = parent
                cat.item_name = item_name
                cat.description = worksheet.cell(row, 4).value
                trimmed = [x.strip() for x in worksheet.cell(row, 5).value.split(',')]
                keywords_low = [x.lower() for x in trimmed]
                synonyms_low = find_synonyms_for_keywords(keywords_low)
                cat.keyword_content = keywords_low + synonyms_low
                cat.query_string = worksheet.cell(row, 8).value
                cat.icon = worksheet.cell(row, 6).value
                cat.bg_icon = worksheet.cell(row, 7).value
                if parent == 'main':
                    list.append(parent_list, cat)
                else:
                    list.append(cat_list, cat)
                    # print(cat.toJson())
            except KeyError:
                print('cat key error')
            except ValueError:
                print('cat value error')
            except:
                print('cat error')

# print(""" { 'categories' : [ """)

catJsonHeader = """ { \n \"categories\" : [ """

parentJsonList = []

valuesList = []
descList = []

rdfObjectsList = []

for parentElem in parent_list:

    childJsonList = []
    childList = []

    for childElem in cat_list:
        if childElem.parent == parentElem.query_string:
            # print(childElem.toJson() + ', ')
            list.append(childJsonList, childElem.toJson())
            list.append(childList, childElem)

            # RDF stuff for child categories
            print(childElem.toRdf(True))
            list.append(rdfObjectsList, childElem.toRdf(True))
        else:
            pass

    parentJsonString = '{ \"hierarchy_number\": \"%s\", \n' \
                       '\"id\": \"%s\", \n' \
                       '\"parent\": \"%s\", \n' \
                       '\"item_name\": \"%s\", \n' \
                       '\"description\": \"%s\", \n' \
                       '\"keyword_content\": [%s], \n' \
                       '\"query_string\": \"%s\", \n' \
                       '\"icon\": \"%s\", \n' \
                       '\"bg_icon\": \"%s\", \n' \
                       '\"children\": [ %s ] }' % (parentElem.hierarchy_number,
                                                   parentElem.id,
                                                   parentElem.parent,
                                                   parentElem.item_name,
                                                   parentElem.description,
                                                   ', '.join(['\"' + x + '\"' for x in parentElem.keyword_content]),
                                                   parentElem.query_string,
                                                   parentElem.icon,
                                                   parentElem.bg_icon,
                                                   ', \n'.join(childJsonList))

    list.append(parentJsonList, parentJsonString)

    # json_children = ', %s \"children\": [ ' % parentJsonString
    # print(json_children)
    # print(""" ]},""")

    # valid values for backend conf
    #
    # values: [
    #   "maps",
    #   "policy",
    #   "groundwateruse"
    # ],
    # descriptions: [
    #   "Maps",
    #   "Policy",
    #   "Groundwater Use"
    # ]
    # values for each parent, all parent and child as combo separated by plus for values
    # Description filed is the descriptions and keywords?
    for childElem in childList:
        valueString = parentElem.item_name.lower() + '+' + childElem.item_name.lower()
        descString = parentElem.item_name + ': ' + childElem.item_name + ' (' + childElem.description + ')'
        list.append(valuesList, valueString)
        list.append(descList, descString)

    # RDF stuff for parent categories
    print(parentElem.toRdf(False))
    list.append(rdfObjectsList, parentElem.toRdf(False))

# print(""" ]}""")
catJsonFooter = """ \n]}"""

parentJsonFull = ', \n'.join(parentJsonList)

fullJsonString = catJsonHeader + parentJsonFull + catJsonFooter

# print(fullJsonString + '\n')

with open('categories.json', 'w') as out:
    out.write(fullJsonString + '\n')

valuesString = ',\n'.join(['\"' + x + '\"' for x in valuesList])
descString = ',\n'.join(['\"' + x + '\"' for x in descList])
comment = '# Generated on: ' + today.isoformat() + ' from Excel ' + excel_file + ' / Worksheet: ' + worksheet_name

validValuesHocon = comment + '\nvalues: [\n' + valuesString + '\n],\n' + 'descriptions: [\n' + descString + '\n]'

# print(validValuesHocon + '\n')

with open('valid-values.hocon.conf', 'w') as out:
    out.write(validValuesHocon + '\n')

rdf_header = """<?xml version="1.0" encoding="UTF-8"?>
<rdf:RDF xmlns:categories="http://vocab.smart-project.info/categories#"
         xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
         xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
         xmlns:dc="http://purl.org/dc/elements/1.1/"
         xmlns:xs="http://www.w3.org/2001/XMLSchema">
         """

rdf_classdef = """    <rdf:Description rdf:ID="SacCategory">
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

rdf_footer = """
</rdf:RDF>
"""


rdfCategories = '\n'.join([x for x in rdfObjectsList])

fullRdfString = rdf_header + '\n<!-- ' + comment + ' -->\n' + rdf_classdef + rdfCategories + rdf_footer

with open('categories_test.rdf', 'w') as out:
    out.write(fullRdfString + '\n')